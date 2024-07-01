/***********************************************************************************************************************
 *  File Name:       GroupHandler.java
 *  Project:         Better2/gatewayservice
 *  Author:          Bryan SebaRaj
 *  Description:     Gateway-service handler for all group service traffic
 **********************************************************************************************************************/
package com.better2.gatewayservice;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.Headers;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.lang.System;
import java.net.HttpURLConnection;
import java.net.URL;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import java.util.concurrent.TimeUnit;

public class GroupHandler implements HttpHandler {

    private final String groupServiceURL = System.getenv("GROUP_SERVICE_HOST") + ":" + System.getenv("GROUP_SERVICE_PORT");
    private Jedis jwtCacheConnection;
    private JedisCluster rateLimiterConnection;
    private final int REQUEST_LIMIT = Integer.parseInt(System.getenv("RL_REQUEST_LIMIT"));
    private final long TIME_WINDOW_SECONDS = Integer.parseInt(System.getenv("RL_TIME_WINDOW"));

    public GroupHandler(Jedis jwtCacheConnection, JedisCluster rateLimiterConnection) {
        this.rateLimiterConnection = rateLimiterConnection;
        this.jwtCacheConnection = jwtCacheConnection;
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(response.getBytes());
            outputStream.close();
        }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            handleCors(exchange);
            String requestMethod = exchange.getRequestMethod();
            if ("OPTIONS".equalsIgnoreCase(requestMethod)) {
                // For OPTIONS requests, just return successful response (200 OK)
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            // Checking redis-cluster rate limiter by host IP
            if (!isRequestAllowed(exchange)) {
                sendResponse(exchange, 429, "{\"error\": \"Rate limit exceeded\"}");
                return;
            }

            // Checking JWT validity
            ValidateRequest validator = new ValidateRequest();
            ValidateRequest.ValidationResult validationResult = validator.validateRequest(exchange, this.jwtCacheConnection);
            if (!validationResult.isValid()) {
                sendResponse(exchange, 401, "{\"error\": \"Unauthorized\"}");
                return;
            }
            String username = validationResult.getUsername();

            // Forward request to the group service
            System.out.println("GroupHandler: User validated. Forwarding request to group service.");
            URL url = new URL("http://" + groupServiceURL + exchange.getRequestURI().toString());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();;
            conn.setRequestMethod(exchange.getRequestMethod());
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Username", username);

            if (conn.getRequestMethod().equalsIgnoreCase("POST") || conn.getRequestMethod().equalsIgnoreCase("PUT")) {
                conn.setDoOutput(true);
                InputStream is = exchange.getRequestBody();
                String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
            }

            // Receiving response from the group service
            System.out.println("GroupHandler: Receiving response from group service.");
            InputStream groupResponseStream;
            int statusCode = conn.getResponseCode();
            if (statusCode >= 200 && statusCode < 300) {
                groupResponseStream = conn.getInputStream();
            } else {
                groupResponseStream = conn.getErrorStream();
            }
            String groupResponse = new String(groupResponseStream.readAllBytes(), StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; utf-8");

            System.out.println("GroupHandler: Routing response back to client.");
            sendResponse(exchange, statusCode, groupResponse);


        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "{\"error\": \"Group service response failed.\"}");
        }
    }

    // Method to handle CORS headers (allowing all methods and headers)
    private static void handleCors(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "*"); // Allow requests from all origins
        headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS"); // Allow all HTTP methods
        headers.set("Access-Control-Allow-Headers", "*"); // Allow all headers
    }

    private boolean isRequestAllowed(HttpExchange exchange) {
        String clientIP = exchange.getRemoteAddress().getAddress().getHostAddress();
        long currentTimestamp = System.currentTimeMillis() / 1000;
        long windowStart = currentTimestamp - TIME_WINDOW_SECONDS + 1;

        try {
            // Remove old requests from the window
            rateLimiterConnection.zremrangeByScore(clientIP, 0, windowStart - 1);
            // Get current number of requests in the window
            long currentRequests = rateLimiterConnection.zcard(clientIP);
            // Add current request to the window
            rateLimiterConnection.zadd(clientIP, currentTimestamp, String.valueOf(currentTimestamp));
            // Set expiration for the key to clear out old entries
            rateLimiterConnection.expire(clientIP, (int) TIME_WINDOW_SECONDS);
            // Check if the number of requests is within the limit
            return currentRequests <= REQUEST_LIMIT;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }
}


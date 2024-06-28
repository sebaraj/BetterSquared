/***********************************************************************************************************************
 *  File Name:       BetHandler.java
 *  Project:         Better2/gatewayservice
 *  Author:          Bryan SebaRaj
 *  Description:     Gateway-service handler for all bet service traffic
 **********************************************************************************************************************/
package com.better2.gatewayservice;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
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

public class BetHandler implements HttpHandler {

    private final String betServiceURL = System.getenv("BET_SERVICE_HOST") + ":" + System.getenv("BET_SERVICE_PORT");
    private Jedis jwtCacheConnection;
    private JedisCluster rateLimiterConnection;
    private final int REQUEST_LIMIT = Integer.parseInt(System.getenv("RL_REQUEST_LIMIT"));
    private final long TIME_WINDOW_SECONDS = Integer.parseInt(System.getenv("RL_TIME_WINDOW"));

    public BetHandler(Jedis jwtCacheConnection, JedisCluster rateLimiterConnection) {
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

            // Forwarding request to the bet service
            System.out.println("BetHandler: User validated. Forwarding request to bet service.");
            URL url = new URL("http://" + betServiceURL + exchange.getRequestURI().toString());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
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

            // Receiving response from the bet service
            System.out.println("BetHandler: Receiving response from bet service.");
            InputStream betResponseStream;
            int statusCode = conn.getResponseCode();
            if (statusCode >= 200 && statusCode < 300) {
                betResponseStream = conn.getInputStream();
            } else {
                betResponseStream = conn.getErrorStream();
            }
            String betResponse = new String(betResponseStream.readAllBytes(), StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; utf-8");

            System.out.println("BetHandler: Routing response back to client.");
            sendResponse(exchange, statusCode, betResponse);

        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "{\"error\": \"Bet service response failed.\"}");
        }
    }

    private boolean isRequestAllowed(HttpExchange exchange) {
        String clientIP = exchange.getRemoteAddress().getAddress().getHostAddress();
        long currentTimestamp = System.currentTimeMillis() / 1000;
        long windowStart = currentTimestamp - TIME_WINDOW_SECONDS + 1;

        try {
            rateLimiterConnection.zremrangeByScore(clientIP, 0, windowStart - 1);
            long currentRequests = rateLimiterConnection.zcard(clientIP);
            rateLimiterConnection.zadd(clientIP, currentTimestamp, String.valueOf(currentTimestamp));
            rateLimiterConnection.expire(clientIP, (int) TIME_WINDOW_SECONDS);
            return currentRequests <= REQUEST_LIMIT;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}


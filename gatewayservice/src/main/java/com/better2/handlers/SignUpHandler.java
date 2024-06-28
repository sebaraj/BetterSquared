/***********************************************************************************************************************
 *  File Name:       SignUpHandler.java
 *  Project:         Better2/gatewayservice
 *  Author:          Bryan SebaRaj
 *  Description:     Gateway-service handler to signup via auth service
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

public class SignUpHandler implements HttpHandler {

    private final String authServiceUrl = System.getenv("AUTH_SERVICE_HOST") + ":" + System.getenv("AUTH_SERVICE_PORT");
    private JedisCluster rateLimiterConnection;
    private final int REQUEST_LIMIT = Integer.parseInt(System.getenv("RL_REQUEST_LIMIT"));
    private final long TIME_WINDOW_SECONDS = Integer.parseInt(System.getenv("RL_TIME_WINDOW"));

    public SignUpHandler(JedisCluster rateLimiterConnection) {
        this.rateLimiterConnection = rateLimiterConnection;
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
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            try {
                // Checking redis-cluster rate limiter by host IP
                if (!isRequestAllowed(exchange)) {
                    sendResponse(exchange, 429, "{\"error\": \"Rate limit exceeded\"}");
                    return;
                }

                // Read request body
                InputStream is = exchange.getRequestBody();
                String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                // Forwarding request to the authentication service
                System.out.println("SignUpHandler: Forwarding request to auth service.");
                URL url = new URL("http://" + authServiceUrl + "/signup");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; utf-8");

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                // Receiving response from the authentication service
                System.out.println("LoginHandler: Receiving response from auth service.");
                InputStream authResponseStream;
                int statusCode = conn.getResponseCode();
                if (statusCode >= 200 && statusCode < 300) {
                    authResponseStream = conn.getInputStream();
                } else {
                    authResponseStream = conn.getErrorStream();
                }
                String authResponse = new String(authResponseStream.readAllBytes(), StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; utf-8");

                System.out.println("LoginHandler: Routing response back to client.");
                sendResponse(exchange, statusCode, authResponse);

            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\": \"Sign-up failed.\"}");
            }
        } else {
            sendResponse(exchange, 405, "{\"error\": \"Method not allowed.\"}");
        }
        exchange.close();
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


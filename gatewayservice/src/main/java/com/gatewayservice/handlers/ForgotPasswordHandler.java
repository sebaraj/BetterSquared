package com.gatewayservice.server;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.lang.System;
import java.net.HttpURLConnection;
import java.net.URL;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import java.util.concurrent.TimeUnit;
//import io.github.cdimascio.dotenv.Dotenv;

public class ForgotPasswordHandler implements HttpHandler {

    private final String authServiceUrl = System.getenv("AUTH_SERVICE_HOST") + ":" + System.getenv("AUTH_SERVICE_PORT");
    private JedisCluster rateLimiterConnection;
    private final int REQUEST_LIMIT = Integer.parseInt(System.getenv("RL_REQUEST_LIMIT"));// 2; // Maximum requests per window
    private final long TIME_WINDOW_SECONDS = Integer.parseInt(System.getenv("RL_TIME_WINDOW")); // 60; // Time window in seconds

    public ForgotPasswordHandler(JedisCluster rateLimiterConnection) {
        this.rateLimiterConnection = rateLimiterConnection;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            try {
                if (!isRequestAllowed(exchange)) {
                    String errorResponse = "{\"error\": \"Rate limit exceeded\"}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json; utf-8");
                    exchange.sendResponseHeaders(429, errorResponse.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(errorResponse.getBytes(StandardCharsets.UTF_8));
                    }
                    return;
                }
                // Read request body
                InputStream is = exchange.getRequestBody();
                String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                // Forward request to the authentication service
                System.out.println("Routing forgot password request to " + authServiceUrl);
                URL url = new URL("http://" + authServiceUrl + "/forgotpassword");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("PUT");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; utf-8");

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                System.out.println("Receiving response from auth service.");
                // Get response from the authentication service
                int responseCode = conn.getResponseCode();
                System.out.println("Response Code: " + responseCode);
                InputStream authResponseStream = responseCode == 200 ? conn.getInputStream() : conn.getErrorStream();
                String authResponse = new String(authResponseStream.readAllBytes(), StandardCharsets.UTF_8);

                // Set response headers and body
                exchange.getResponseHeaders().set("Content-Type", "application/json; utf-8");
                exchange.sendResponseHeaders(responseCode, authResponse.getBytes(StandardCharsets.UTF_8).length);
                System.out.println("Routing forgot password response from auth to gateway");
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(authResponse.getBytes(StandardCharsets.UTF_8));
                }

            } catch (Exception e) {
                e.printStackTrace();
                String errorResponse = "{\"error\": \"Reset password failed.\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json; utf-8");
                exchange.sendResponseHeaders(500, errorResponse.getBytes(StandardCharsets.UTF_8).length);

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errorResponse.getBytes(StandardCharsets.UTF_8));
                }
            }
        } else {
            exchange.sendResponseHeaders(405, -1); // 405 Method Not Allowed
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


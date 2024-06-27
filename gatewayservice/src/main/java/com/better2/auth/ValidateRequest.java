/***********************************************************************************************************************
 *  File Name:       ValidateRequest.java
 *  Project:         Better2/gatewayservice
 *  Author:          Bryan SebaRaj
 *  Description:     Gateway-service-level abstraction for handlers to authenticate JWT via cache/auth service call
 **********************************************************************************************************************/
package com.better2.gatewayservice;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.Headers;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.lang.System;
import java.net.HttpURLConnection;
import java.net.URL;
import redis.clients.jedis.Jedis;

public class ValidateRequest {

    private final String authServiceUrl = System.getenv("AUTH_SERVICE_HOST") + ":" + System.getenv("AUTH_SERVICE_PORT");

    public class ValidationResult {
        private boolean isValid;
        private String username;

        public ValidationResult(boolean isValid, String username) {
            this.isValid = isValid;
            this.username = username;
        }

        public boolean isValid() {
            return isValid;
        }

        public String getUsername() {
            return username;
        }
    }

    public ValidationResult validateRequest(HttpExchange exchange, Jedis jwtCache) {
        try {
            // Check if JWT is in cache (only filed with valid JWTs)
            Headers requestHeaders = exchange.getRequestHeaders();
            List<String> authorizationHeader = requestHeaders.get("Authorization");
            if (authorizationHeader == null || authorizationHeader.isEmpty()) {
                return new ValidationResult(false, null);
            }
            String token = authorizationHeader.get(0).replace("Bearer ", "");
            String username = jwtCache.get(token);
            if (username != null) {
                System.out.println("ValidateRequest: Token for username " + username + " found in JWT cache");
                return new ValidationResult(true, username);
            }

            System.out.println("ValidateRequest: Token for username " + username + " not found in JWT cache. Routing to auth.");
            // Send JWT (as HTTP POST request) to auth service for verification
            URL url = new URL("http://" + authServiceUrl + "/validate");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.connect();

            // Read/process HTTP response from auth service
            System.out.println("ValidateRequest: Received response from auth: " + conn.getResponseCode());
            StringBuilder response = new StringBuilder();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
            }
            if ("Invalid".equals(response.toString())) {
                return new ValidationResult(false, "");
            }
            String[] parts = (response.toString()).split("/");
            return new ValidationResult("Authenticated".equals((String) parts[0]), (String) parts[parts.length - 1]);
        } catch (Exception e) {
            System.out.println("ValidateRequest: Error validating JWT.");
            e.printStackTrace();
            return new ValidationResult(false, "");
        }
    }

}


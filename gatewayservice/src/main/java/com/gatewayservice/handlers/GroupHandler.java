package com.gatewayservice.server;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.lang.System;
import java.net.HttpURLConnection;
import java.net.URL;

public class GroupHandler implements HttpHandler {

    private final String groupServiceURL = System.getenv("GROUP_SERVICE_HOST") + ":" + System.getenv("GROUP_SERVICE_PORT");

    public GroupHandler() {
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            // Read request body
            InputStream is = exchange.getRequestBody();
            String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            // Validate request
            ValidateRequest validator = new ValidateRequest();
            ValidateRequest.ValidationResult validationResult = validator.validateRequest(exchange);

            if (!validationResult.isValid()) {
                String errorResponse = "{\"error\": \"Unauthorized\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json; utf-8");
                exchange.sendResponseHeaders(401, errorResponse.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errorResponse.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            String username = validationResult.getUsername();

            // Forward request to the group service
            //System.out.println("Routing group request to " + groupServiceURL);
            System.out.println("http://" + groupServiceURL + exchange.getRequestURI().toString());
            URL url = new URL("http://" + groupServiceURL + exchange.getRequestURI().toString());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            System.out.println(exchange.getRequestMethod());
            conn.setRequestMethod(exchange.getRequestMethod());
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Username", username);

            if (conn.getRequestMethod().equalsIgnoreCase("POST") || conn.getRequestMethod().equalsIgnoreCase("PUT")) {
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
            }
            System.out.println(conn.getRequestMethod());


            System.out.println("Receiving response from group service.");
            // Get response from the group service
            int responseCode = conn.getResponseCode();
            System.out.println("Response Code: " + responseCode);
            InputStream authResponseStream = responseCode == 200 ? conn.getInputStream() : conn.getErrorStream();
            String authResponse = new String(authResponseStream.readAllBytes(), StandardCharsets.UTF_8);

            // Set response headers and body
            exchange.getResponseHeaders().set("Content-Type", "application/json; utf-8");
            exchange.sendResponseHeaders(responseCode, authResponse.getBytes(StandardCharsets.UTF_8).length);
            System.out.println("Routing group response from group to gateway");
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
    }
}


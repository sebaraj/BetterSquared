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
//import io.github.cdimascio.dotenv.Dotenv;

public class SignUpHandler implements HttpHandler {

    //private Connection connection;
    //Dotenv dotenv = Dotenv.configure().load();
    //private final String jwtSecret = System.getenv("JWT_SECRET");
    private final String authServiceUrl = System.getenv("AUTH_SERVICE_HOST") + ":" + System.getenv("AUTH_SERVICE_PORT");

    public SignUpHandler() {
    }
    //}  this.connection = connection;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            try {
                System.out.println("routing signup request from gateway to auth");
                System.out.println("Routing to " + authServiceUrl);

                // Read request body
                InputStream is = exchange.getRequestBody();
                String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                System.out.println("Received request: " + requestBody);

                // Forward request to the authentication service
                URL url = new URL("http://" + authServiceUrl + "/signup");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; utf-8");

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                // Get response from the authentication service
                int responseCode = conn.getResponseCode();
                System.out.println("Response Code: " + responseCode);
                InputStream authResponseStream = responseCode == 200 ? conn.getInputStream() : conn.getErrorStream();
                String authResponse = new String(authResponseStream.readAllBytes(), StandardCharsets.UTF_8);

                // Set response headers and body
                exchange.getResponseHeaders().set("Content-Type", "application/json; utf-8");
                exchange.sendResponseHeaders(responseCode, authResponse.getBytes(StandardCharsets.UTF_8).length);
                System.out.println("routing signup response from auth to gateway");
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(authResponse.getBytes(StandardCharsets.UTF_8));
                }

            } catch (Exception e) {
                e.printStackTrace();
                String errorResponse = "{\"error\": \"Internal server error\"}";
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
}


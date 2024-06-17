package com.gatewayservice.server;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.Headers;
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

public class TestValidateHandler implements HttpHandler {

    //private Connection connection;
    //Dotenv dotenv = Dotenv.configure().load();
    //private final String jwtSecret = System.getenv("JWT_SECRET");
    private final String authServiceUrl = System.getenv("AUTH_SERVICE_HOST") + ":" + System.getenv("AUTH_SERVICE_PORT");

    public TestValidateHandler() {
    }
    //}  this.connection = connection;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            try {
                ValidateRequest validator = new ValidateRequest();
                ValidateRequest.ValidationResult validationResult = validator.validateRequest(exchange);
                boolean result = validationResult.isValid();
                String username = validationResult.getUsername();
                exchange.setAttribute("username", username);
                String response = "Validation " + (result ? "succeeded" : "failed");
                exchange.sendResponseHeaders(200, response.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());

                os.close();

            } catch (Exception e) {
                e.printStackTrace();
                String errorResponse = "{\"error\": \"Test validation failed\"}";
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


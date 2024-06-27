/***********************************************************************************************************************
 *  File Name:       LoginHandler.java
 *  Project:         Better2/authservice
 *  Author:          Bryan SebaRaj
 *  Description:     Auth-service-level handler for user login
 **********************************************************************************************************************/
package com.better2.authservice;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.json.JSONObject;
import org.mindrot.jbcrypt.BCrypt;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.lang.System;

public class LoginHandler implements HttpHandler {

    private Connection connection;
    private final String jwtSecret = System.getenv("AUTH_JWT_SECRET");

    public LoginHandler(Connection connection) {
        this.connection = connection;
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
                // Reading the request body and parsing JSON payload
                InputStream inputStream = exchange.getRequestBody();
                String requestBody = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                JSONObject jsonObject = new JSONObject(requestBody);
                String username = jsonObject.getString("username");
                String password = jsonObject.getString("password");
                System.out.println("LoginHandler: Received request and JSON payload.");

                // Authneticating user by username+password
                if (authenticateUser(username, password)) {
                    System.out.println("LoginHandler: User authenticated.");
                    updateLastAccessAt(username);
                    String token = createJWT(username);
                    String response = "{\"token\":\"" + token + "\"}";
                    sendResponse(exchange, 200, response);
                } else {
                    sendResponse(exchange, 401, "Unauthorized");
                }
            } catch (SQLException e) {
                e.printStackTrace();
                sendResponse(exchange, 404, "{\"error\": \"User not found\"}");
            }
        } else {
            sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
        }
        exchange.close();
    }

    private boolean updateLastAccessAt(String username) throws SQLException {
        String updateSQL = "UPDATE users SET last_accessed_at = CURRENT_TIMESTAMP WHERE username = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(updateSQL)) {
            preparedStatement.setString(1, username);
            int affectedRows = preparedStatement.executeUpdate();
            return affectedRows > 0;
        }
    }

    private boolean authenticateUser(String username, String password) throws SQLException {
        String query = "SELECT password FROM users WHERE username = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, username);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                String storedPassword = resultSet.getString("password");
                return BCrypt.checkpw(password, storedPassword);
            }
        }
        return false;
    }

    private String createJWT(String username) {
        try {
            return JWT.create()
                    .withIssuer("auth0")
                    .withClaim("username", username)
                    .withExpiresAt(new Date(System.currentTimeMillis() + 24 * 3600 * 1000)) // 1 day expiration
                    .sign(Algorithm.HMAC256(jwtSecret));
        } catch (JWTCreationException exception) {
            System.out.println("LoginHandler: Error creating JWT.");
            throw new RuntimeException("Error creating JWT", exception);

        }
    }
}

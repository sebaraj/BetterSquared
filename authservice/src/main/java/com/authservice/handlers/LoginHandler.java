package com.authservice.server;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.util.List;
import java.lang.System;
//import io.github.cdimascio.dotenv.Dotenv;

public class LoginHandler implements HttpHandler {

    private Connection connection;
    //Dotenv dotenv = Dotenv.configure().load();
    private final String jwtSecret = System.getenv("AUTH_JWT_SECRET");

    public LoginHandler(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            // Read the request body
            InputStream inputStream = exchange.getRequestBody();
            String requestBody = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            System.out.println("Received JSON payload: " + requestBody);

            JsonObject jsonObject = JsonParser.parseString(requestBody).getAsJsonObject();
            String username = jsonObject.get("username").getAsString();
            String password = jsonObject.get("password").getAsString();

            try {
                if (authenticateUser(username, password)) {
                    updateLastAccessAt(username);
                    System.out.println("User authenticated. Creating JWT...");
                    String token = createJWT(username);
                    //System.out.println(token);
                    String response = "{\"token\":\"" + token + "\"}";
                    System.out.println("Sending response...");
                    exchange.sendResponseHeaders(200, response.getBytes().length);
                    OutputStream outputStream = exchange.getResponseBody();
                    outputStream.write(response.getBytes());
                    outputStream.close();
                    System.out.println("Response sent.");
                } else {
                    exchange.sendResponseHeaders(401, -1); // 401 Unauthorized
                }
            } catch (SQLException e) {
                e.printStackTrace();
                exchange.sendResponseHeaders(500, -1); // 500 Internal Server Error

            }
        } else {
            exchange.sendResponseHeaders(405, -1); // 405 Method Not Allowed
        }
        exchange.close();
    }

    private boolean updateLastAccessAt(String username) throws SQLException {
        System.out.println("Updating last access at...");
        String updateSQL = "UPDATE users SET last_accessed_at = CURRENT_TIMESTAMP WHERE username = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(updateSQL)) {
            preparedStatement.setString(1, username);
            int affectedRows = preparedStatement.executeUpdate();
            return affectedRows > 0;
        }
    }

    private boolean authenticateUser(String username, String password) throws SQLException {
        System.out.println("Authenticating user...");
        String query = "SELECT password FROM users WHERE username = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, username);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                String storedPassword = resultSet.getString("password");
                return BCrypt.checkpw(password, storedPassword); // storedPassword.equals(password); // need to hash and salt passwords
            }
        }
        return false;
    }

    private String createJWT(String username) {
        try {
            System.out.println("Creating JWT");
            return JWT.create()
                    .withIssuer("auth0")
                    .withClaim("username", username)
                    .withExpiresAt(new Date(System.currentTimeMillis() + 24 * 3600 * 1000)) // 1 day expiration
                    .sign(Algorithm.HMAC256(jwtSecret));
        } catch (JWTCreationException exception) {
            System.out.println("Error creating JWT");
            throw new RuntimeException("Error creating JWT", exception);

        }
    }
}

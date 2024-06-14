package com.authservice.server;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.json.JSONObject;
import org.mindrot.jbcrypt.BCrypt;
import java.security.SecureRandom;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.lang.System;
import java.sql.ResultSet;

public class ForgotPasswordHandler implements HttpHandler {
    private Connection connection;

    public ForgotPasswordHandler(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            // Read the request body
            InputStream inputStream = exchange.getRequestBody();
            String requestBody = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            System.out.println("Received JSON payload: " + requestBody);

            try {
                // Parse the JSON payload
                JSONObject jsonObject = new JSONObject(requestBody);
                String username = jsonObject.getString("username");

                // generate new password following contstraints
                String unhashedPassword = generateRandomPassword(10);

                // Hash and salt the password
                String hashedPassword = hashPassword(unhashedPassword);

                // update db with new password: UPDATE auth SET password = (hashed,salted,generated password) WHERE username= (username thats passed)
                updatePasswordInDatabse(username, hashedPassword);
                System.out.println("Changed password for user"); // print username

                // Send a response
                String response = "Reset password successfully";
                exchange.sendResponseHeaders(200, response.getBytes().length);
                OutputStream outputStream = exchange.getResponseBody();
                outputStream.write(response.getBytes());
                outputStream.close();

                // future: add message to RabbitMQ so gmail SMTP microservice can send email notification of new password to email provided
                System.out.println("Password reset successfully:" + unhashedPassword);
                //System.out.println("Added message to RabbitMQ email queue");
            } catch (Exception e) {
                e.printStackTrace();
                String response = "Error processing password change request";
                exchange.sendResponseHeaders(500, response.getBytes().length);
                OutputStream outputStream = exchange.getResponseBody();
                outputStream.write(response.getBytes());
                outputStream.close();
            }
        } else {
            exchange.sendResponseHeaders(405, -1); // 405 Method Not Allowed
        }
    }

    private String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    private void updatePasswordInDatabse(String username, String pass) throws SQLException {
        String updateSQL = "UPDATE users SET password = ? WHERE username = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(updateSQL)) {
            preparedStatement.setString(1, pass);
            preparedStatement.setString(2, username);
            preparedStatement.executeUpdate();
        }
    }

    private String generateRandomPassword(int length) {
        String upperCaseLetters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lowerCaseLetters = upperCaseLetters.toLowerCase();
        String numbers = "0123456789";
        String symbols = "!@#$%^&*()-_=+";
        String allowedChars = upperCaseLetters + lowerCaseLetters + numbers+symbols;

        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            int randomIndex = random.nextInt(allowedChars.length());
            password.append(allowedChars.charAt(randomIndex));
        }

        return password.toString();
    }
}

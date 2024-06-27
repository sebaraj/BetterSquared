/***********************************************************************************************************************
 *  File Name:       ForgotPasswordHandler.java
 *  Project:         Better2/authservice
 *  Author:          Bryan SebaRaj
 *  Description:     Auth-service-level handler to reset password
 **********************************************************************************************************************/
package com.better2.authservice;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.json.JSONObject;
import org.mindrot.jbcrypt.BCrypt;
import java.security.SecureRandom;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.lang.System;
import java.sql.ResultSet;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;

public class ForgotPasswordHandler implements HttpHandler {

    private java.sql.Connection dbConnection;
    private com.rabbitmq.client.Channel rabbitMQChannel;

    public ForgotPasswordHandler(java.sql.Connection dbConnection, com.rabbitmq.client.Channel rabbitMQChannel) {
        this.dbConnection = dbConnection;
        this.rabbitMQChannel = rabbitMQChannel;
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
        if ("PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            try {
                // Reading the request body and parsing JSON payload
                InputStream inputStream = exchange.getRequestBody();
                String requestBody = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                JSONObject jsonObject = new JSONObject(requestBody);
                String username = jsonObject.getString("username");
                System.out.println("ForgotPasswordHandler: Received request and JSON payload.");

                // Generating new hashed/salted password and updating databse
                String unhashedPassword = generateRandomPassword(10);
                String hashedPassword = hashPassword(unhashedPassword);
                updatePasswordInDatabase(username, hashedPassword);
                System.out.println("ForgotPasswordHandler: Changed password for user: " + username);

                // Sending HTTP response to client (via gateway)
                sendResponse(exchange, 200, "Reset password successfully");

                // Publishing message to RabbitMQ
                String queueName = "reset_password_email_queue";
                rabbitMQChannel.queueDeclare(queueName, true, false, false, null);
                JSONObject messageJson = new JSONObject();
                String email = getEmail(username);
                messageJson.put("email", email);
                messageJson.put("password", unhashedPassword);
                String message = messageJson.toString();
                rabbitMQChannel.basicPublish("", queueName, MessageProperties.PERSISTENT_TEXT_PLAIN, message.getBytes(StandardCharsets.UTF_8));

                System.out.println("ForgotPasswordHandler: Message send to RabbitMQ");

            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\": \"Auth service reset password failed\"}");
            }
        } else {
            sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
        }
    }

    private String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    private void updatePasswordInDatabase(String username, String pass) throws SQLException {
        String updateSQL = "UPDATE users SET password = ? WHERE username = ?";
        try (PreparedStatement preparedStatement = dbConnection.prepareStatement(updateSQL)) {
            preparedStatement.setString(1, pass);
            preparedStatement.setString(2, username);
            preparedStatement.executeUpdate();
        }
    }

    private String getEmail(String username) throws SQLException {
        String getSQL = "SELECT email FROM users WHERE username = ?";
        try (PreparedStatement preparedStatement = dbConnection.prepareStatement(getSQL)) {
            preparedStatement.setString(1, username);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("email");
                } else {
                    throw new SQLException();
                }
            }
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

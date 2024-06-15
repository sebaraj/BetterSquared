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

                System.out.println("Password reset successfully:" + unhashedPassword);
                // add message to RabbitMQ so gmail SMTP microservice can send email notification of new password to email provided
                String queueName = "reset_password_email_queue";
                rabbitMQChannel.queueDeclare(queueName, true, false, false, null);

                // Create the JSON message to send to RabbitMQ
                JSONObject messageJson = new JSONObject();
                String email = getEmail(username);
                messageJson.put("email", email);
                messageJson.put("password", unhashedPassword);
                String message = messageJson.toString();

                // Publish the message to the queue
                rabbitMQChannel.basicPublish("", queueName, MessageProperties.PERSISTENT_TEXT_PLAIN, message.getBytes(StandardCharsets.UTF_8));

                System.out.println("Added message to RabbitMQ email queue");
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

/***********************************************************************************************************************
 *  File Name:       SignUpHandler.java
 *  Project:         Better2/authservice
 *  Author:          Bryan SebaRaj
 *  Description:     Auth-service-level handler for user sign up
 **********************************************************************************************************************/
package com.better2.authservice;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.json.JSONObject;
import org.mindrot.jbcrypt.BCrypt;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.lang.System;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class SignUpHandler implements HttpHandler {

    private java.sql.Connection dbConnection;
    private com.rabbitmq.client.Channel rabbitMQChannel;
    private static final String emailRegex = "^[\\w.-]+@([\\w-]+\\.)+[\\w-]{2,4}$";
    private static final Pattern emailPattern = Pattern.compile(emailRegex);

    public SignUpHandler(java.sql.Connection dbConnection, com.rabbitmq.client.Channel rabbitMQChannel) {
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

    private boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) { return false; }
        Matcher matcher = emailPattern.matcher(email);
        return matcher.matches();
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
                String email = jsonObject.getString("email");
                String password = jsonObject.getString("password");
                System.out.println("SignUpHandler: Received request and JSON payload.");

                // Checking if email is valid
                if (!isValidEmail(email)) {
                    sendResponse(exchange, 400, "{\"error\": \"Invalid email address\"}");
                    return;
                }

                // Checking if password is valid (long enough for now)
                if (password.length() < 8) {
                    sendResponse(exchange, 400, "{\"error\": \"Password must be at least 8 characters\"}");
                    return;
                }

                // Hashing+salting the password
                String hashedPassword = hashPassword(password);

                // Inserting the user into the database
                insertUserIntoDatabase(username, email, hashedPassword);
                System.out.println("SignUpHandler: User signed up successfully.");

                // Routing response back to client
                sendResponse(exchange, 200, "User signed up successfully.");

                // Publishing message to RabbitMQ
                String queueName = "signup_email_queue";
                rabbitMQChannel.queueDeclare(queueName, true, false, false, null);
                JSONObject messageJson = new JSONObject();
                messageJson.put("email", email);
                messageJson.put("username", username);
                String message = messageJson.toString();
                rabbitMQChannel.basicPublish("", queueName, MessageProperties.PERSISTENT_TEXT_PLAIN, message.getBytes(StandardCharsets.UTF_8));

                System.out.println("SignUpHandler: Message sent to RabbitMQ.");
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\": \"Auth service sign-up failed\"}");
            }
        } else {
            sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
        }
    }

    private String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    private void insertUserIntoDatabase(String username, String email, String password) throws SQLException {
        String insertSQL = "INSERT INTO users (username, email, password) VALUES (?, ?, ?)";
        try (PreparedStatement preparedStatement = dbConnection.prepareStatement(insertSQL)) {
            preparedStatement.setString(1, username);
            preparedStatement.setString(2, email);
            preparedStatement.setString(3, password);
            preparedStatement.executeUpdate();
        }
    }

}

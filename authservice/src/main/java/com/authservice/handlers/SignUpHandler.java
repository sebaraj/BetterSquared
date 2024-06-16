package com.authservice.server;

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
import java.sql.ResultSet;

public class SignUpHandler implements HttpHandler {

    private java.sql.Connection dbConnection;
    private com.rabbitmq.client.Channel rabbitMQChannel;

    public SignUpHandler(java.sql.Connection dbConnection, com.rabbitmq.client.Channel rabbitMQChannel) {
        this.dbConnection = dbConnection;
        this.rabbitMQChannel = rabbitMQChannel;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            // Read the request body
            InputStream inputStream = exchange.getRequestBody();
            String requestBody = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            System.out.println("Received JSON payload: " + requestBody);

            try {
                // Parse the JSON payload
                JSONObject jsonObject = new JSONObject(requestBody);
                String username = jsonObject.getString("username");
                String email = jsonObject.getString("email");
                String password = jsonObject.getString("password");
                //int roleInt = getRoleInt((String) jsonObject.optString("role", "Standard"));
                // Hash and salt the password
                String hashedPassword = hashPassword(password);

                // Insert the sign-up information into the database
                insertUserIntoDatabase(username, email, hashedPassword, roleInt);
                System.out.println("User: " + username + " created and inserted into database.");
                // Send a response
                String response = "User signed up successfully";
                exchange.sendResponseHeaders(200, response.getBytes().length);
                OutputStream outputStream = exchange.getResponseBody();
                outputStream.write(response.getBytes());
                outputStream.close();

                // add message to RabbitMQ so gmail SMTP microservice can send email notification of new account to email provided
                String queueName = "signup_email_queue";
                rabbitMQChannel.queueDeclare(queueName, true, false, false, null);

                // Create the JSON message to send to RabbitMQ
                JSONObject messageJson = new JSONObject();
                messageJson.put("email", email);
                messageJson.put("username", username);
                String message = messageJson.toString();

                // Publish the message to the queue
                rabbitMQChannel.basicPublish("", queueName, MessageProperties.PERSISTENT_TEXT_PLAIN, message.getBytes(StandardCharsets.UTF_8));

                System.out.println("Sent '" + message + "' to RabbitMQ email queue");
            } catch (Exception e) {
                e.printStackTrace();
                String response = "Error processing sign-up request";
                exchange.sendResponseHeaders(500, response.getBytes().length);
                OutputStream outputStream = exchange.getResponseBody();
                outputStream.write(response.getBytes());
                outputStream.close();
            }
        } else {
            // Method not allowed
            exchange.sendResponseHeaders(405, -1); // 405 Method Not Allowed
        }
    }

    private String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    private void insertUserIntoDatabase(String username, String email, String password, int role) throws SQLException {
        String insertSQL = "INSERT INTO users (username, email, password) VALUES (?, ?, ?)";
        try (PreparedStatement preparedStatement = dbConnection.prepareStatement(insertSQL)) {
            preparedStatement.setString(1, username);
            preparedStatement.setString(2, email);
            preparedStatement.setString(3, password);
            preparedStatement.executeUpdate();
        }
    }

}

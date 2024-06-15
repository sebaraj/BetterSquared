package com.groupservice.server;

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

public class CreateGroupHandler implements HttpHandler {

    private java.sql.Connection dbConnection;
    //private com.rabbitmq.client.Channel rabbitMQChannel;

    public CreateGroupHandler(java.sql.Connection dbConnection) {
        this.dbConnection = dbConnection;
        //this.rabbitMQChannel = rabbitMQChannel;
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
                String gorupName = jsonObject.getString("groupName");
                // Create group in DB

                // Send a response
                String response = "Group created successfully up successfully";
                exchange.sendResponseHeaders(200, response.getBytes().length);
                OutputStream outputStream = exchange.getResponseBody();
                outputStream.write(response.getBytes());
                outputStream.close();


            } catch (Exception e) {
                e.printStackTrace();
                String response = "Error processing group creation request";
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

    private int getRoleInt(String role) throws SQLException {
        String getSQL = "SELECT id FROM roles WHERE role_name = ?";
        try (PreparedStatement preparedStatement = dbConnection.prepareStatement(getSQL)) {
            preparedStatement.setString(1, role);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("id");
                } else {
                    throw new SQLException();
                }
            }
        }
    }

//   Modify this to create new group
//    private void insertUserIntoDatabase(String username, String email, String password, int role) throws SQLException {
//        String insertSQL = "INSERT INTO users (username, email, password, role_id) VALUES (?, ?, ?, ?)";
//        try (PreparedStatement preparedStatement = dbConnection.prepareStatement(insertSQL)) {
//            preparedStatement.setString(1, username);
//            preparedStatement.setString(2, email);
//            preparedStatement.setString(3, password);
//            preparedStatement.setInt(4, role);
//            preparedStatement.executeUpdate();
//        }
//    }

}

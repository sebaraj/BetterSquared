package com.groupservice.server;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.Headers;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Map;
import java.util.HashMap;

public class GroupsHandler implements HttpHandler {

    private java.sql.Connection dbConnection;
    //private String clientUsername;

    public GroupsHandler(java.sql.Connection dbConnection) {
        //this.clientUsername = clientUsername;
        this.dbConnection = dbConnection;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String clientUsername; // = "bryans"; //  (String) exchange.getAttribute("username");
            Headers requestHeaders = exchange.getRequestHeaders();
            if (requestHeaders.containsKey("username")) {
                clientUsername = requestHeaders.getFirst("username");
            }
            int page = 0;
            URI requestUri = exchange.getRequestURI();
            String path = requestUri.getPath();
            String query = requestUri.getQuery();
            Map<String, String> queryParams = parseQueryParams(query);
            String pageStr = queryParams.getOrDefault("page", "-1");
            page = Integer.parseInt(pageStr);
            String group_name = queryParams.getOrDefault("name", "");

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                // if pageSTR is -1 and group_name not empty
                if (page > -1 && !group_name.isEmpty()) {
                    handleGetGroupsByName(exchange, group_name, page);
                    return;
                } else if (page > -1 && group_name.isEmpty()) {
                    handleGetGroupsWithoutName(exchange, page);
                    return;
                }
            }
            String response = "Method not allowed";
            sendResponse(exchange, 405, response);

        } catch (Exception e) {
            e.printStackTrace();
            String response = "Not allowed";
            sendResponse(exchange, 500, response);
        }

    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> queryParams = new HashMap<>();
        if (query != null && !query.isEmpty()) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length > 1) {
                    queryParams.put(keyValue[0], keyValue[1]);
                } else {
                    queryParams.put(keyValue[0], "");
                }
            }
        }
        return queryParams;
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(response.getBytes());
            outputStream.close();
        }
    }

    private boolean has_been_deleted(String group_name) throws SQLException {
        String query = "SELECT * FROM groups WHERE group_name = ?";
        try (PreparedStatement statement = dbConnection.prepareStatement(query)) {
            statement.setString(1, group_name);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getBoolean("has_been_deleted");
                } else {
                    throw new SQLException("Group not found");
                }
            }
        }
    }

    private void handleGetGroupsByName(HttpExchange exchange, String name, int page) throws IOException {
        try {
            String query = "SELECT * FROM groups WHERE group_name LIKE ? AND has_been_deleted = false LIMIT 50 OFFSET ?"; // get all groups with start with the substring name
            try (PreparedStatement statement = dbConnection.prepareStatement(query)) {
                statement.setString(1, name + "%");
                statement.setInt(2, page*50);
                try (ResultSet resultSet = statement.executeQuery()) {
                    JSONArray groupsArray = new JSONArray();
                    while (resultSet.next()) {
                        JSONObject groupJson = new JSONObject();
                        groupJson.put("group_name", resultSet.getString("group_name"));
                        groupJson.put("start_date", resultSet.getDate("start_date"));
                        groupJson.put("end_date", resultSet.getDate("end_date"));
                        groupJson.put("is_active", resultSet.getBoolean("is_active"));
                        groupJson.put("starting_cash", resultSet.getFloat("starting_cash"));
                        groupsArray.put(groupJson);
                    }

                    String response = groupsArray.toString();
                    sendResponse(exchange, 200, response);
                }
            }
        } catch (SQLException e) {
            String response = "Groups with that name do not exist.";
            sendResponse(exchange, 404, response);
        }
    }

    private void handleGetGroupsWithoutName(HttpExchange exchange, int page) throws IOException {
        try {
            String query = "SELECT * FROM groups WHERE has_been_deleted = false AND end_date > CURRENT_DATE ORDER BY end_date LIMIT 50 OFFSET ?";
            try (PreparedStatement statement = dbConnection.prepareStatement(query)) {
                statement.setInt(1, page*50);
                try (ResultSet resultSet = statement.executeQuery()) {
                    JSONArray groupsArray = new JSONArray();
                    while (resultSet.next()) {
                        JSONObject groupJson = new JSONObject();
                        groupJson.put("group_name", resultSet.getString("group_name"));
                        groupJson.put("start_date", resultSet.getDate("start_date"));
                        groupJson.put("end_date", resultSet.getDate("end_date"));
                        groupJson.put("is_active", resultSet.getBoolean("is_active"));
                        groupJson.put("starting_cash", resultSet.getFloat("starting_cash"));
                        groupsArray.put(groupJson);
                    }

                    String response = groupsArray.toString();
                    sendResponse(exchange, 200, response);
                }
            }
        } catch (SQLException e) {
            String response = "Failed to get groups.";
            sendResponse(exchange, 500, response);
        }
    }


}

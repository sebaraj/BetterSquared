/***********************************************************************************************************************
 *  File Name:       GroupsHandler.java
 *  Project:         Better2/groupservice
 *  Author:          Bryan SebaRaj
 *  Description:     Handler for multi-group GET HTTP traffic for group service
 **********************************************************************************************************************/
package com.better2.groupservice;

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
    private Pattern groupsPattern = Pattern.compile("/groups"); // get all groups that belong to user that called it
    private Pattern searchPattern = Pattern.compile("/groups/search"); // get all groups that fit search parameters

    public GroupsHandler(java.sql.Connection dbConnection) {
        this.dbConnection = dbConnection;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            // Getting client username from HTTP header
            String clientUsername = "";
            Headers requestHeaders = exchange.getRequestHeaders();
            if (requestHeaders.containsKey("username")) {
                clientUsername = requestHeaders.getFirst("username");
            } else {
                sendResponse(exchange, 401, "{\"error\": \"Group service did not receive a username\"}");
                return;
            }

            // Matching regex patterns and extracting query parameters
            int page = 0;
            URI requestUri = exchange.getRequestURI();
            String path = requestUri.getPath();
            Matcher groupsMatcher = groupsPattern.matcher(path);
            Matcher searchMatcher = searchPattern.matcher(path);
            String query = requestUri.getQuery();
            Map<String, String> queryParams = parseQueryParams(query);
            String pageStr = queryParams.getOrDefault("page", "-1");
            page = Integer.parseInt(pageStr);
            String group_name = queryParams.getOrDefault("name", "");

            // Routing traffic to respective sub-handler
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                if (groupsMatcher.matches()) {
                    handlerGetAccountsOfUser(exchange, clientUsername);
                    return;
                } else if (searchMatcher.matches()) {
                    if (page > -1 && !"".equals(group_name)) {
                        System.out.println("GroupsHandler: Handling GetGroupsByName.");
                        handleGetGroupsByName(exchange, group_name, page);
                        return;
                    } else if (page > -1 && group_name.isEmpty()) {
                        System.out.println("GroupsHandler: Handling GetGroupsWithoutName.");
                        handleGetGroupsWithoutName(exchange, page);
                        return;
                    }
                } else {
                    sendResponse(exchange, 400, "{\"error\": \"Incorrect query parameters\"}");
                    return;
                }
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "{\"error\": \"Group service multi-group GET failed\"}");
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

    private void handlerGetAccountsOfUser(HttpExchange exchange, String username) throws IOException {
        try {
            String query = "SELECT * FROM accounts WHERE username = ?";
            try (PreparedStatement statement = dbConnection.prepareStatement(query)) {
                statement.setString(1, username);
                try (ResultSet resultSet = statement.executeQuery()) {
                    JSONArray groupsArray = new JSONArray();
                    while (resultSet.next()) {
                        JSONObject groupJson = new JSONObject();
                        groupJson.put("group_name", resultSet.getString("group_name"));
                        groupJson.put("current_cash", resultSet.getFloat("current_cash"));
                        String groupQuery = "SELECT * FROM groups WHERE group_name = ?";
                        try (PreparedStatement groupStatement = dbConnection.prepareStatement(groupQuery)) {
                            groupStatement.setString(1, resultSet.getString("group_name"));
                            try (ResultSet groupResultSet = groupStatement.executeQuery()) {
                                if (groupResultSet.next()) {
                                    groupJson.put("start_date",  groupResultSet.getDate("start_date"));
                                    groupJson.put("end_date",  groupResultSet.getDate("end_date"));
                                    groupJson.put("is_active",  groupResultSet.getBoolean("is_active"));
                                    groupJson.put("starting_cash",  groupResultSet.getFloat("starting_cash"));
                                    if (!groupResultSet.getBoolean("has_been_deleted")) {
                                        groupsArray.put(groupJson);
                                    }
                                } else {
                                    throw new SQLException("Group not found");
                                }
                            }
                        }

                    }
                    String response = groupsArray.toString();
                    sendResponse(exchange, 200, response);
                }
            }
        } catch (SQLException e) {
            sendResponse(exchange, 500, "{\"error\": \"Could not get user accounts\"}");
        }
    }

    private void handleGetGroupsByName(HttpExchange exchange, String name, int page) throws IOException {
        try {
            String query = "SELECT * FROM groups WHERE group_name LIKE ? AND has_been_deleted = false LIMIT 50 OFFSET ?";
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
            sendResponse(exchange, 500, "{\"error\": \"Could not get groups by substring\"}");
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
            sendResponse(exchange, 500, "{\"error\": \"Could not get groups without name\"}");
        }
    }


}

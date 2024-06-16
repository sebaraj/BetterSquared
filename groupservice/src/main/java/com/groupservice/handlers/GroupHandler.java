package com.groupservice.server;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.Filter;
import org.json.JSONObject;
import org.mindrot.jbcrypt.BCrypt;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.lang.System;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.lang.Integer;


public class CreateGroupHandler implements HttpHandler {

    private java.sql.Connection dbConnection;
    private String clientUsername;
    private Pattern createGroupPattern = Pattern.compile("/group"); // used to create group w/o groupname
    private Pattern rudGroupPattern = Pattern.compile("/group/([^/]+)"); // used for update, get, delete group
    private Pattern joinGroupPattern = Pattern.compile("/group/([^/]+)/join"); // group/group_name/join
    private Pattern leaveGroupPattern = Pattern.compile("/group/([^/]+)/leave"); // group/group_name/leave
    private Pattern getLeaderboardPattern = Pattern.compile("/group/([^/]+)/leaderboard"); // group/group_name/leaderboard?page=n
    private Pattern getUserListPattern = Pattern.compile("/group/([^/]+)/users"); // group/group_name/users?page=n
    private Pattern makeAdminPattern = Pattern.compile("/group/([^/]+)/admin/([^/]+)"); // group/group_name/admin/username
    private Pattern getUserBetCashPattern = Pattern.compile("/group/([^/]+)/user/([^/]+)"); // group/group_name/user/username

    public CreateGroupHandler(java.sql.Connection dbConnection, String clientUsername) {
        this.clientUsername = clientUsername;
        this.dbConnection = dbConnection;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String targetUsername, group_name;
        int page;
        URI requestUri = exchange.getRequestURI();
        String path = requestUri.getPath();
        Matcher createGroupMatcher = createGroupPattern.matcher(path);
        Matcher rudGroupMatcher = rudGroupPatter.matcher(path);
        Matcher joinGroupMatcher = joinGroupPattern.matcher(path);
        Matcher leaveGroupMatcher = leaveGroupPattern.matcher(path);
        Matcher getLeaderboardMatcher = getLeaderboardPattern.matcher(path);
        Matcher getUserListMatcher = getUserListPattern.matcher(path);
        Matcher makeAdminMatcher = makeAdminPattern.matcher(path);
        Matcher getUserBetCashMatcher = getUserBetCashPattern.matcher(path);

        try {
            switch (exchange.getRequestMethod()) {
                case "GET":
                    if (rudGroupMatcher.matches()) {
                        group_name = rudGroupMatcher.group(1);
                        handleGetGroup(exchange, group_name, clientUsername);
                    } else if (leaveGroupMatcher.matches()) {
                        group_name = leaveGroupMatcher.group(1);
                        // parse URI to get page
                        handleGetLeaderboard(exchange, group_name, clientUsername, page);
                    } else if (getUserListMatcher.matches()) {
                        group_name = getUserListMatcher.group(1);
                        // parse URI to get page
                        handleGetUserList(exchange, group_name, clientUsername, page);
                    } else if (getUserBetCashMatcher.matcher()) {
                        group_name = getUserBetCashMatcher.group(1);
                        targetUsername = getUserBetCashMatcher.group(2);
                        handleGetBetCash(exchange, clientUsername, group_name, targetUsername);
                    } else {
                        exchange.sendResponseHeaders(405, -1);
                    }
                    break;
                case "POST":
                    // use post to create new gorup
                    if (createGroupMatcher.matches()) {
                        handleCreateGroup(exchange, clientUsername);
                    } else if (joinGroupMatcher.matches()) {
                        group_name = joinGroupMatcher.group(1);
                        handleJoinGroup(exchange, group_name, clientUsername);
                    } else {
                        exchange.sendResponseHeaders(405, -1); // Method Not Allowed
                    }
                    break;
                case "PUT":
                    if (rudGroupMatcher.matches()) {
                        group_name = rudGroupMatcher.group(1);
                        handleUpdateGroup(exchange, group_name, clientUsername);
                    } else if (makeAdminMatcher.matches()) {
                        group_name = makeAdminMatcher.group(1);
                        targetUsernam = makeAdminMatcher.group(2);
                        handleMakeAdmin(exchange, group_name, clientUsername, targetUsername);
                    } else {
                        exchange.sendResponseHeaders(405, -1);
                    }
                    break;
                case "DELETE":
                    if (rudGroupMatcher.matches()) {
                        group_name = rudGroupMatcher.group(1);
                        handleDeleteGroup(exchange, group_name, clientUsername);
                    } else if (leaveGroupMatcher.matches()) {
                        group_name = leaveGroupMatcher.group(1);
                        handleLeaveGroup(exchange, group_name, clientUsername);
                    } else {
                        exchange.sendResponseHeaders(405, -1);
                    }
                    break;
                default:
                    exchange.sendResponseHeaders(405, -1); // Method Not Allowed
                    break;
            }
        } catch (IOException | Exception e) {
            exchange.sendResponseHeaders(405, -1);
        }

    }

    private int roleInGroup(String username, int group_name) throw SQLException {
        String query = "SELECT group_role_id FROM accounts WHERE username = ? AND group_name = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, username);
            preparedStatement.setString(2, group_name);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("group_role_id");
                } else {
                    throw new SQLException("No such username and group_name combination found in accounts table.");
                }
            }
        }
    }

    private void handleGetGroup(HttpExchange exchange, String group_name, String username) throws IOException {
        try {
            if (roleInGroup(username, group_name) > 3) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            // parse exchange request to get necessary values
            String query = "SELECT * FROM groups WHERE group_name = ?";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, group_name);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        JSONObject messageJson = new JSONObject();
                        messageJson.put("group_name", resultSet.getString(group_name));
                        messageJson.put("created_at", resultSet.getDate(created_at));
                        messageJson.put("start_date", resultSet.getDate(start_date));
                        messageJson.put("end_date", resultSet.getDate(end_date));
                        messageJson.put("is_active", resultSet.getBoolean(is_active));
                        messageJson.put("starting_cash", resultSet.getFloat(starting_cash));
                        String message = messageJson.toString();
                        exchange.sendResponseHeaders(200, 0);
                        OutputStream output = exchange.getResponseBody();
                        output.write(message.getBytes());
                        output.close();
                        // Set other properties as needed
                        return group;
                    } else {
                        throw new SQLException("Group not found");
                    }
                }
            } catch (SQLException e) {
                System.out.println("Get group failed.")
                e.printStackTrace();
                String response = "Get group failed.";
                exchange.sendResponseHeaders(500, response.getBytes().length);
                OutputStream outputStream = exchange.getResponseBody();
                outputStream.write(response.getBytes());
                outputStream.close();
            }

        }
    }

    private void handleCreateGroup(HttpExchange exchange, String username) throws IOException {
        try {
            if (roleInGroup(username, group_name) > 3) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            // parse exchange request to get necessary values

            try {
                String insert = "INSERT INTO groups () VALUES (?,?,?,?,?)";
                try (PreparedStatement preparedStatement = connection.prepareStatement(insert)) {
                    preparedStatement.setString(1, username);
                    preparedStatement.setString(2, group_name);
                    try (ResultSet resultSet = preparedStatement.executeQuery()) {
                        System.out.println("Inserted successfully");
                    } catch (SQLException e) {
                        System.out.println(e.getMessage());
                    }
                }
            } catch (SQLException e) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }


        } catch (SQLException e) {
            exchange.sendResponseHeaders(405, -1);
        }
    }

    private void handleDeleteGroup(HttpExchange exchange, String group_name, String username) throws IOException {

    }

    private void handleUpdateGroup(HttpExchange exchange, String group_name, String username) throws IOException {
        // need to be group creator or group admin
    }

    private void handleJoinGroup(HttpExchange exchange, String group_name, String username) throws IOException {

    }

    private void handleLeaveGroup(HttpExchange exchange, String group_name, String username) throws IOException {

    }

    private void handleGetLeaderboard(HttpExchange exchange, String group_name, String username, int page) throws IOException {

    }

    private void handleGetUserList(HttpExchange exchange, String group_name, String username, int page) throws IOException {

    }

    private void handleMakeAdmin(HttpExchange exchange, String group_name, String username) throws IOException {

    }

    private void handleGetBetCash(HttpExchange exchange, String group_name, String clientUsername, String targetUsername) throws IOException {

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

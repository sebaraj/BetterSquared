package com.groupservice.server;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.Headers;
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

public class GroupHandler implements HttpHandler {

    private Connection dbConnection;
    //private String clientUsername;

    private Pattern createGroupPattern = Pattern.compile("/group"); // used to create group w/o groupname
    private Pattern rudGroupPattern = Pattern.compile("/group/([^/]+)/"); // used for update, get, delete group
    private Pattern joinGroupPattern = Pattern.compile("/group/([^/]+)/join"); // group/group_name/join
    private Pattern leaveGroupPattern = Pattern.compile("/group/([^/]+)/leave"); // group/group_name/leave
    private Pattern getLeaderboardPattern = Pattern.compile("/group/([^/]+)/leaderboard"); // group/group_name/leaderboard?page=n
    private Pattern getUserListPattern = Pattern.compile("/group/([^/]+)/users"); // group/group_name/users?page=n
    private Pattern makeAdminPattern = Pattern.compile("/group/([^/]+)/admin/([^/]+)"); // group/group_name/admin/username
    private Pattern getUserBetCashPattern = Pattern.compile("/group/([^/]+)/user/([^/]+)"); // group/group_name/user/username

    public GroupHandler(Connection dbConnection) {
        //this.clientUsername = clientUsername;
        this.dbConnection = dbConnection;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String clientUsername = "bryans"; //  (String) exchange.getAttribute("username");
        Headers requestHeaders = exchange.getRequestHeaders();
        if (requestHeaders.containsKey("username")) {
            clientUsername = requestHeaders.getFirst("username");
        }
        String targetUsername, group_name;
        int page = 0;
        URI requestUri = exchange.getRequestURI();
        String path = requestUri.getPath();
        System.out.println(path);
        Matcher createGroupMatcher = createGroupPattern.matcher(path);
        Matcher rudGroupMatcher = rudGroupPattern.matcher(path);
        Matcher joinGroupMatcher = joinGroupPattern.matcher(path);
        Matcher leaveGroupMatcher = leaveGroupPattern.matcher(path);
        Matcher getLeaderboardMatcher = getLeaderboardPattern.matcher(path);
        Matcher getUserListMatcher = getUserListPattern.matcher(path);
        Matcher makeAdminMatcher = makeAdminPattern.matcher(path);
        Matcher getUserBetCashMatcher = getUserBetCashPattern.matcher(path);
        System.out.println(exchange.getRequestMethod());

        try {
            switch (exchange.getRequestMethod()) {
                case "GET":
                    if (rudGroupMatcher.matches()) {
                        //System.out.println("matched");
                        group_name = rudGroupMatcher.group(1);
                        handleGetGroup(exchange, group_name, clientUsername);
                    } else if (leaveGroupMatcher.matches()) {
                        group_name = leaveGroupMatcher.group(1);
                        handleLeaveGroup(exchange, group_name, clientUsername);
                    } else if (getUserListMatcher.matches()) {
                        group_name = getUserListMatcher.group(1);
                        handleGetUserList(exchange, group_name, clientUsername, page);
                    } else if (getUserBetCashMatcher.matches()) {
                        group_name = getUserBetCashMatcher.group(1);
                        targetUsername = getUserBetCashMatcher.group(2);
                        handleGetBetCash(exchange, clientUsername, group_name, targetUsername);
                    } else {
                        //System.out.println("no match. reached the end of get switch");
                        exchange.sendResponseHeaders(405, -1);
                    }
                    break;
                case "POST":
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
                        targetUsername = makeAdminMatcher.group(2);
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
                        //System.out.println("no match. reached the end of get switch pt 4");
                        exchange.sendResponseHeaders(405, -1);
                    }
                    break;
                default:
                    exchange.sendResponseHeaders(405, -1); // Method Not Allowed
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
            exchange.sendResponseHeaders(500, -1);
        }
    }

    private int roleInGroup(String username, String group_name) throws SQLException {
        String query = "SELECT group_role_id FROM accounts WHERE username = ? AND group_name = ?";
        try (PreparedStatement preparedStatement = dbConnection.prepareStatement(query)) {
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

    private void handleGetGroup(HttpExchange exchange, String group_name, String username) throws IOException, SQLException {
        //System.out.println("Group name:" + group_name);
        //System.out.println("username:" + username);
        if (roleInGroup(username, group_name) > 3) {
            exchange.sendResponseHeaders(403, -1); // Forbidden
            return;
        }
        System.out.println("Group name: " + group_name);
        String query = "SELECT * FROM groups WHERE group_name = ?";
        try (PreparedStatement statement = dbConnection.prepareStatement(query)) {
            statement.setString(1, group_name);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    JSONObject messageJson = new JSONObject();
                    messageJson.put("group_name", resultSet.getString("group_name"));
                    messageJson.put("created_at", resultSet.getDate("created_at"));
                    messageJson.put("start_date", resultSet.getDate("start_date"));
                    messageJson.put("end_date", resultSet.getDate("end_date"));
                    messageJson.put("is_active", resultSet.getBoolean("is_active"));
                    messageJson.put("starting_cash", resultSet.getFloat("starting_cash"));
                    String message = messageJson.toString();
                    exchange.sendResponseHeaders(200, message.getBytes().length);
                    OutputStream output = exchange.getResponseBody();
                    output.write(message.getBytes());
                    output.close();
                } else {
                    throw new SQLException("Group not found");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            String response = "Get group failed.";
            exchange.sendResponseHeaders(500, response.getBytes().length);
            OutputStream outputStream = exchange.getResponseBody();
            outputStream.write(response.getBytes());
            outputStream.close();
        }
    }

    private void handleCreateGroup(HttpExchange exchange, String username) throws IOException {
        try {
            InputStream inputStream = exchange.getRequestBody();
            String requestBody = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            System.out.println("Received JSON payload: " + requestBody);

            JSONObject jsonObject = new JSONObject(requestBody);
            String group_name = jsonObject.getString("group_name");
            Date start_date = Date.valueOf(jsonObject.getString("start_date"));
            Date end_date = Date.valueOf(jsonObject.getString("end_date"));
            boolean is_active = start_date.before(new Date(System.currentTimeMillis()));
            float starting_cash = (float) jsonObject.getDouble("starting_cash");
            try {
                // Begin transaction
                dbConnection.setAutoCommit(false);
                String insertGroup = "INSERT INTO groups (group_name, start_date, end_date, is_active, starting_cash) VALUES (?,?,?,?,?)";
                try (PreparedStatement preparedStatement = dbConnection.prepareStatement(insertGroup)) {
                    preparedStatement.setString(1, group_name);
                    preparedStatement.setDate(2, start_date);
                    preparedStatement.setDate(3, end_date);
                    preparedStatement.setBoolean(4, is_active);
                    preparedStatement.setFloat(5, starting_cash);
                    preparedStatement.executeUpdate();
                    System.out.println("Inserted group successfully");
                }

                String insertAccount = "INSERT INTO accounts (username, group_name, group_role_id) VALUES (?,?,?)";
                int group_creater = 1;
                try (PreparedStatement preparedStatement = dbConnection.prepareStatement(insertAccount)) {
                    preparedStatement.setString(1, username);
                    preparedStatement.setString(2, group_name);
                    preparedStatement.setInt(3, group_creater);
                    preparedStatement.executeUpdate();
                    System.out.println("Inserted creator account successfully");
                }
                dbConnection.commit();
                String response = "Group formed successfully";
                exchange.sendResponseHeaders(200, response.getBytes().length);
                OutputStream outputStream = exchange.getResponseBody();
                outputStream.write(response.getBytes());
                outputStream.close();
            } catch (SQLException e) {
                // Rollback transaction if an exception occurs
                try {
                    if (dbConnection != null) {
                        dbConnection.rollback();
                    }
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
                e.printStackTrace();
            } finally {
                // Reset auto-commit mode
                try {
                    dbConnection.setAutoCommit(true);
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            // need to delete group insertion into groups to make whole transaction atomic
            exchange.sendResponseHeaders(500, -1); // Internal Server Error
        }
    }

    private void handleDeleteGroup(HttpExchange exchange, String group_name, String username) { //throws IOException {
        if (roleInGroup(username, group_name) > 1) {
            exchange.sendResponseHeaders(403, -1); // Forbidden
            return;
        }
        //System.out.println("Group name: " + group_name);
        String delete = "DELETE FROM groups WHERE group_name = ?";
        try (PreparedStatement statement = dbConnection.prepareStatement(delete)) {
            statement.setString(1, group_name);
            int rowsAffected = statement.executeUpdate();

            if (rowsAffected > 0) {
                String message = group_name + " successfully deleted.";
                exchange.sendResponseHeaders(200, message.getBytes().length);
                OutputStream output = exchange.getResponseBody();
                output.write(message.getBytes());
                output.close();
            } else {
                String message = "Group not found.";
                exchange.sendResponseHeaders(404, message.getBytes().length); // Not Found
                OutputStream output = exchange.getResponseBody();
                output.write(message.getBytes());
                output.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            String response = "Delete group failed.";
            exchange.sendResponseHeaders(500, response.getBytes().length);
            OutputStream outputStream = exchange.getResponseBody();
            outputStream.write(response.getBytes());
            outputStream.close();
        }
    }

    private void handleUpdateGroup(HttpExchange exchange, String group_name, String username) { //throws IOException {
        System.out.println("Not done");// need to be group creator or group admin
    }

    private void handleJoinGroup(HttpExchange exchange, String group_name, String username) { //throws IOException {
        System.out.println("Not done");
    }

    private void handleLeaveGroup(HttpExchange exchange, String group_name, String username) { //throws IOException {
        System.out.println("Not done");
    }

    private void handleGetLeaderboard(HttpExchange exchange, String group_name, String username, int page) { //throws IOException {
        System.out.println("Not done");
    }

    private void handleGetUserList(HttpExchange exchange, String group_name, String username, int page) { //throws IOException {
        System.out.println("Not done");
    }

    private void handleMakeAdmin(HttpExchange exchange, String group_name, String username, String targetUsername) { //throws IOException {
        System.out.println("Not done");
    }

    private void handleGetBetCash(HttpExchange exchange, String group_name, String clientUsername, String targetUsername) { //throws IOException {
        System.out.println("Not done");
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

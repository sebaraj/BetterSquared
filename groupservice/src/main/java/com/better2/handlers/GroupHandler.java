/***********************************************************************************************************************
 *  File Name:       GroupHandler.java
 *  Project:         Better2/groupservice
 *  Author:          Bryan SebaRaj
 *  Description:     Handler for single-group GET and all POST/PUT/DELETE HTTP traffic for group service
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
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Map;
import java.util.HashMap;


public class GroupHandler implements HttpHandler {

    private Connection dbConnection;
    private Pattern createGroupPattern = Pattern.compile("/group"); // POST. used to create group w/o groupname
    private Pattern rudGroupPattern = Pattern.compile("/group/([^/]+)"); // GET, PUT, DELETE. used for update, get, delete group
    private Pattern joinGroupPattern = Pattern.compile("/group/([^/]+)/join"); // POST. group/group_name/join
    private Pattern leaveGroupPattern = Pattern.compile("/group/([^/]+)/leave"); // DELETE. group/group_name/leave
    private Pattern getUserListPattern = Pattern.compile("/group/([^/]+)/users"); // GET. group/group_name/users?page=n
    private Pattern makeAdminPattern = Pattern.compile("/group/([^/]+)/admin"); // PUT. group/group_name/admin/ (username in body)
    private Pattern getUserBetCashPattern = Pattern.compile("/group/([^/]+)/user/([^/]+)"); // GET. group/group_name/user/username

    public GroupHandler(Connection dbConnection) {
        this.dbConnection = dbConnection;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
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
        String targetUsername, group_name;
        int page = 0;
        URI requestUri = exchange.getRequestURI();
        String path = requestUri.getPath();
        Matcher createGroupMatcher = createGroupPattern.matcher(path);
        Matcher rudGroupMatcher = rudGroupPattern.matcher(path);
        Matcher joinGroupMatcher = joinGroupPattern.matcher(path);
        Matcher leaveGroupMatcher = leaveGroupPattern.matcher(path);
        Matcher getUserListMatcher = getUserListPattern.matcher(path);
        Matcher makeAdminMatcher = makeAdminPattern.matcher(path);
        Matcher getUserBetCashMatcher = getUserBetCashPattern.matcher(path);

        // Routing traffic to respective sub-handler
        try {
            switch (exchange.getRequestMethod()) {
                case "GET":
                    if (rudGroupMatcher.matches()) {
                        System.out.println("GroupHandler: Handling GET GroupMatcher.");
                        group_name = rudGroupMatcher.group(1);
                        handleGetGroup(exchange, group_name, clientUsername);
                    } else if (getUserListMatcher.matches()) {
                        System.out.println("GroupHandler: Handling GetUserList.");
                        group_name = getUserListMatcher.group(1);
                        String query = requestUri.getQuery();
                        Map<String, String> queryParams = parseQueryParams(query);
                        String pageStr = queryParams.getOrDefault("page", "0");
                        page = Integer.parseInt(pageStr);
                        handleGetUserList(exchange, group_name, clientUsername, page);
                    } else if (getUserBetCashMatcher.matches()) {
                        System.out.println("GroupHandler: Handling GetBetCash.");
                        group_name = getUserBetCashMatcher.group(1);
                        targetUsername = getUserBetCashMatcher.group(2);
                        handleGetBetCash(exchange, group_name, clientUsername, targetUsername);
                    } else {
                        System.out.println("GroupHandler: Invalid GET request.");
                        sendResponse(exchange, 404, "{\"error\": \"GroupService: endpoint does not exit\"}");
                    }
                    break;
                case "POST":
                    if (createGroupMatcher.matches()) {
                        System.out.println("GroupHandler: Handling CreateGroup.");
                        handleCreateGroup(exchange, clientUsername);
                    } else if (joinGroupMatcher.matches()) {
                        System.out.println("GroupHandler: Handling JoinGroup.");
                        group_name = joinGroupMatcher.group(1);
                        handleJoinGroup(exchange, group_name, clientUsername);
                    } else {
                        System.out.println("GroupHandler: Invalid POST request.");
                        sendResponse(exchange, 404, "{\"error\": \"GroupService: endpoint does not exit\"}");
                    }
                    break;
                case "PUT":
                    if (rudGroupMatcher.matches()) {
                        System.out.println("GroupHandler: Handling PUT GroupMatcher.");
                        group_name = rudGroupMatcher.group(1);
                        handleUpdateGroup(exchange, group_name, clientUsername);
                    } else if (makeAdminMatcher.matches()) {
                        System.out.println("GroupHandler: Handling MakeAdmin.");
                        group_name = makeAdminMatcher.group(1);
                        handleToggleAdmin(exchange, group_name, clientUsername);
                    } else {
                        System.out.println("GroupHandler: Invalid PUT request.");
                        sendResponse(exchange, 404, "{\"error\": \"GroupService: endpoint does not exit\"}");
                    }
                    break;
                case "DELETE":
                    if (rudGroupMatcher.matches()) {
                        System.out.println("GroupHandler: Handling DELETE GroupMatcher.");
                        group_name = rudGroupMatcher.group(1);
                        handleDeleteGroup(exchange, group_name, clientUsername);
                    } else if (leaveGroupMatcher.matches()) {
                        System.out.println("GroupHandler: Handling LeaveGroup.");
                        group_name = leaveGroupMatcher.group(1);
                        handleLeaveGroup(exchange, group_name, clientUsername);
                    } else {
                        System.out.println("GroupHandler: Invalid DELETE request.");
                        sendResponse(exchange, 404, "{\"error\": \"GroupService: endpoint does not exit\"}");
                    }
                    break;
                default:
                    System.out.println("GroupsHandler: Invalid method.");
                    sendResponse(exchange, 405, "{\"error\": \"GroupService: invalid method\"}");
                    break;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "{\"error\": \"Group service request failed\"}");
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
        if (has_been_deleted(group_name)) {
            sendResponse(exchange, 410, "{\"error\": \"Group has been deleted\"}");
            return;
        }
        //if (roleInGroup(username, group_name) > 3) {
        //    sendResponse(exchange, 403, "{\"error\": \"User not in group\"}");
        //    return;
       // }
        String query = "SELECT * FROM groups WHERE group_name = ?";
        try (PreparedStatement statement = dbConnection.prepareStatement(query)) {
            statement.setString(1, group_name);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    JSONObject messageJson = new JSONObject();
                    messageJson.put("group_name", resultSet.getString("group_name"));
                    messageJson.put("created_at", resultSet.getTimestamp("created_at"));
                    messageJson.put("start_date", resultSet.getTimestamp("start_date"));
                    messageJson.put("end_date", resultSet.getTimestamp("end_date"));
                    messageJson.put("is_active", resultSet.getBoolean("is_active"));
                    messageJson.put("starting_cash", resultSet.getFloat("starting_cash"));
                    String message = messageJson.toString();
                    sendResponse(exchange, 200, message);
                } else {
                    throw new SQLException("Group not found");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "{\"error\": \"Get group failed\"}");
        }
    }

    private void handleCreateGroup(HttpExchange exchange, String username) throws IOException {
        try {
            InputStream inputStream = exchange.getRequestBody();
            String requestBody = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

            JSONObject jsonObject = new JSONObject(requestBody);
            String group_name = jsonObject.getString("group_name");

            DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
            OffsetDateTime startDateTime = OffsetDateTime.parse(jsonObject.getString("start_date"), formatter);
            OffsetDateTime endDateTime = OffsetDateTime.parse(jsonObject.getString("end_date"), formatter);
            Timestamp start_date = Timestamp.valueOf(startDateTime.toLocalDateTime());
            Timestamp end_date = Timestamp.valueOf(endDateTime.toLocalDateTime());

            boolean is_active = start_date.before(new Timestamp(System.currentTimeMillis()));
            float starting_cash = (float) jsonObject.getDouble("starting_cash");
            try {
                // Begin transaction
                dbConnection.setAutoCommit(false);
                String insertGroup = "INSERT INTO groups (group_name, start_date, end_date, is_active, starting_cash) VALUES (?,?,?,?,?)";
                try (PreparedStatement preparedStatement = dbConnection.prepareStatement(insertGroup)) {
                    preparedStatement.setString(1, group_name);
                    preparedStatement.setTimestamp(2, start_date);
                    preparedStatement.setTimestamp(3, end_date);
                    preparedStatement.setBoolean(4, is_active);
                    preparedStatement.setFloat(5, starting_cash);
                    preparedStatement.executeUpdate();
                    System.out.println("GroupHandler: Inserted group successfully");
                }

                String insertAccount = "INSERT INTO accounts (username, group_name, group_role_id) VALUES (?,?,?)";
                int group_creater = 1;
                try (PreparedStatement preparedStatement = dbConnection.prepareStatement(insertAccount)) {
                    preparedStatement.setString(1, username);
                    preparedStatement.setString(2, group_name);
                    preparedStatement.setInt(3, group_creater);
                    preparedStatement.executeUpdate();
                    System.out.println("GroupHandler: Inserted creator account successfully");
                }
                dbConnection.commit();
                sendResponse(exchange, 200, "Group formed successfully");
            } catch (SQLException e) {
                // Rollback transaction if an exception occurs
                try {
                    sendResponse(exchange, 500, "{\"error\": \"Create group failed\"}");
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
            sendResponse(exchange, 500, "{\"error\": \"Create group failed\"}");
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

    private void handleDeleteGroup(HttpExchange exchange, String group_name, String username) throws IOException, SQLException {
        if (has_been_deleted(group_name)) {
            sendResponse(exchange, 410, "{\"error\": \"Group has already been deleted\"}");
            return;
        }
        if (roleInGroup(username, group_name) > 1) {
            sendResponse(exchange, 403, "{\"error\": \"Only group creator can delete groups\"}");
            return;
        }

        String delete = "UPDATE groups SET has_been_deleted = ? WHERE group_name = ?";
        try (PreparedStatement statement = dbConnection.prepareStatement(delete)) {
            statement.setBoolean(1, true);
            statement.setString(2, group_name);
            int rowsAffected = statement.executeUpdate();

            if (rowsAffected > 0) {
                String message = group_name + " successfully deleted.";
                sendResponse(exchange, 200, message);
            } else {
                sendResponse(exchange, 404, "{\"error\": \"Group not found\"}");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "{\"error\": \"Delete group failed\"}");
        }
    }

    private void handleUpdateGroup(HttpExchange exchange, String group_name, String username) throws IOException, SQLException {
        try {
            if (has_been_deleted(group_name)) {
                sendResponse(exchange, 410, "{\"error\": \"Group has already been deleted\"}");
                return;
            }
            // user needs to be creator/admin
            if (roleInGroup(username, group_name) > 2) {
                sendResponse(exchange, 403, "{\"error\": \"Only group creator/admin can update group\"}");
                return;
            }

            InputStream inputStream = exchange.getRequestBody();
            String requestBody = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            JSONObject jsonObject = new JSONObject(requestBody);

            DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
            OffsetDateTime updatedStartDateTime = OffsetDateTime.parse(jsonObject.getString("start_date"), formatter);
            OffsetDateTime updatedEndDateTime = OffsetDateTime.parse(jsonObject.getString("end_date"), formatter);
            Timestamp updated_start_date = Timestamp.valueOf(updatedStartDateTime.toLocalDateTime());
            Timestamp updated_end_date = Timestamp.valueOf(updatedEndDateTime.toLocalDateTime());

            boolean updated_is_active = updated_start_date.before(new Timestamp(System.currentTimeMillis()));
            float updated_starting_cash = (float) jsonObject.getDouble("starting_cash");
            boolean isActive;
            Timestamp old_start_date, old_end_date;
            System.out.println("GroupHandler: Executing initial query for group update");
            String query = "SELECT * FROM groups WHERE group_name = ?";
            try (PreparedStatement statement = dbConnection.prepareStatement(query)) {
                statement.setString(1, group_name);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        isActive =  resultSet.getBoolean("is_active");
                        old_start_date = resultSet.getTimestamp("start_date");
                        old_end_date = resultSet.getTimestamp("end_date");
                    } else {
                        throw new SQLException("Group not found");
                    }
                }
            }
            System.out.println("GroupHandler: Executing update for group update");
            Date current_time = new Date(System.currentTimeMillis());
            if (isActive) {
                if (updated_end_date.before(current_time)) {
                    sendResponse(exchange, 400, "{\"error\": \"Group is already active. End date needs to set after current time.\"}");
                    return;
                }
                String update = "UPDATE groups SET end_date = ? WHERE group_name = ?";
                try (PreparedStatement statement = dbConnection.prepareStatement(update)) {
                    statement.setTimestamp(1, updated_end_date);
                    statement.setString(2, group_name);
                    int rowsAffected = statement.executeUpdate();

                    if (rowsAffected > 0) {
                        String message = group_name + " was successfully updated. Since the group is active, changed only end_date.";
                        sendResponse(exchange, 200, message);
                        return;
                    } else {
                        sendResponse(exchange, 409, "{\"error\": \"New group name already taken by another group.\"}");
                        return;
                    }
                }
            } else if (!isActive && !old_end_date.before(current_time)) {
                String update = "UPDATE groups SET start_date = ?, end_date = ?, starting_cash = ?, is_active = ? WHERE group_name = ?";
                try (PreparedStatement statement = dbConnection.prepareStatement(update)) {
                    statement.setTimestamp(1, updated_start_date);
                    statement.setTimestamp(2, updated_end_date);
                    statement.setFloat(3, updated_starting_cash);
                    statement.setBoolean(4, updated_is_active);
                    statement.setString(5, group_name);
                    int rowsAffected = statement.executeUpdate();

                    if (rowsAffected == 0) {
                        sendResponse(exchange, 500, "{\"error\": \"Could not update group.\"}");
                        return;
                    }

                    String updateAccounts = "UPDATE accounts SET current_cash = ? WHERE group_name = ?";
                    try (PreparedStatement accountStatement = dbConnection.prepareStatement(updateAccounts)) {
                        accountStatement.setFloat(1, updated_starting_cash);
                        accountStatement.setString(2, group_name);
                        int rowsAffectedAcc = accountStatement.executeUpdate();
                        if (rowsAffectedAcc == 0) {
                            sendResponse(exchange, 500, "{\"error\": \"Could not update group.\"}");
                            return;
                        } else {
                            String message = group_name + " was successfully updated.";
                            sendResponse(exchange, 200, message);
                        }
                    }
                }
            } else {
                sendResponse(exchange, 400, "{\"error\": \"Group is no longer active\"}");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "{\"error\": \"Update group failed\"}");
        }
    }

    private void handleJoinGroup(HttpExchange exchange, String group_name, String username) throws IOException, SQLException {
        try {
            if (has_been_deleted(group_name)) {
                sendResponse(exchange, 410, "{\"error\": \"Group has already been deleted\"}");
                return;
            }
            roleInGroup(username, group_name);
            // If no exception is thrown, the user is already in the group
            sendResponse(exchange, 403, "{\"error\": \"User already in group\"}");
        } catch (SQLException e) {
            String insertSQL = "INSERT INTO accounts (username, group_name) VALUES (?, ?)";
            try (PreparedStatement preparedStatement = dbConnection.prepareStatement(insertSQL)) {
                preparedStatement.setString(1, username);
                preparedStatement.setString(2, group_name);
                preparedStatement.executeUpdate();
            }
            System.out.println("GroupHandler: user joined group");
            sendResponse(exchange, 200, "User joined group successfully");
        }

    }

    private void handleLeaveGroup(HttpExchange exchange, String group_name, String username) throws IOException, SQLException {
        try {
            if (has_been_deleted(group_name)) {
                sendResponse(exchange, 410, "{\"error\": \"Group has already been deleted\"}");
                return;
            }
            int role = roleInGroup(username, group_name);
            if (role == 1) {
                sendResponse(exchange, 403, "{\"error\": \"Group creator cannot leave group. Try deleting instead.\"}");
                return;
            }

            String delete = "DELETE FROM accounts WHERE username = ? AND group_name = ?";
            try (PreparedStatement statement = dbConnection.prepareStatement(delete)) {
                statement.setString(1, username);
                statement.setString(2, group_name);
                int rowsAffected = statement.executeUpdate();

                if (rowsAffected > 0) {
                    String message = username + " was successfully removed from " + group_name + ".";
                    sendResponse(exchange, 200, message);
                } else {
                    sendResponse(exchange, 400, "{\"error\": \"User not in group.\"}"); // this should never be returned
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "{\"error\": \"Leave group failed.\"}");
        }
    }

    private void handleGetUserList(HttpExchange exchange, String group_name, String clientUsername, int page) throws IOException, SQLException {
        try {
            if (has_been_deleted(group_name)) {
                sendResponse(exchange, 410, "{\"error\": \"Group has already been deleted\"}");
                return;
            }
            JSONArray jsonArray = new JSONArray();
            String betQuery  = "SELECT * FROM accounts WHERE group_name = ? ORDER BY current_cash DESC LIMIT 50 OFFSET ? * 50";
            try (PreparedStatement betStatement = dbConnection.prepareStatement(betQuery)) {
                betStatement.setString(1, group_name);
                betStatement.setInt(2, page);
                try (ResultSet resultSet = betStatement.executeQuery()) {
                    while (resultSet.next()) {
                        JSONObject messageJson = new JSONObject();
                        messageJson.put("username", resultSet.getString("username"));
                        messageJson.put("current_cash", resultSet.getFloat("current_cash"));
                        String role = "Group Member";
                        // this is probably faster than waiting on a SQL read + client doesnt have to transform string
                        if (resultSet.getInt("group_role_id") == 1) {
                            role = "Group Creator";
                        } else if (resultSet.getInt("group_role_id") == 2) {
                            role = "Group Administrator";
                        }
                        messageJson.put("group_role", role);
                        jsonArray.put(messageJson);
                    }
                }
            }
            String response = jsonArray.toString();
            sendResponse(exchange, 200, response);

        } catch (SQLException e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "{\"error\": \"Get user list failed.\"}");
        }
    }

    private void handleToggleAdmin(HttpExchange exchange, String group_name, String username) throws IOException, SQLException {
        String targetUsername = "";
        int inputRole = 2;
        try {
            if (has_been_deleted(group_name)) {
                sendResponse(exchange, 410, "{\"error\": \"Group has already been deleted\"}");
                return;
            }
            int role = roleInGroup(username, group_name);
            if (role > 1) {
                sendResponse(exchange, 403, "{\"error\": \"Only group creators promote user to admin.\"}");
                return;
            }

            // Reading body to get targetUsername
            InputStream inputStream = exchange.getRequestBody();
            String requestBody = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            JSONObject jsonObject = new JSONObject(requestBody);
            targetUsername = jsonObject.getString("admin");
            inputRole = jsonObject.getInt("role");
            System.out.println("GroupHandler: Changing " + targetUsername + " to " + inputRole);

            // Checking current role of target user for promotion
            int targetRole = roleInGroup(targetUsername, group_name);
            if (targetRole == 1) {
                sendResponse(exchange, 400, "{\"error\": \"Cannot demote group creator to admin.\"}");
                return;
            }

            int adminRole = (inputRole == 2) ? 2 : 3;
            String update = "UPDATE accounts SET group_role_id = ? WHERE username = ? AND group_name = ?";
            try (PreparedStatement statement = dbConnection.prepareStatement(update)) {
                statement.setInt(1, adminRole);
                statement.setString(2, targetUsername);
                statement.setString(3, group_name);
                int rowsAffected = statement.executeUpdate();

                if (rowsAffected > 0) {
                    String message = targetUsername + " was successfully promoted to admin in " + group_name + ".";
                    sendResponse(exchange, 200, message);
                } else {
                    sendResponse(exchange, 400, "{\"error\": \"Target user not in group.\"}");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "{\"error\": \"Promote user to admin failed.\"}");
        }
    }

    private void handleGetBetCash(HttpExchange exchange, String group_name, String username, String targetUsername) throws IOException, SQLException {
        try {
            if (has_been_deleted(group_name)) {
                sendResponse(exchange, 410, "{\"error\": \"Group has already been deleted\"}");
                return;
            }
            //if (roleInGroup(username, group_name) > 3) {
            //    sendResponse(exchange, 403, "{\"error\": \"Only group members can acccess user information.\"}");
            //    return;
            //}

            JSONArray jsonArray = new JSONArray();
            String accountQuery = "SELECT * FROM accounts WHERE username = ? AND group_name = ?";
            try (PreparedStatement accountStatement = dbConnection.prepareStatement(accountQuery)) {
                accountStatement.setString(1, targetUsername);
                accountStatement.setString(2, group_name);
                try (ResultSet resultSet = accountStatement.executeQuery()) {
                    if (resultSet.next()) {
                        JSONObject accountJson = new JSONObject();
                        accountJson.put("username", resultSet.getString("username"));
                        accountJson.put("group_name", resultSet.getString("group_name"));

                        String role = "Group Member";
                        // this is probably faster than waiting on a SQL read + client doesnt have to transform string
                        if (resultSet.getInt("group_role_id") == 1) {
                            role = "Group Creator";
                        } else if (resultSet.getInt("group_role_id") == 2) {
                            role = "Group Administrator";
                        }
                        accountJson.put("group_role", role);
                        //accountJson.put("group_role_id", resultSet.getInt("group_role_id"));
                        accountJson.put("current_cash", resultSet.getFloat("current_cash"));
                        jsonArray.put(accountJson);
                    } else {
                        sendResponse(exchange, 404, "{\"error\": \"Target user not in group.\"}");
                        return;
                    }
                }
            }

            String betQuery = "SELECT * FROM bets WHERE username = ? AND group_name = ?";
            try (PreparedStatement betStatement = dbConnection.prepareStatement(betQuery)) {
                betStatement.setString(1, targetUsername);
                betStatement.setString(2, group_name);
                try (ResultSet resultSet = betStatement.executeQuery()) {
                    while (resultSet.next()) {
                        JSONObject messageJson = new JSONObject();
                        messageJson.put("type", resultSet.getString("type"));
                        messageJson.put("wagered", resultSet.getFloat("wagered"));
                        messageJson.put("amount_to_win", resultSet.getFloat("amount_to_win"));
                        messageJson.put("picked_winner", resultSet.getString("picked_winner"));
                        messageJson.put("time_placed", resultSet.getTimestamp("time_placed"));
                        messageJson.put("been_distributed", resultSet.getBoolean("been_distributed"));
                        messageJson.put("is_parlay", resultSet.getBoolean("is_parlay"));

                        String gameQuery = "SELECT * FROM games WHERE game_id = ?";
                        try (PreparedStatement gameStatement = dbConnection.prepareStatement(gameQuery)) {
                            gameStatement.setInt(1, resultSet.getInt("game_id"));
                            ResultSet gameResultSet = gameStatement.executeQuery();
                            if (gameResultSet.next()) {
                                messageJson.put("team1", gameResultSet.getString("team1"));
                                messageJson.put("odds1", gameResultSet.getFloat("odds1"));
                                messageJson.put("line1", gameResultSet.getFloat("line1"));
                                messageJson.put("score1", gameResultSet.getInt("score1"));
                                messageJson.put("team2", gameResultSet.getString("team2"));
                                messageJson.put("odds2", gameResultSet.getFloat("odds2"));
                                messageJson.put("line2", gameResultSet.getFloat("line2"));
                                messageJson.put("score2", gameResultSet.getInt("score2"));
                                messageJson.put("last_update", gameResultSet.getTimestamp("last_update"));
                                messageJson.put("game_start_time", gameResultSet.getTimestamp("game_start_time"));
                                messageJson.put("status", gameResultSet.getString("status"));
                                messageJson.put("winner", gameResultSet.getString("winner"));
                                messageJson.put("league", gameResultSet.getString("league"));
                            }
                        }
                        jsonArray.put(messageJson);
                    }
                }
            } catch (SQLException e) {
                System.out.println("GroupHandler: Failed loading bets for user " + targetUsername + " in group " + group_name + ".");
                sendResponse(exchange, 500, "{\"error\": \"Failed to get bets for user in group.\"}");
                return;
            }

            String message = jsonArray.toString();
            sendResponse(exchange, 200, message);

        } catch (SQLException e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "{\"error\": \"Get bets/cash for target user failed.\"}");
        }
    }
}

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

public class GroupHandler implements HttpHandler {

    private Connection dbConnection;
    //private String clientUsername;

    private Pattern createGroupPattern = Pattern.compile("/group"); // used to create group w/o groupname
    private Pattern rudGroupPattern = Pattern.compile("/group/([^/]+)/"); // used for update, get, delete group
    private Pattern joinGroupPattern = Pattern.compile("/group/([^/]+)/join"); // group/group_name/join
    private Pattern leaveGroupPattern = Pattern.compile("/group/([^/]+)/leave"); // group/group_name/leave
    private Pattern getUserListPattern = Pattern.compile("/group/([^/]+)/users"); // group/group_name/users?page=n
    private Pattern makeAdminPattern = Pattern.compile("/group/([^/]+)/admin/([^/]+)"); // group/group_name/admin/username
    private Pattern getUserBetCashPattern = Pattern.compile("/group/([^/]+)/user/([^/]+)"); // group/group_name/user/username

    public GroupHandler(Connection dbConnection) {
        //this.clientUsername = clientUsername;
        this.dbConnection = dbConnection;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String clientUsername = ""; // = "bryans"; //  (String) exchange.getAttribute("username");
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
                    } else if (getUserListMatcher.matches()) {
                        group_name = getUserListMatcher.group(1);
                        // query for page
                        String query = requestUri.getQuery();
                        Map<String, String> queryParams = parseQueryParams(query);
                        String pageStr = queryParams.getOrDefault("page", "0");
                        page = Integer.parseInt(pageStr);
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
                    } else if (leaveGroupMatcher.matches()) {
                        group_name = leaveGroupMatcher.group(1);
                        handleLeaveGroup(exchange, group_name, clientUsername);
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
        } catch (SQLException e) {
            e.printStackTrace();
            exchange.sendResponseHeaders(500, -1);
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
        // check if group has been deleted
        if (has_been_deleted(group_name)) {
            exchange.sendResponseHeaders(410, -1);
            return;
        }
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
                sendResponse(exchange, 200, response);
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

    private void handleDeleteGroup(HttpExchange exchange, String group_name, String username) throws IOException, SQLException { //throws IOException {
        // change this to update is_deleted in groups
        if (roleInGroup(username, group_name) > 1) {
            exchange.sendResponseHeaders(403, -1); // Forbidden
            return;
        }
        //System.out.println("Group name: " + group_name);
        String delete = "UPDATE groups SET has_been_deleted = ? WHERE group_name = ?";
        // "DELETE FROM groups WHERE group_name = ?";
        try (PreparedStatement statement = dbConnection.prepareStatement(delete)) {
            statement.setBoolean(1, true);
            statement.setString(2, group_name);
            int rowsAffected = statement.executeUpdate();

            if (rowsAffected > 0) {
                String message = group_name + " successfully deleted.";
                sendResponse(exchange, 200, message);
            } else {
                String message = "Group not found.";
                sendResponse(exchange, 404, message);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            String response = "Delete group failed.";
            sendResponse(exchange, 500, response);
        }
    }

    private void handleUpdateGroup(HttpExchange exchange, String group_name, String username) throws IOException, SQLException { //throws IOException {
        // check if group is deleted ^^
        // need to be group creator or group admin
        try {
            if (has_been_deleted(group_name)) {
                exchange.sendResponseHeaders(410, -1);
                return;
            }
            if (roleInGroup(username, group_name) > 2) {
                String response = "Need to be group creator or admin.";
                sendResponse(exchange, 403, response);
                return;
            }
            InputStream inputStream = exchange.getRequestBody();
            String requestBody = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            System.out.println("Received JSON payload: " + requestBody);

            JSONObject jsonObject = new JSONObject(requestBody);
            String updated_group_name = jsonObject.getString("group_name");
            Date updated_start_date = Date.valueOf(jsonObject.getString("start_date"));
            Date updated_end_date = Date.valueOf(jsonObject.getString("end_date"));
            boolean updated_is_active = updated_start_date.before(new Date(System.currentTimeMillis()));
            float updated_starting_cash = (float) jsonObject.getDouble("starting_cash");
            boolean isActive;
            Date old_start_date;
            // pull data from db. if is active, can only change the group name, end date. if not active, can change everything.
            String query = "SELECT * FROM groups WHERE group_name = ?";
            try (PreparedStatement statement = dbConnection.prepareStatement(query)) {
                statement.setString(1, group_name);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        isActive =  resultSet.getBoolean("is_active");
                        old_start_date = Date.valueOf(resultSet.getString("start_date"));
                    } else {
                        throw new SQLException("Group not found");
                    }
                }
            }

            if (isActive) {
                if (old_start_date.before(updated_end_date)) {
                    String message = "Group is already active, so cannot change the start date. End date needs to be after start date.";
                    sendResponse(exchange, 400, message);
                    return;
                }
                String update = "UPDATE groups SET end_date = ? AND group_name = ? WHERE username = ? AND group_name = ?";
                try (PreparedStatement statement = dbConnection.prepareStatement(update)) {
                    statement.setDate(1, updated_end_date);
                    statement.setString(2, updated_group_name);
                    statement.setString(3, username);
                    statement.setString(4, group_name);
                    int rowsAffected = statement.executeUpdate();

                    if (rowsAffected > 0) {
                        String message = updated_group_name + " was successfully updated. Since the group is active, only end_date & name can be changed.";
                        sendResponse(exchange, 200, message);
                    } else {
                        String message = "New group name already taken by another group.";
                        sendResponse(exchange, 404, message);
                    }
                }
            } else {
                String update = "UPDATE groups SET start_date AND end_date = ? AND group_name = ? AND starting_cash = ? AND is_active = ? WHERE username = ? AND group_name = ?";
                try (PreparedStatement statement = dbConnection.prepareStatement(update)) {
                    statement.setDate(1, updated_start_date);
                    statement.setDate(2, updated_end_date);
                    statement.setString(3, updated_group_name);
                    statement.setFloat(4, updated_starting_cash);
                    statement.setString(5, updated_group_name);
                    statement.setBoolean(6, updated_is_active);
                    statement.setString(7, username);
                    statement.setString(8, group_name);
                    int rowsAffected = statement.executeUpdate();

                    if (rowsAffected > 0) {
                        String message = updated_group_name + " was successfully updated.";
                        sendResponse(exchange, 200, message);
                    } else {
                        String message = "Error updating group.";
                        sendResponse(exchange, 404, message);
                    }
                }
            }


        } catch (SQLException e) {
            e.printStackTrace();
            String response = "Update group failed.";
        }
    }

    private void handleJoinGroup(HttpExchange exchange, String group_name, String username) throws IOException, SQLException { //throws IOException {
        // check if group is deleted ^^ and if group is_active
        try {
            if (has_been_deleted(group_name)) {
                exchange.sendResponseHeaders(410, -1);
                return;
            }
            roleInGroup(username, group_name);
            // If no exception is thrown, the user is already in the group
            String response = "Join group failed. Already in group.";
            sendResponse(exchange, 500, response);
        } catch (SQLException e) {
            String insertSQL = "INSERT INTO accounts (username, group_name) VALUES (?, ?)";
            try (PreparedStatement preparedStatement = dbConnection.prepareStatement(insertSQL)) {
                preparedStatement.setString(1, username);
                preparedStatement.setString(2, group_name);
                preparedStatement.executeUpdate();
            }

            System.out.println("User: " + username + " created account in group " + group_name +".");
            // Send a response
            String response = "User added to group successfully";
            sendResponse(exchange, 200, response);
        }

    }

    private void handleLeaveGroup(HttpExchange exchange, String group_name, String username) throws IOException, SQLException { //throws IOException {
        try {
            if (has_been_deleted(group_name)) {
                exchange.sendResponseHeaders(410, -1);
                return;
            }
            int role = roleInGroup(username, group_name);
            if (role == 1) {
                String response = "Group creator cannot leave group. Try deleting instead.";
                sendResponse(exchange, 500, response);
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
                    String message = "User not in group.";
                    sendResponse(exchange, 404, message);
                }
            } catch (SQLException e) {
                e.printStackTrace();
                String response = "Delete group failed.";
                sendResponse(exchange, 500, response);
            }


        } catch (SQLException e) {
            e.printStackTrace();
            String response = "Leave group failed. Not in group.";
            sendResponse(exchange, 500, response);
        }
    }

    private void handleGetUserList(HttpExchange exchange, String group_name, String clientUsername, int page) throws IOException, SQLException { //throws IOException {
        // anybody can access user list. doesnt need to be in group
        // check if group is deleted ^^
        try {
            if (has_been_deleted(group_name)) {
                exchange.sendResponseHeaders(410, -1);
                return;
            }
            JSONArray jsonArray = new JSONArray();
            String betQuery  = "SELECT * FROM groups WHERE group_name = ? ORDER BY group_name LIMIT 50 OFFSET ? * 50";
            try (PreparedStatement betStatement = dbConnection.prepareStatement(betQuery)) {
                betStatement.setString(1, group_name);
                betStatement.setInt(2, page);
                try (ResultSet resultSet = betStatement.executeQuery()) {
                    while (resultSet.next()) {
                        // Process the results
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
            String response = "Get user list failed.";
            sendResponse(exchange, 500, response);
        }
    }

    private void handleMakeAdmin(HttpExchange exchange, String group_name, String username, String targetUsername) throws IOException, SQLException { //throws IOException {
        // check if group is deleted
        try {
            if (has_been_deleted(group_name)) {
                exchange.sendResponseHeaders(410, -1);
                return;
            }
            int role = roleInGroup(username, group_name);
            if (role > 1) {
                String response = "Only group creators promote user to admin.";
                sendResponse(exchange, 403, response);
                return;
            }

            int targetRole = roleInGroup(targetUsername, group_name);
            if (targetRole == 1) {
                String response = "Cannot demote group creator to admin.";
                sendResponse(exchange, 403, response);
                return;
            } else if (targetRole == 2) {
                String response = "Target user already admin.";
                sendResponse(exchange, 409, response);
                return;
            }
            int adminRole = 2;
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
                    String message = "Target user not in group.";
                    sendResponse(exchange, 404, message);
                }
            } catch (SQLException e) {
                e.printStackTrace();
                String response = "Update "+targetUsername + " with admin role failed.";
                sendResponse(exchange, 500, response);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            String response = "Make " + targetUsername + " admin failed. Not in group";
            sendResponse(exchange, 500, response);
        }
    }

    private void handleGetBetCash(HttpExchange exchange, String group_name, String username, String targetUsername) throws IOException, SQLException {
        // check if group is deleted
        try {
            if (has_been_deleted(group_name)) {
                exchange.sendResponseHeaders(410, -1);
                return;
            }
            if (roleInGroup(username, group_name) > 3) {
                String response = "Only group members can access user info.";
                sendResponse(exchange, 403, response);
                return;
            }

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
                        accountJson.put("group_role_id", resultSet.getInt("group_role_id"));
                        accountJson.put("current_cash", resultSet.getFloat("current_cash"));
                        jsonArray.put(accountJson);
                    } else {
                        String response = "Target user not found in group.";
                        sendResponse(exchange, 404, response); // 404 Not Found
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
                        // Process the results
                        JSONObject messageJson = new JSONObject();
                        messageJson.put("type", resultSet.getString("type"));
                        messageJson.put("wagered", resultSet.getFloat("wagered"));
                        messageJson.put("amount_to_win", resultSet.getFloat("amount_to_win"));
                        messageJson.put("picked_winner", resultSet.getString("picked_winner"));
                        messageJson.put("time_placed", resultSet.getDate("time_placed"));
                        messageJson.put("been_distributed", resultSet.getBoolean("current_cash"));
                        messageJson.put("is_parlay", resultSet.getBoolean("is_parlay"));
                        // get game info and append
                        String gameQuery = "SELECT * FROM games WHERE game_id = ?";
                        try (PreparedStatement gameStatement = dbConnection.prepareStatement(gameQuery)) {
                            gameStatement.setInt(1, resultSet.getInt("game_id"));
                            ResultSet gameResultSet = gameStatement.executeQuery();
                            if (gameResultSet.next()) {
                                messageJson.put("team1", gameResultSet.getString("team1"));
                                messageJson.put("odds1", gameResultSet.getFloat("odds1"));
                                messageJson.put("line1", gameResultSet.getFloat("line1"));
                                messageJson.put("team2", gameResultSet.getString("team2"));
                                messageJson.put("odds2", gameResultSet.getFloat("odds2"));
                                messageJson.put("line2", gameResultSet.getFloat("line2"));
                                messageJson.put("last_update", gameResultSet.getDate("last_update"));
                                messageJson.put("game_start_time", gameResultSet.getDate("game_start_time"));
                                messageJson.put("status", gameResultSet.getString("status"));
                                messageJson.put("winner", gameResultSet.getString("winner"));
                                messageJson.put("league", gameResultSet.getString("league"));
                            } else {
                                throw new SQLException("Game not found for bet");
                            }
                        }
                        jsonArray.put(messageJson);

                    }
                    // convert array to String and

                    //String response = jsonArray.toString();
                    //sendResponse(exchange, 200, response);
                }
            } catch (SQLException e) {
                System.out.println("No bets found for user " + targetUsername + " in group " + group_name + ".");
                // Handle the exception
            }

            String message = jsonArray.toString();
            sendResponse(exchange, 200, message);
                //System.out.println("Group name: " + group_name);

        } catch (SQLException e) {
            e.printStackTrace();
            String response = "Get target user bet and cash failed.";
            sendResponse(exchange, 500, response);
        }
    }
}

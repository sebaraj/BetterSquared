package com.betservice.server;

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

public class BetHandler implements HttpHandler {

    private Connection dbConnection;
    //private String clientUsername;


    private Pattern getLeaguesPattern = Pattern.compile("/bet/([^/]+)"); // GET /bet/{group_name}
    private Pattern getGamesByLeaguePattern = Pattern.compile("/bet/([^/]+)/view/([^/]+)"); // GET /bet/{group_name}/view/{league_name}
    private Pattern getGamesByIDPattern = Pattern.compile("/bet/([^/]+)/view/([^/]+)/([^/]+)"); // GET /bet/{group_name}/view/{league_name}/{game_id}
    private Pattern buyBetPattern = Pattern.compile("/bet/([^/]+)/buy"); // POST /bet/{group_name}/buy
    private Pattern sellBetPattern = Pattern.compile("/bet/([^/]+)/sell"); // DELETE /bet/{group_name}/sell
    private Pattern getActiveBetsPattern = Pattern.compile("/bet/([^/]+)/active"); // GET /bet/{group_name}/active
    private Pattern getSettledBetsPattern = Pattern.compile("/bet/([^/]+)/settled"); // GET /bet/{group_name}/settled


    public BetHandler(Connection dbConnection) {
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
        String group_name, league_name;
        int page = 0, game_id;
        URI requestUri = exchange.getRequestURI();
        String path = requestUri.getPath();
        System.out.println(path);
        Matcher createGroupMatcher = createGroupPattern.matcher(path);

        try {
            switch (exchange.getRequestMethod()) {
                case "GET":
                    if (rudGroupMatcher.matches()) {
                        //System.out.println("matched");
                        group_name = rudGroupMatcher.group(1);
                        handleGetGroup(exchange, group_name, clientUsername);
                    } else {
                        System.out.println("no match. reached the end of get switch");
                        exchange.sendResponseHeaders(405, -1);
                    }
                    break;
                case "POST":
                    if (createGroupMatcher.matches()) {
                        handleCreateGroup(exchange, clientUsername);
                    } else {
                        exchange.sendResponseHeaders(405, -1); // Method Not Allowed
                    }
                    break;
                case "DELETE":
                    if (rudGroupMatcher.matches()) {
                        group_name = rudGroupMatcher.group(1);
                        handleDeleteGroup(exchange, group_name, clientUsername);
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
        //String query = "SELECT * FROM groups WHERE group_name = ?";
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

}

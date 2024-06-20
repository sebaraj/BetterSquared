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
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.TimeZone;

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
        String group_name, league_name, game_id_str;
        int page = 0, game_id;
        URI requestUri = exchange.getRequestURI();
        String path = requestUri.getPath();
        System.out.println(path);
        Matcher getLeaguesMatcher = getLeaguesPattern.matcher(path);
        Matcher getGamesByLeagueMatcher = getGamesByLeaguePattern.matcher(path);
        Matcher getGamesByIDMatcher = getGamesByIDPattern.matcher(path);
        Matcher buyBetMatcher = buyBetPattern.matcher(path);
        Matcher sellBetMatcher = sellBetPattern.matcher(path);
        Matcher getActiveBetsMatcher = getActiveBetsPattern.matcher(path);
        Matcher getSettledBetsMatcher = getSettledBetsPattern.matcher(path);
        String query = requestUri.getQuery();
        Map<String, String> queryParams = parseQueryParams(query);
        String pageStr = queryParams.getOrDefault("page", "-1");
        page = Integer.parseInt(pageStr);

        try {
            switch (exchange.getRequestMethod()) {
                case "GET":
                    if (getLeaguesMatcher.matches()) {
                        //System.out.println("matched");
                        group_name = getLeaguesMatcher.group(1);
                        handleGetLeagues(exchange, group_name, page);
                    } else if (getGamesByLeagueMatcher.matches()) {
                        group_name = getGamesByLeagueMatcher.group(1);
                        league_name = getGamesByLeagueMatcher.group(2);
                        handleGetGamesByLeague(exchange, group_name, league_name, page);
                    } else if (getGamesByIDMatcher.matcher()) {
                        group_name = getGamesByIDMatcher.group(1);
                        league_name = getGamesByIDMatcher.group(2);
                        game_id_str = getGamesByIDMatcher.group(3);
                        game_id = Integer.parseInt(game_id_str);
                        handleGetGamesByID(exchange, group_name, league_name, game_id);
                    } else if (getActiveBetsMatcher.matches()) {
                        group_name = getActiveBetsMatcher.group(1);
                        handleGetBetsByStatus(exchange, group_name, clientUsername, page, false);
                    } else if (getSettledBetsMatcher.matches()) {
                        group_name = getSettledBetsMatcher.group(1);
                        handleGetBetsByStatus(exchange, group_name, clientUsername, page, true);
                    } else {
                        System.out.println("no match. reached the end of get switch");
                        exchange.sendResponseHeaders(405, -1);
                    }
                    break;
                case "POST":
                    if (buyBetMatcher.matches()) {
                        group_name = buyBetMatcher.group(1);
                        handleBuyBet(exchange, group_name);
                    } else {
                        exchange.sendResponseHeaders(405, -1); // Method Not Allowed
                    }
                    break;
                case "DELETE":
                    if (sellBetMatcher.matches()) {
                        group_name = sellBetMatcher.group(1);
                        handleSellBet(exchange, group_name, page);
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

    private void handleGetLeagues(HttpExchange exchange, String group_name, int page) throws IOException, SQLException {
        // check if group has been deleted
        if (has_been_deleted(group_name)) {
            exchange.sendResponseHeaders(410, -1);
            return;
        }
        System.out.println("Group name: " + group_name);
        JSONArray jsonArray = new JSONArray();
        String query = "SELECT * FROM leagues LIMIT 50 OFFSET ? * 50";
        try (PreparedStatement statement = dbConnection.prepareStatement(query)) {
            betStatement.setInt(1, page);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    JSONObject messageJson = new JSONObject();
                    messageJson.put("name", resultSet.getString("name"));
                    messageJson.put("subleague_of", resultSet.getString("subleague_of"));
                    jsonArray.put(messageJson);
                }
                String response = jsonArray.toString();
                sendResponse(exchange, 200, response);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            String response = "Get league failed.";
            sendResponse(exchange, 500, response);
        }
    }

    private void handleGetGamesByLeague(HttpExchange exchange, String group_name, String league_name, int page) throws IOException, SQLException {
        // check if group has been deleted
        if (has_been_deleted(group_name)) {
            exchange.sendResponseHeaders(410, -1);
            return;
        }

        // Set the start time to 12:01 AM of the current day
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 1);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Timestamp startTime = new Timestamp(calendar.getTimeInMillis());

        System.out.println("Group name: " + group_name+". League name: " + league_name);
        String query = "SELECT * FROM games WHERE league_name = ? AND game_start_time >= ? LIMIT 50 OFFSET ? * 50";
        JSONArray jsonArray = new JSONArray();
        try (PreparedStatement statement = dbConnection.prepareStatement(query)) {
            statement.setString(1, league_name);
            statement.setTimestamp(2, startTime);
            statement.setInt(3, page);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    JSONObject messageJson = new JSONObject();
                    messageJson.put("game_id", resultSet.getInt("game_id"));
                    messageJson.put("team1", resultSet.getString("team1"));
                    messageJson.put("odds1", resultSet.getFloat("odds1"));
                    messageJson.put("line1", resultSet.getFloat("line1"));
                    messageJson.put("team2", resultSet.getString("team2"));
                    messageJson.put("odds2", resultSet.getFloat("odds2"));
                    messageJson.put("line2", resultSet.getFloat("line2"));
                    messageJson.put("last_update", resultSet.getDate("last_update"));
                    messageJson.put("game_start_time", resultSet.getDate("game_start_time"));
                    messageJson.put("status", resultSet.getString("status"));
                    messageJson.put("winner", resultSet.getString("winner"));
                    messageJson.put("league", resultSet.getString("league"));
                    jsonArray.put(messageJson);
                }
                String response = jsonArray.toString();
                sendResponse(exchange, 200, response);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            String response = "Get games in league failed.";
            sendResponse(exchange, 500, response);
        }
    }

    private void handleGetGamesByID(HttpExchange exchange, String group_name, String league_name, int game_id) throws IOException, SQLException {
        // check if group has been deleted
        if (has_been_deleted(group_name)) {
            exchange.sendResponseHeaders(410, -1);
            return;
        }
        JSONObject messageJson = new JSONObject();
        System.out.println("Group name: " + group_name+". Game ID: " + game_id);
        String query = "SELECT * FROM games WHERE game_id = ?";
        try (PreparedStatement statement = dbConnection.prepareStatement(query)) {
            statement.setInt(1, game_id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    messageJson.put("game_id", resultSet.getInt("game_id"));
                    messageJson.put("team1", resultSet.getString("team1"));
                    messageJson.put("odds1", resultSet.getFloat("odds1"));
                    messageJson.put("line1", resultSet.getFloat("line1"));
                    messageJson.put("team2", resultSet.getString("team2"));
                    messageJson.put("odds2", resultSet.getFloat("odds2"));
                    messageJson.put("line2", resultSet.getFloat("line2"));
                    messageJson.put("last_update", resultSet.getDate("last_update"));
                    messageJson.put("game_start_time", resultSet.getDate("game_start_time"));
                    messageJson.put("status", resultSet.getString("status"));
                    messageJson.put("winner", resultSet.getString("winner"));
                    messageJson.put("league", resultSet.getString("league"));
                }
                String response = messageJson.toString();
                sendResponse(exchange, 200, response);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            String response = "Get games in league failed.";
            sendResponse(exchange, 500, response);
        }
    }

    private void handleGetBetsByStatus(HttpExchange exchange, String group_name, String username, int page, boolean been_distributed) throws IOException, SQLException {
        // check if group has been deleted
        try {
            if (has_been_deleted(group_name)) {
                exchange.sendResponseHeaders(410, -1);
                return;
            }
            roleInGroup(username, group_name);
            JSONArray jsonArray = new JSONArray();
            System.out.println("Group name: " + group_name + ". Username: " + username);
            String query = "SELECT * FROM bets WHERE group_name = ? AND username = ? AND been_distributed = ? LIMIT 50 OFFSET ? * 50";
            String gameQuery = "SELECT * FROM games WHERE game_id = ?";
            try (PreparedStatement statement = dbConnection.prepareStatement(query)) {
                statement.setString(1, group_name);
                statement.setString(2, username);
                statement.setBoolean(3, been_settled);
                statement.setInt(4, page);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        JSONObject messageJson = new JSONObject();
                        messageJson.put("bet_id", resultSet.getInt("bet_id"));
                        messageJson.put("type", resultSet.getString("type"));
                        messageJson.put("group_name", resultSet.getString("group_name"));
                        messageJson.put("username", resultSet.getString("username"));
                        messageJson.put("wagered", resultSet.getFloat("wagered"));
                        messageJson.put("amount_to_win", resultSet.getFloat("amount_to_win"));
                        messageJson.put("picked_winner", resultSet.getString("picked_winner"));
                        messageJson.put("time_placed", resultSet.getDate("time_placed"));
                        messageJson.put("been_distributed", resultSet.getBoolean("been_distributed"));
                        messageJson.put("is_parlay", resultSet.getBoolean("is_parlay"));
                        try (PreparedStatement gameStatement = dbConnection.prepareStatement(gameQuery)) {
                            gameStatement.setString(1, resultSet.getInt("game_id"));
                            try (ResultSet gameResultSet = gameStatement.executeQuery()) {
                                if (gameResultSet.next()) {
                                    messageJson.put("game_id", gameResultSet.getInt("game_id"));
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
                                    throw new SQLException("Group not found for bet");
                                }
                            }

                        }
                        jsonArray.put(messageJson);
                    }
                }
                String response = jsonArray.toString();
                sendResponse(exchange, 200, response);
        } catch (SQLException e) {
            e.printStackTrace();
            String response = "Get games in league failed.";
            sendResponse(exchange, 500, response);
        }
    }


}

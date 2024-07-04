/***********************************************************************************************************************
 *  File Name:       BetHandler.java
 *  Project:         Better2/betservice
 *  Author:          Bryan SebaRaj
 *  Description:     Handler for all HTTP traffic for bet service
 **********************************************************************************************************************/
package com.better2.betservice;

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
    private Pattern getLeaguesPattern = Pattern.compile("/bet/([^/]+)"); // GET /bet/{group_name}
    private Pattern getGamesByLeaguePattern = Pattern.compile("/bet/([^/]+)/view/([^/]+)"); // GET /bet/{group_name}/view/{league_name}
    private Pattern getGamesByIDPattern = Pattern.compile("/bet/([^/]+)/view/([^/]+)/([^/]+)"); // GET /bet/{group_name}/view/{league_name}/{game_id}
    private Pattern buyBetPattern = Pattern.compile("/bet/([^/]+)/buy"); // POST /bet/{group_name}/buy
    private Pattern sellBetPattern = Pattern.compile("/bet/([^/]+)/sell"); // PUT /bet/{group_name}/sell
    private Pattern getActiveBetsPattern = Pattern.compile("/bet/([^/]+)/active"); // GET /bet/{group_name}/active
    private Pattern getSettledBetsPattern = Pattern.compile("/bet/([^/]+)/settled"); // GET /bet/{group_name}/settled


    public BetHandler(Connection dbConnection) {
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
                        System.out.println("BetHandler: Handling GetLeagues.");
                        group_name = getLeaguesMatcher.group(1);
                        handleGetLeagues(exchange, group_name, page);
                    } else if (getGamesByLeagueMatcher.matches()) {
                        System.out.println("BetHandler: Handling GetGamesByLeague.");
                        group_name = getGamesByLeagueMatcher.group(1);
                        league_name = getGamesByLeagueMatcher.group(2);
                        handleGetGamesByLeague(exchange, group_name, league_name, page);
                    } else if (getGamesByIDMatcher.matches()) {
                        System.out.println("BetHandler: Handling GetGamesByID.");
                        group_name = getGamesByIDMatcher.group(1);
                        league_name = getGamesByIDMatcher.group(2);
                        game_id_str = getGamesByIDMatcher.group(3);
                        game_id = Integer.parseInt(game_id_str);
                        handleGetGamesByID(exchange, group_name, league_name, game_id);
                    } else if (getActiveBetsMatcher.matches()) {
                        System.out.println("BetHandler: Handling GetActiveBetsByStatus.");
                        group_name = getActiveBetsMatcher.group(1);
                        handleGetBetsByStatus(exchange, group_name, clientUsername, page, false);
                    } else if (getSettledBetsMatcher.matches()) {
                        System.out.println("BetHandler: Handling GetSettledBetsByStatus.");
                        group_name = getSettledBetsMatcher.group(1);
                        handleGetBetsByStatus(exchange, group_name, clientUsername, page, true);
                    } else {
                        System.out.println("BetHandler: Invalid GET request.");
                        sendResponse(exchange, 404, "{\"error\": \"BetHandler: endpoint does not exit\"}");
                    }
                    break;
                case "POST":
                    if (buyBetMatcher.matches()) {
                        System.out.println("BetHandler: Handling BuyBet.");
                        group_name = buyBetMatcher.group(1);
                        handleBuyBet(exchange, group_name, clientUsername);
                    } else {
                        System.out.println("BetHandler: Invalid POST request.");
                        sendResponse(exchange, 404, "{\"error\": \"BetHandler: endpoint does not exit\"}");
                    }
                    break;
                case "PUT":
                    if (sellBetMatcher.matches()) {
                        System.out.println("BetHandler: Handling SellBet.");
                        group_name = sellBetMatcher.group(1);
                        handleSellBet(exchange, group_name, clientUsername);
                    } else {
                        System.out.println("BetHandler: Invalid PUT request.");
                        sendResponse(exchange, 404, "{\"error\": \"BetHandler: endpoint does not exit\"}");
                    }
                    break;
                default:
                    sendResponse(exchange, 405, "{\"error\": \"BetHandler: method not allowed\"}");
                    break;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "{\"error\": \"Bet service request failed.\"}");
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

    private boolean isGroupActive(String group_name) throws SQLException {
        String query = "SELECT is_active FROM groups WHERE group_name = ?";
        try (PreparedStatement preparedStatement = dbConnection.prepareStatement(query)) {
            preparedStatement.setString(1, group_name);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getBoolean("is_active");
                } else {
                    throw new SQLException("No such group found in accounts table.");
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
        if (has_been_deleted(group_name)) {
            sendResponse(exchange, 410, "{\"error\": \"Group has been deleted\"}");
            return;
        }

        JSONArray jsonArray = new JSONArray();
        String query = "SELECT * FROM leagues LIMIT 50 OFFSET ? * 50";
        try (PreparedStatement statement = dbConnection.prepareStatement(query)) {
            statement.setInt(1, page);
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
            sendResponse(exchange, 500, "{\"error\": \"Get leagues failed\"}");
        }
    }

    private void handleGetGamesByLeague(HttpExchange exchange, String group_name, String league_name, int page) throws IOException, SQLException {
        if (has_been_deleted(group_name)) {
            sendResponse(exchange, 410, "{\"error\": \"Group has been deleted\"}");
            return;
        }

        // Set the start time to 12:01 AM of the current day
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 1);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Timestamp startTime = new Timestamp(calendar.getTimeInMillis());

        String query = "SELECT * FROM games WHERE league = ? AND game_start_time >= ? LIMIT 50 OFFSET ? * 50";
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
                    messageJson.put("score1", resultSet.getInt("score1"));
                    messageJson.put("team2", resultSet.getString("team2"));
                    messageJson.put("odds2", resultSet.getFloat("odds2"));
                    messageJson.put("line2", resultSet.getFloat("line2"));
                    messageJson.put("score2", resultSet.getInt("score2"));
                    messageJson.put("last_update", resultSet.getTimestamp("last_update"));
                    messageJson.put("game_start_time", resultSet.getTimestamp("game_start_time"));
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
            sendResponse(exchange, 500, "{\"error\": \"Get games in league failed\"}");
        }
    }

    private void handleGetGamesByID(HttpExchange exchange, String group_name, String league_name, int game_id) throws IOException, SQLException {
        try {
            if (has_been_deleted(group_name)) {
                sendResponse(exchange, 410, "{\"error\": \"Group has been deleted\"}");
                return;
            }

            JSONObject messageJson = new JSONObject();
            String query = "SELECT * FROM games WHERE game_id = ?";
            try (PreparedStatement statement = dbConnection.prepareStatement(query)) {
                statement.setInt(1, game_id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        messageJson.put("game_id", resultSet.getInt("game_id"));
                        messageJson.put("team1", resultSet.getString("team1"));
                        messageJson.put("odds1", resultSet.getFloat("odds1"));
                        messageJson.put("line1", resultSet.getFloat("line1"));
                        messageJson.put("score1", resultSet.getInt("score1"));
                        messageJson.put("team2", resultSet.getString("team2"));
                        messageJson.put("odds2", resultSet.getFloat("odds2"));
                        messageJson.put("line2", resultSet.getFloat("line2"));
                        messageJson.put("score2", resultSet.getInt("score2"));
                        messageJson.put("last_update", resultSet.getTimestamp("last_update"));
                        messageJson.put("game_start_time", resultSet.getTimestamp("game_start_time"));
                        messageJson.put("status", resultSet.getString("status"));
                        messageJson.put("winner", resultSet.getString("winner"));
                        messageJson.put("league", resultSet.getString("league"));
                    }
                    String response = messageJson.toString();
                    sendResponse(exchange, 200, response);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "{\"error\": \"Get game by id failed\"}");
        }
    }

    private void handleGetBetsByStatus(HttpExchange exchange, String group_name, String username, int page, boolean been_distributed) throws IOException, SQLException {
        try {
            if (has_been_deleted(group_name)) {
                sendResponse(exchange, 410, "{\"error\": \"Group has been deleted\"}");
                return;
            }

            roleInGroup(username, group_name);
            JSONArray jsonArray = new JSONArray();
            String query = "SELECT * FROM bets WHERE group_name = ? AND username = ? AND been_distributed = ? LIMIT 50 OFFSET ? * 50";
            String gameQuery = "SELECT * FROM games WHERE game_id = ?";
            try (PreparedStatement statement = dbConnection.prepareStatement(query)) {
                statement.setString(1, group_name);
                statement.setString(2, username);
                statement.setBoolean(3, been_distributed);
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
                        messageJson.put("time_placed", resultSet.getTimestamp("time_placed"));
                        messageJson.put("been_distributed", resultSet.getBoolean("been_distributed"));
                        messageJson.put("is_parlay", resultSet.getBoolean("is_parlay"));
                        try (PreparedStatement gameStatement = dbConnection.prepareStatement(gameQuery)) {
                            gameStatement.setInt(1, resultSet.getInt("game_id"));
                            try (ResultSet gameResultSet = gameStatement.executeQuery()) {
                                if (gameResultSet.next()) {
                                    messageJson.put("game_id", gameResultSet.getInt("game_id"));
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
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "{\"error\": \"Get bets failed\"}");
        }
    }


    private void handleBuyBet(HttpExchange exchange, String group_name, String username) throws IOException, SQLException {
        try {
            if (has_been_deleted(group_name) && !isGroupActive(group_name)) {
                sendResponse(exchange, 410, "{\"error\": \"Group has been deleted or is inactive\"}");
                return;
            }

            roleInGroup(username, group_name);
            InputStream inputStream = exchange.getRequestBody();
            String requestBody = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            JSONObject jsonObject = new JSONObject(requestBody);
            int game_id = jsonObject.getInt("game_id");
            String bet_type = jsonObject.getString("type");
            float wagered = (float) jsonObject.getDouble("wagered");
            String picked_winner = jsonObject.getString("picked_winner");
            boolean isParlay = false, been_distributed = false;
            String team1 = "", team2 = "";
            float odds1 = 0.0f, odds2 = 0.0f;
            float line1 = 0.0f, line2 = 0.0f;
            String gameStatus = "";
            System.out.println("BetHandler: Preparing DB statements");

            // atomically do the following
            dbConnection.setAutoCommit(false);
            try {
                // Pull game info from games by game_id
                try (PreparedStatement gameStatement = dbConnection.prepareStatement(
                        "SELECT * FROM games WHERE game_id = ? FOR UPDATE")) {
                    gameStatement.setInt(1, game_id);
                    try (ResultSet gameResultSet = gameStatement.executeQuery()) {
                        if (gameResultSet.next()) {
                            gameStatus = gameResultSet.getString("status");
                            odds1 = gameResultSet.getFloat("odds1");
                            odds2 = gameResultSet.getFloat("odds2");
                            line1 = gameResultSet.getFloat("line1");
                            line2 = gameResultSet.getFloat("line2");
                            team1 = gameResultSet.getString("team1");
                            team2 = gameResultSet.getString("team2");

                        } else {
                            dbConnection.rollback();
                            sendResponse(exchange, 404, "{\"error\": \"Game not found\"}");
                            return;
                        }
                    }
                }
                System.out.println("BetHandler: Checking if game is available to bet");

                // Check status is "upcoming"
                if (!"upcoming".equalsIgnoreCase(gameStatus)) {
                    dbConnection.rollback();
                    sendResponse(exchange, 400, "{\"error\": \"Game is already being played or over.\"}");
                    return;
                }

                // Calculate amount to win from wagered
                float amountToWin;
                if ("h2h".equalsIgnoreCase(bet_type)) {
                    if (team1.equalsIgnoreCase(picked_winner)) {
                        if (odds1 < 0) {
                            amountToWin = (wagered * 100/(-odds1));
                        } else {
                            amountToWin = (wagered * (odds1)/100);
                        }
                    } else if (team2.equalsIgnoreCase(picked_winner)) {
                        if (odds2 < 0) {
                            amountToWin = (wagered * 100/(-odds2));
                        } else {
                            amountToWin = (wagered * (odds2)/100);
                        }
                    } else {
                        sendResponse(exchange, 400, "{\"error\": \"Invalid winner choice.\"}");
                        return;
                    }
                } else {
                    sendResponse(exchange, 400, "{\"error\": \"Invalid bet type.\"}");
                    return;
                }

                System.out.println("BetHandler: Inserting bet into DB");
                // Insert into bets
                try (PreparedStatement insertBetStatement = dbConnection.prepareStatement(
                        "INSERT INTO bets (game_id, username, type, wagered, picked_winner, amount_to_win, been_distributed, is_parlay, group_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    insertBetStatement.setInt(1, game_id);
                    insertBetStatement.setString(2, username);
                    insertBetStatement.setString(3, bet_type);
                    insertBetStatement.setFloat(4, wagered);
                    insertBetStatement.setString(5, picked_winner);
                    insertBetStatement.setFloat(6, amountToWin);
                    insertBetStatement.setBoolean(7, been_distributed);
                    insertBetStatement.setBoolean(8, isParlay);
                    insertBetStatement.setString(9, group_name);

                    insertBetStatement.executeUpdate();
                }

                String updateAccount = "UPDATE accounts SET current_cash = current_cash - ? WHERE username = ? AND group_name = ?";
                try (PreparedStatement updateBetStatement = dbConnection.prepareStatement(updateAccount)) {
                    updateBetStatement.setFloat(1, wagered);
                    updateBetStatement.setString(2, username);
                    updateBetStatement.setString(3, group_name);
                    updateBetStatement.executeUpdate();
                }

                // Commit the transaction
                dbConnection.commit();

                // Send success response
                sendResponse(exchange, 200, "Bet placed successfully.");
            } catch (SQLException e) {
                dbConnection.rollback();
                throw e;
            } finally {
                dbConnection.setAutoCommit(true);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "{\"error\": \"Buy bet failed.\"}");
        }
    }

    private void handleSellBet(HttpExchange exchange, String group_name, String username) throws IOException, SQLException {
        // check if group has been deleted
        try {
            if (has_been_deleted(group_name) && !isGroupActive(group_name)) {
                sendResponse(exchange, 410, "{\"error\": \"Group has been deleted or is inactive\"}");
                return;
            }
            roleInGroup(username, group_name);
            InputStream inputStream = exchange.getRequestBody();
            String requestBody = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

            JSONObject jsonObject = new JSONObject(requestBody);
            int bet_id = jsonObject.getInt("bet_id");
            float wageredAmount = 0;
            boolean betExists = false;
            boolean beenDistributed = false;
            dbConnection.setAutoCommit(false);

            // atomically read bets user bet_id to get wagered and check that the bet exists , update if bets !been_distributed and update accounts where current_cash += from bet
            String query = "SELECT * FROM bets WHERE bet_id = ? AND username = ? AND group_name = ? FOR UPDATE";
            String updateBet = "UPDATE bets SET been_distributed = true WHERE bet_id = ?";
            String updateAccount = "UPDATE accounts SET current_cash = current_cash + ? WHERE username = ? AND group_name = ?";
            try (PreparedStatement checkBetStatement = dbConnection.prepareStatement(query)) {
                checkBetStatement.setInt(1, bet_id);
                checkBetStatement.setString(2, username);
                checkBetStatement.setString(3, group_name);

                try (ResultSet resultSet = checkBetStatement.executeQuery()) {
                    if (resultSet.next()) {
                        betExists = true;
                        wageredAmount = (float) resultSet.getDouble("wagered");
                        beenDistributed = resultSet.getBoolean("been_distributed");
                    }
                }

                if (!betExists) {
                    dbConnection.rollback();
                    sendResponse(exchange, 404, "{\"error\": \"Bet not found.\"}");
                    return;
                }

                if (beenDistributed) {
                    dbConnection.rollback();
                    sendResponse(exchange, 400, "{\"error\": \"Bet has already been distributed/sold.\"}");
                    return;
                }

                // Update the bet's been_distributed status
                try (PreparedStatement updateBetStatement = dbConnection.prepareStatement(updateBet)) {
                    updateBetStatement.setInt(1, bet_id);
                    updateBetStatement.executeUpdate();
                }

                // Update the user's account balance
                try (PreparedStatement updateAccountStatement = dbConnection.prepareStatement(updateAccount)) {
                    updateAccountStatement.setDouble(1, wageredAmount * 0.9); // 90% of original buy price
                    updateAccountStatement.setString(2, username);
                    updateAccountStatement.setString(3, group_name);
                    updateAccountStatement.executeUpdate();
                }

                // Commit the transaction
                dbConnection.commit();
                sendResponse(exchange, 200, "Bet sold successfully.");
            } catch (SQLException e) {
                dbConnection.rollback();
                throw e;
            } finally {
                dbConnection.setAutoCommit(true);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "{\"error\": \"Sell bet failed.\"}");
        }
    }
}

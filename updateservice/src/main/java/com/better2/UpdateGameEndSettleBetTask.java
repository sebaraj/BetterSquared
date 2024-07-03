/***********************************************************************************************************************
 *  File Name:       UpdateGameEndSettleBetTask.java
 *  Project:         Better2/updateservice
 *  Author:          Bryan SebaRaj
 *  Description:     Calls odds-api to get finished games and settles all bets associated with finished games
 *  Schedule:        Every 3 hours
 **********************************************************************************************************************/
package com.better2.updateservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class UpdateGameEndSettleBetTask implements Job {
    private static Connection dbConnection;
    private static String API_URL;
    private static String API_KEY;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        System.out.println("UpdateGameEndSettleBetTask: Executing job");
        //LocalDateTime today = LocalDateTime.now(ZoneOffset.UTC);
        API_KEY = System.getenv("UPDATE_API_KEY");
        if (API_KEY == null) {
            throw new JobExecutionException("API key not found in environment variables");
        }
        callApiAndGetFinishedGames(); // today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"))
    }

    private void callApiAndGetFinishedGames() {
        try {
            // Constructing API connection for specific date
            API_URL ="https://api.the-odds-api.com/v4/sports/baseball_mlb/scores/?apiKey=" + API_KEY + "&daysFrom=2";
            URL url = new URL(API_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                throw new RuntimeException("UpdateGameEndSettleBetTask: HTTP GET Request Failed with Error code : " + responseCode);
            }

            // Reading response from API
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // Parsing the JSON response and updating db
            ObjectMapper objectMapper = new ObjectMapper();
            List<GameScore> games = objectMapper.readValue(response.toString(), objectMapper.getTypeFactory().constructCollectionType(List.class, GameScore.class));
            updateGameScoreInDatabase(games);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateGameScoreInDatabase(List<GameScore> games) {
        try {
            // Connecting to DB and initializing queries
            dbConnection = connectToDatabase();
            String checkGameQuery = "SELECT * FROM games WHERE api_id = ?";
            String updateGameQuery = "UPDATE games SET last_update = ?, status = ?, score1 = ?, team1 = ?, score2 = ?, team2 = ?, winner = ? WHERE api_id = ?";
            PreparedStatement checkStmt = dbConnection.prepareStatement(checkGameQuery);
            PreparedStatement updateStmt = dbConnection.prepareStatement(updateGameQuery);

            for (GameScore game : games) {
                // Checks if game is done/score is updated
                List<GameScore.Score> scores = game.getScores();
                if (scores != null && !scores.isEmpty()) {
                    // Checking if the game already exists
                    checkStmt.setString(1, game.getId());
                    ResultSet rs = checkStmt.executeQuery();
                    int gameId = 0, count = 0;
                    if (rs.next()) {
                        gameId = rs.getInt("id");
                        count = 1;
                    }
                    rs.close();
                    GameScore.Score score1 = scores.get(0);
                    GameScore.Score score2 = scores.get(1);
                    //ZonedDateTime zonedDateTime = ZonedDateTime.parse(bookmaker.getLast_update(), DateTimeFormatter.ISO_DATE_TIME);
                    // If game exists, update it
                    if (count == 1 && game.getCompleted()) {
                        updateStmt.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
                        //updateStmt.setDate(1, Date.valueOf(LocalDate.now()));
                        updateStmt.setString(2, "settled");
                        int score1Val = Integer.parseInt(score1.getScore());
                        int score2Val = Integer.parseInt(score2.getScore());
                        updateStmt.setInt(3, score1Val);
                        updateStmt.setString(4, score1.getName());
                        updateStmt.setInt(5, score2Val);
                        updateStmt.setString(6, score2.getName());
                        String winner = (score1Val > score2Val) ? score1.getName() : score2.getName();
                        updateStmt.setString(7, winner);
                        updateStmt.setString(8, game.getId());
                        updateStmt.executeUpdate();
                        settleBetsWithGameID(gameId, winner);
                    }
                }

            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            // Close database connection
            try {
                if (dbConnection != null) {
                    dbConnection.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void settleBetsWithGameID(int gameId, String winner) {
        try {
            String checkGameQuery = "SELECT * FROM bets WHERE game_id = ?";
            String settleQuery = "UPDATE bets SET been_distributed = TRUE WHERE bet_id = ?";
            String updateAccountWinQuery = "UPDATE accounts SET current_cash = current_cash + ? WHERE username = ? and group_name = ?";

            PreparedStatement checkStmt = dbConnection.prepareStatement(checkGameQuery);
            checkStmt.setInt(1, gameId);
            ResultSet rs = checkStmt.executeQuery();
            dbConnection.setAutoCommit(false);
            while (rs.next()) {
                if (winner.equals(rs.getString("picked_winner")) && !rs.getBoolean("been_distributed")) {
                    PreparedStatement updateWinStmt = dbConnection.prepareStatement(updateAccountWinQuery);
                    updateWinStmt.setFloat(1, rs.getFloat("amount_to_win"));
                    updateWinStmt.setString(2, rs.getString("username"));
                    updateWinStmt.setString(3, rs.getString("group_name"));
                    updateWinStmt.executeUpdate();
                    updateWinStmt.close();
                }
                PreparedStatement settledStmt = dbConnection.prepareStatement(settleQuery);
                settledStmt.setInt(1, rs.getInt("bet_id"));
                settledStmt.executeUpdate();
                settledStmt.close();

            }
            dbConnection.commit();
        } catch (SQLException e) {
            if (dbConnection != null) {
                try {
                    // Rollback in case of exception
                    dbConnection.rollback();
                } catch (SQLException rollbackException) {
                    rollbackException.printStackTrace();
                }
            }
            e.printStackTrace();
        } finally {
            try {
                // Reset auto-commit
                dbConnection.setAutoCommit(true);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    // Class to represent the structure of a game in the API response
    static class GameScore {
        private String id;
        private String sport_key;
        private String sport_title;
        private String commence_time;
        private boolean completed;
        private String home_team;
        private String away_team;
        private List<Score> scores;
        private String last_update;

        public static class Score {
            private String name;
            private String score;

            // Getters and setters for each field
            public String getName() { return name; }
            public void setName(String name) { this.name = name; }

            public String getScore() { return score; }
            public void setScore(String score) { this.score = score; }
        }

        // Getters and setters for each field
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getSport_key() { return sport_key; }
        public void setSport_key(String sport_key) { this.sport_key = sport_key; }

        public String getSport_title() { return sport_title; }
        public void setSport_title(String sport_title) { this.sport_title = sport_title; }

        public String getCommence_time() { return commence_time; }
        public void setCommence_time(String commence_time) { this.commence_time = commence_time; }

        public boolean getCompleted() { return completed; }
        public void setCompleted(boolean completed) { this.completed = completed; }

        public String getHome_team() { return home_team; }
        public void setHome_team(String home_team) { this.home_team = home_team; }

        public String getAway_team() { return away_team; }
        public void setAway_team(String away_team) { this.away_team = away_team; }

        public List<Score> getScores() { return scores; }
        public void setScores(List<Score> scores) { this.scores = scores; }

        public String getLast_update() { return last_update; }
        public void setLast_update(String last_update) { this.last_update = last_update; }
    }

    private static Connection connectToDatabase() throws SQLException {
        String url = "jdbc:postgresql://"+ System.getenv("UPDATE_DB_HOST") +":" + System.getenv("UPDATE_DB_PORT") + "/" + System.getenv("UPDATE_DB_NAME");
        String user = System.getenv("UPDATE_DB_USER");
        String password = System.getenv("UPDATE_DB_PASSWORD");
        return DriverManager.getConnection(url, user, password);
    }
}
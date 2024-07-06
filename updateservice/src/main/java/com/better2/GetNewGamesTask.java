/***********************************************************************************************************************
 *  File Name:       GetNetGamesTask.java
 *  Project:         Better2/updateservice
 *  Author:          Bryan SebaRaj
 *  Description:     Calls odds-api to get game information, adding new games into database
 *  Schedule:        Once a day, at 1:00 am
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

public class GetNewGamesTask implements Job {
    private static Connection dbConnection;
    private static String API_URL;
    private static String API_KEY;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        System.out.println("GetNewGamesTask: Executing job");
        LocalDateTime today = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime dateIn2Days = today.plusDays(2);
        API_KEY = System.getenv("UPDATE_API_KEY");
        if (API_KEY == null) {
            throw new JobExecutionException("API key not found in environment variables");
        }
        callApiAndStoreGames(today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")), dateIn2Days.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")));
    }

    private void callApiAndStoreGames(String date, String dateIn2Days) {
        try {
            // Constructing API connection for specific date range
            API_URL ="https://api.the-odds-api.com/v4/sports/baseball_mlb/odds/?apiKey=" + API_KEY + "&regions=us&bookmakers=fanduel&markets=h2h&oddsFormat=american&commenceTimeFrom=" + date + "&commenceTimeTo=" + dateIn2Days;
            URL url = new URL(API_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                throw new RuntimeException("HTTP GET Request Failed with Error code : " + responseCode);
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
            List<Game> games = objectMapper.readValue(response.toString(), objectMapper.getTypeFactory().constructCollectionType(List.class, Game.class));
            storeGamesInDatabase(games);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void storeGamesInDatabase(List<Game> games) {
        try {
            // Connecting to DB and initializing queries
            dbConnection = connectToDatabase();
            String checkGameQuery = "SELECT COUNT(*) FROM games WHERE api_id = ?";
            String insertGameQuery = "INSERT INTO games (api_id, league, game_start_time, team1, team2, odds1, odds2, last_update, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            String updateGameQuery = "UPDATE games SET team1 = ?, odds1 = ?, team2 = ?, odds2 = ?, last_update = ?, game_start_time = ? WHERE api_id = ?";
            PreparedStatement checkStmt = dbConnection.prepareStatement(checkGameQuery);
            PreparedStatement insertStmt = dbConnection.prepareStatement(insertGameQuery);
            PreparedStatement updateStmt = dbConnection.prepareStatement(updateGameQuery);

            for (Game game : games) {
                // Check if the game already exists
                checkStmt.setString(1, game.getId());
                ResultSet rs = checkStmt.executeQuery();
                rs.next();
                int count = rs.getInt(1);
                Game.Bookmaker bookmaker = game.getBookmakers().get(0);
                Game.Outcome outcome1 = bookmaker.getMarkets().get(0).getOutcomes().get(0);
                Game.Outcome outcome2 = bookmaker.getMarkets().get(0).getOutcomes().get(1);
                ZonedDateTime zonedDateTime = ZonedDateTime.parse(bookmaker.getLast_update(), DateTimeFormatter.ISO_DATE_TIME); // DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX"));
                ZonedDateTime zonedStartDateTime = ZonedDateTime.parse(game.getCommence_time(), DateTimeFormatter.ISO_DATE_TIME);
                //LocalDateTime localDate = zonedDateTime.toLocalDate();
                // If game exists, update it
                if (count == 1) {
                    updateStmt.setString(1, game.getHome_team());
                    updateStmt.setFloat(2, outcome1.getPrice());
                    updateStmt.setString(3, game.getAway_team());
                    updateStmt.setFloat(4, outcome2.getPrice());
                    //updateStmt.setDate(5, Date.valueOf(localDate));
                    updateStmt.setTimestamp(5, Timestamp.valueOf(zonedDateTime.toLocalDateTime()));
                    updateStmt.setTimestamp(6, Timestamp.valueOf(zonedStartDateTime.toLocalDateTime()));
                    updateStmt.setString(7, game.getId());
                    updateStmt.executeUpdate();
                }

                // If the game doesn't exist, insert it
                if (count == 0) {
                    insertStmt.setString(1, game.getId());
                    insertStmt.setString(2, game.getSport_key());
                     // DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX"));
                    //LocalDateTime startTime = zonedStartDateTime.toLocalDate();
                    //LocalDate startTime = LocalDate.parse(game.getCommence_time(), DateTimeFormatter.ISO_LOCAL_DATE);
                    //insertStmt.setDate(3, Date.valueOf(startTime));
                    insertStmt.setTimestamp(3, Timestamp.valueOf(zonedStartDateTime.toLocalDateTime()));
                    insertStmt.setString(4, game.getHome_team());
                    insertStmt.setString(5, game.getAway_team());
                    insertStmt.setFloat(6, outcome1.getPrice());
                    insertStmt.setFloat(7, outcome2.getPrice());
                    insertStmt.setTimestamp(8, Timestamp.valueOf(zonedDateTime.toLocalDateTime()));
                    insertStmt.setString(9, "upcoming");
                    insertStmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Class to represent the structure of a game in the API response
    static class Game {
        private String id;
        private String sport_key;
        private String sport_title;
        private String commence_time;
        private String home_team;
        private String away_team;
        private List<Bookmaker> bookmakers;

        public static class Bookmaker {
            private String key;
            private String title;
            private String last_update;
            private List<Market> markets;

            // Getters and setters for each field
            public String getKey() { return key; }
            public void setKey(String key) { this.key = key; }

            public String getTitle() { return title; }
            public void setTitle(String title) { this.title = title; }

            public String getLast_update() { return last_update; }
            public void setLast_update(String last_update) { this.last_update = last_update; }

            public List<Market> getMarkets() { return markets; }
            public void setMarkets(List<Market> markets) { this.markets = markets; }
        }

        public static class Market {
            private String key;
            private String last_update;
            private List<Outcome> outcomes;

            // Getters and setters for each field
            public String getKey() { return key; }
            public void setKey(String key) { this.key = key; }

            public String getLast_update() { return last_update; }
            public void setLast_update(String last_update) { this.last_update = last_update; }

            public List<Outcome> getOutcomes() { return outcomes; }
            public void setOutcomes(List<Outcome> outcomes) { this.outcomes = outcomes; }
        }

        public static class Outcome {
            private String name;
            private int price;
            private Double point;

            // Getters and setters for each field
            public String getName() { return name; }
            public void setName(String name) { this.name = name; }

            public int getPrice() { return price; }
            public void setPrice(int price) { this.price = price; }

            public Double getPoint() { return point; }
            public void setPoint(Double point) { this.point = point; }
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

        public String getHome_team() { return home_team; }
        public void setHome_team(String home_team) { this.home_team = home_team; }

        public String getAway_team() { return away_team; }
        public void setAway_team(String away_team) { this.away_team = away_team; }

        public List<Bookmaker> getBookmakers() { return bookmakers; }
        public void setBookmakers(List<Bookmaker> bookmakers) { this.bookmakers = bookmakers; }
    }

    private static Connection connectToDatabase() throws SQLException {
        String url = "jdbc:postgresql://"+ System.getenv("UPDATE_DB_HOST") +":" + System.getenv("UPDATE_DB_PORT") + "/" + System.getenv("UPDATE_DB_NAME");
        String user = System.getenv("UPDATE_DB_USER");
        String password = System.getenv("UPDATE_DB_PASSWORD");
        return DriverManager.getConnection(url, user, password);
    }
}
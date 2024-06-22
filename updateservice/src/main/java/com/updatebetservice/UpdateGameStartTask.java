package com.updateservice.update;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class UpdateGameStartTask implements Job {
    private static Connection dbConnection;
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        System.out.println("Executing update game start task");
        int gameId = context.getJobDetail().getJobDataMap().getInt("gameId");
        try {
            // Update the game status in the database
            connectToDatabase();
            PreparedStatement updateGameStart = dbConnection.prepareStatement("UPDATE games SET status = 'playing' WHERE id = ?");
            updateGameStart.setInt(1, gameId);
            updateGameStart.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (updateGameStart != null) {
                try {
                    updateGameStart.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            if (dbConnection != null) {
                try {
                    dbConnection.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void connectToDatabase() throws SQLException {
        System.out.println("Connecting to DB...");
        String url = "jdbc:postgresql://"+ System.getenv("UPDATE_DB_HOST") +":" + System.getenv("UPDATE_DB_PORT") + "/" + System.getenv("UPDATE_DB_NAME");
        String user = System.getenv("UPDATE_DB_USER");
        String password = System.getenv("UPDATE_DB_PASSWORD");

        dbConnection = DriverManager.getConnection(url, user, password);
        System.out.println("Connected to the PostgreSQL server successfully.");
    }
}
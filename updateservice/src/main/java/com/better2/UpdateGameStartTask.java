/***********************************************************************************************************************
 *  File Name:       UpdateGameStartTask.java
 *  Project:         Better2/updateservice
 *  Author:          Bryan SebaRaj
 *  Description:     Updates all games which ended the previous day to inactive
 *  Schedule:        Set up ScheduleGamesTask, at start time of respective game
 **********************************************************************************************************************/
package com.better2.updateservice;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class UpdateGameStartTask implements Job {
    private static Connection dbConnection;
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        System.out.println("UpdateGameStartTask: Executing job");
        PreparedStatement updateGameStart = null;
        int gameId = context.getJobDetail().getJobDataMap().getInt("gameId");
        try {
            // Updating the game status in the database
            dbConnection = connectToDatabase();
            updateGameStart = dbConnection.prepareStatement("UPDATE games SET status = 'playing' WHERE id = ?");
            updateGameStart.setInt(1, gameId);
            updateGameStart.executeUpdate();
            System.out.println("UpdateGameStartTask: Update game " + gameId + " successfully executed");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // CLosing prepared statement/psql connection if they are open
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

    private static Connection connectToDatabase() throws SQLException {
        String url = "jdbc:postgresql://"+ System.getenv("UPDATE_DB_HOST") +":" + System.getenv("UPDATE_DB_PORT") + "/" + System.getenv("UPDATE_DB_NAME");
        String user = System.getenv("UPDATE_DB_USER");
        String password = System.getenv("UPDATE_DB_PASSWORD");
        return DriverManager.getConnection(url, user, password);
    }
}
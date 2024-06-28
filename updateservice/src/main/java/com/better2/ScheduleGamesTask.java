/***********************************************************************************************************************
 *  File Name:       ScheduleGamesTask.java
 *  Project:         Better2/updateservice
 *  Author:          Bryan SebaRaj
 *  Description:     Gets all games which start today from database+schedule UpdateGameStartTask to run as games start
 *  Schedule:        Once a day, at 3:00 am
 **********************************************************************************************************************/
package com.better2.updateservice;

import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.Scheduler;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Instant;
import java.util.Date;


public class ScheduleGamesTask implements Job {
    private static Connection dbConnection;
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        System.out.println("ScheduleGamesTask: Executing job");
        try {
            dbConnection = connectToDatabase();
            Statement stmt = dbConnection.createStatement();
            String today = LocalDate.now().toString();
            // Fetching today's games from  database
            ResultSet rs = stmt.executeQuery("SELECT id, game_start_time FROM games WHERE start_time::date = '" + today + "'");

            while (rs.next()) {
                int gameId = rs.getInt("id");
                LocalDateTime startTime = rs.getTimestamp("game_start_time").toLocalDateTime();
                scheduleGameStartUpdateJob(gameId, startTime);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Closing psql connection if it is open
            if (dbConnection != null) {
                try {
                    dbConnection.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    private void scheduleGameStartUpdateJob(int gameId, LocalDateTime startTime) throws Exception {
        JobDetail jobDetail = JobBuilder.newJob(UpdateGameStartTask.class).withIdentity("updateGameStartJob-" + gameId).usingJobData("gameId", gameId).build();
        Trigger trigger = TriggerBuilder.newTrigger().withIdentity("trigger-" + gameId).startAt(Date.from(startTime.atZone(ZoneId.systemDefault()).toInstant())).build();
        Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
        scheduler.scheduleJob(jobDetail, trigger);
    }

    private static Connection connectToDatabase() throws SQLException {
        String url = "jdbc:postgresql://"+ System.getenv("UPDATE_DB_HOST") +":" + System.getenv("UPDATE_DB_PORT") + "/" + System.getenv("UPDATE_DB_NAME");
        String user = System.getenv("UPDATE_DB_USER");
        String password = System.getenv("UPDATE_DB_PASSWORD");
        return DriverManager.getConnection(url, user, password);
    }
}
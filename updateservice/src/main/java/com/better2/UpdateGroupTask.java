/***********************************************************************************************************************
 *  File Name:       UpdateGroupTask.java
 *  Project:         Better2/updateservice
 *  Author:          Bryan SebaRaj
 *  Description:     Updates all games which ended the previous day to inactive
 *  Schedule:        Once a day, at 12:01 am
 **********************************************************************************************************************/
package com.better2.updateservice;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Date;
import java.time.LocalDate;

public class UpdateGroupTask implements Job {
    private static Connection dbConnection;
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        System.out.println("UpdateGroupTask: Executing job");
        PreparedStatement updateGroupStatus = null;
        try {
            // Updating group active status in database
            dbConnection = connectToDatabase();
            LocalDate yesterday = LocalDate.now().minusDays(1);
            updateGroupStatus = dbConnection.prepareStatement("UPDATE groups SET is_active = FALSE WHERE end_date = ?");
            updateGroupStatus.setDate(1, Date.valueOf(yesterday));
            int rowsUpdated = updateGroupStatus.executeUpdate();
            System.out.println("UpdateGroupTask: Updated " + rowsUpdated + " rows to set is_active to FALSE where end_date was " + yesterday);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // CLosing prepared statement/psql connection if they are open
            if (updateGroupStatus != null) {
                try {
                    updateGroupStatus.close();
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
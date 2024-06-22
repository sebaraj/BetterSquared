package com.updateservice.update;

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
        System.out.println("Executing updateGroup task");
        PreparedStatement updateGroupStatus = null;
        try {
            // Update the game status in the database
            connectToDatabase();
            LocalDate yesterday = LocalDate.now().minusDays(1);
            updateGroupStatus = dbConnection.prepareStatement("UPDATE groups SET is_active = FALSE WHERE end_date = ?");
            updateGroupStatus.setDate(1, Date.valueOf(yesterday));
            int rowsUpdated = updateGroupStatus.executeUpdate();
            System.out.println("Updated " + rowsUpdated + " rows to set is_active to FALSE where end_date was " + yesterday);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
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

    private static void connectToDatabase() throws SQLException {
        System.out.println("Connecting to DB...");
        String url = "jdbc:postgresql://"+ System.getenv("UPDATE_DB_HOST") +":" + System.getenv("UPDATE_DB_PORT") + "/" + System.getenv("UPDATE_DB_NAME");
        String user = System.getenv("UPDATE_DB_USER");
        String password = System.getenv("UPDATE_DB_PASSWORD");

        dbConnection = DriverManager.getConnection(url, user, password);
        System.out.println("Connected to the PostgreSQL server successfully.");
    }
}
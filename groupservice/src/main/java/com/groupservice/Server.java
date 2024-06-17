package com.groupservice.server;

import com.sun.net.httpserver.HttpServer;
import com.rabbitmq.client.ConnectionFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.Scanner;
import java.lang.System;
import org.postgresql.Driver;

public class Server {

    private static java.sql.Connection dbConnection;

    public static void main(String[] args) {
        try {

            // connect to DB
            connectToDatabase(); // dotenv

            // Create an HttpServer instance, listening on port HTTP_SERVER_PORT with backlog HTTP_SERVER_BACKLOG
            HttpServer server = HttpServer.create(new InetSocketAddress(System.getenv("GROUP_HTTP_SERVER_HOST"), Integer.parseInt(System.getenv("GROUP_HTTP_SERVER_PORT"))), Integer.parseInt(System.getenv("GROUP_HTTP_SERVER_BACKLOG")));

            // Create a context for the endpoints

            server.createContext("/group", new GroupHandler(dbConnection)); // handle get, post(create), put(update), delete by id (dont need id for post/create) (use user in header from parsed JWT to get credentials to authenticate group_role & set group_role).  join/leave group handled by /group/{id use regex}/join and /group/{id use regex}/leave (use user in header from parsed JWT to choose which to update). getLeaderboard handled by /group/{id use regex}/leaderboard?page=1 (only accessible to usernames in group and only shows cash/position). makeAdmin handled by /group/{id use regex}/admins?page=1 (only accessible by group creator check with username in JWT parse). getBetsAndCashForUser handled by /group/{id use regex}/user/{id use regex} (verify from username in parsed JWT that clientuser is in group and that other user that is being searched for is also in group)
            server.createContext("/groups", new GroupsHandler(dbConnection)); // go through all active groups. query parametes. name & page


            // New pausable thread pool executor
            PausableThreadPoolExecutor executor = new PausableThreadPoolExecutor(Integer.parseInt(System.getenv("GROUP_THREAD_POOL_CORE_SIZE")), Integer.parseInt(System.getenv("GROUP_THREAD_POOL_MAX_SIZE")), Integer.parseInt(System.getenv("GROUP_THREAD_POOL_KEEP_ALIVE")), TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
            int startedThreads = executor.prestartAllCoreThreads();
            System.out.println("Current # of active threads in pool for group service: " + startedThreads);
            // Start the server
            server.setExecutor(executor);
            server.start();
            System.out.println("Server started on port "+System.getenv("GROUP_HTTP_SERVER_PORT"));


        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }
    }

    private static void connectToDatabase() throws SQLException {
//        Class.forName("org.postgresql.Driver");
        System.out.println("Connecting to DB...");
        String url = "jdbc:postgresql://"+ System.getenv("GROUP_DB_HOST") +":" + System.getenv("GROUP_DB_PORT") + "/" + System.getenv("GROUP_DB_NAME");
        String user = System.getenv("GROUP_DB_USER");
        String password = System.getenv("GROUP_DB_PASSWORD");

        dbConnection = DriverManager.getConnection(url, user, password);
        System.out.println("Connected to the PostgreSQL server successfully.");
    }

}
/***********************************************************************************************************************
 *  File Name:       Server.java
 *  Project:         Better2/betservice
 *  Author:          Bryan SebaRaj
 *  Description:     Intializes Postgres connection/HTTP server for bet service
 **********************************************************************************************************************/
package com.better2.betservice;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.lang.System;
import org.postgresql.Driver;

public class Server {

    private static Connection dbConnection;

    public static void main(String[] args) {
        try {
            // Connecting to postgres database
            dbConnection = connectToDatabase();
            System.out.println("Server: Established connection to psql db.");

            // Initializing an HttpServer, contexts, and executor
            HttpServer server = HttpServer.create(new InetSocketAddress(System.getenv("BET_HTTP_SERVER_HOST"), Integer.parseInt(System.getenv("BET_HTTP_SERVER_PORT"))), Integer.parseInt(System.getenv("BET_HTTP_SERVER_BACKLOG")));
            server.createContext("/bet", new BetHandler(dbConnection));
            PausableThreadPoolExecutor executor = new PausableThreadPoolExecutor(Integer.parseInt(System.getenv("BET_THREAD_POOL_CORE_SIZE")), Integer.parseInt(System.getenv("BET_THREAD_POOL_MAX_SIZE")), Integer.parseInt(System.getenv("BET_THREAD_POOL_KEEP_ALIVE")), TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
            int startedThreads = executor.prestartAllCoreThreads();
            System.out.println("Server: Prestarted " + startedThreads + " threads for HTTP server");

            // Starting the server
            server.setExecutor(executor);
            server.start();
            System.out.println("Server: HTTP server listening on port " + System.getenv("BET_HTTP_SERVER_PORT"));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Connection connectToDatabase() throws SQLException {
        String url = "jdbc:postgresql://"+ System.getenv("BET_DB_HOST") +":" + System.getenv("BET_DB_PORT") + "/" + System.getenv("BET_DB_NAME");
        String user = System.getenv("BET_DB_USER");
        String password = System.getenv("BET_DB_PASSWORD");
        return DriverManager.getConnection(url, user, password);
    }
}
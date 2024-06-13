package com.authservice.server;

import com.sun.net.httpserver.HttpServer;
//import io.github.cdimascio.dotenv.Dotenv;

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

    private static Connection connection;

    static class StopServerException extends Exception {
        public StopServerException(String message) {
            super(message);
        }
    }

    public static void main(String[] args) {
        try {
            // Load the .env file
            //Dotenv dotenv = Dotenv.load();
            // connect to DB
            connectToDatabase(); // dotenv

            // Create an HttpServer instance, listening on port HTTP_SERVER_PORT with backlog HTTP_SERVER_BACKLOG
            HttpServer server = HttpServer.create(new InetSocketAddress(System.getenv("AUTH_HTTP_SERVER_HOST"), Integer.parseInt(System.getenv("AUTH_HTTP_SERVER_PORT"))), Integer.parseInt(System.getenv("AUTH_HTTP_SERVER_BACKLOG")));

            // Create a context for the endpoints
            server.createContext("/login", new LoginHandler(connection));
            server.createContext("/signup", new SignUpHandler(connection));
            server.createContext("/changepassword", new ChangePasswordHandler(connection));
            server.createContext("/validate", new JWTAuthHandler(connection)); // cannot be accessed directly by client. called by gateway for jwt auth

            // New pausable thread pool executor
            PausableThreadPoolExecutor executor = new PausableThreadPoolExecutor(Integer.parseInt(System.getenv("AUTH_THREAD_POOL_CORE_SIZE")), Integer.parseInt(System.getenv("AUTH_THREAD_POOL_MAX_SIZE")), Integer.parseInt(System.getenv("AUTH_THREAD_POOL_KEEP_ALIVE")), TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
            int startedThreads = executor.prestartAllCoreThreads();
            System.out.println("Current # of active threads in pool for auth service: " + startedThreads);
            // Start the server
            server.setExecutor(executor);
            server.start();
            System.out.println("Server started on port "+System.getenv("AUTH_HTTP_SERVER_PORT"));

            // use `kubectl exec -it <pod-name> -c <container-name> -- /bin/sh` when running k8s or use k9s
//            Scanner scanner = new Scanner(System.in);
//
//            while (true) {
//                try {
//                    System.out.print("Enter command: ");
//                    if (scanner.hasNextLine()) {
//                        String input = scanner.nextLine();
//                        System.out.println("You entered: " + input);
//
//                        if ("gateway-server-stop".equalsIgnoreCase(input)) {
//                            throw new StopServerException("Stopping the server.");
//                        } else if ("gateway-thread-pool-pause".equalsIgnoreCase(input)) {
//                            executor.pause();
//                            System.out.println("Thread pool paused.");
//                        } else if ("gateway-thread-pool-resume".equalsIgnoreCase(input)) {
//                            executor.resume();
//                            System.out.println("Thread pool resumed.");
//                        }
//                    }
//
//                } catch (StopServerException e) {
//                    System.out.println("Shutting down server...");
//                    executor.shutdown();
//                    server.stop(0); // delay of 0
//                    System.out.println("Server shut down.");
//                    break;
//                }
//            }

        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }
    }

    private static void connectToDatabase() throws SQLException {
//        Class.forName("org.postgresql.Driver");
        System.out.println("Connecting to DB...");
        String url = "jdbc:postgresql://"+ System.getenv("AUTH_DB_HOST") +":" + System.getenv("AUTH_DB_PORT") + "/" + System.getenv("AUTH_DB_NAME");
        String user = System.getenv("AUTH_DB_USER");
        String password = System.getenv("AUTH_DB_PASSWORD");

        connection = DriverManager.getConnection(url, user, password);
        System.out.println("Connected to the PostgreSQL server successfully.");
    }


}
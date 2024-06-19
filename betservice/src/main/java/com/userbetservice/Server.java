package com.userbetservice.server;

import com.sun.net.httpserver.HttpServer;
import com.rabbitmq.client.ConnectionFactory;
//import com.rabbitmq.client.Connection;
//import com.rabbitmq.client.Channel;
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
    private static com.rabbitmq.client.Connection rabbitMQConnection;
    private static com.rabbitmq.client.Channel rabbitMQChannel;

    public static void main(String[] args) {
        try {
            // connect to DB
            connectToDatabase(); // dotenv

            connectToRabbitMQ();

            // Create an HttpServer instance, listening on port HTTP_SERVER_PORT with backlog HTTP_SERVER_BACKLOG
            HttpServer server = HttpServer.create(new InetSocketAddress(System.getenv("BET_HTTP_SERVER_HOST"), Integer.parseInt(System.getenv("BET_HTTP_SERVER_PORT"))), Integer.parseInt(System.getenv("BET_HTTP_SERVER_BACKLOG")));

            // Create a context for the endpoints
            server.createContext("/viewBet", new ViewBetHandler(dbConnection));

            // New pausable thread pool executor
            PausableThreadPoolExecutor executor = new PausableThreadPoolExecutor(Integer.parseInt(System.getenv("BET_THREAD_POOL_CORE_SIZE")), Integer.parseInt(System.getenv("BET_THREAD_POOL_MAX_SIZE")), Integer.parseInt(System.getenv("BET_THREAD_POOL_KEEP_ALIVE")), TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
            int startedThreads = executor.prestartAllCoreThreads();
            System.out.println("Current # of active threads in pool for user bet service: " + startedThreads);
            // Start the server
            server.setExecutor(executor);
            server.start();
            System.out.println("Server started on port "+System.getenv("BET_HTTP_SERVER_PORT"));

            // artifact from running program outside of docker/k8s. why pause/resume threads/shutdown server when i can just spin up/down containers
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
        String url = "jdbc:postgresql://"+ System.getenv("BET_DB_HOST") +":" + System.getenv("BET_DB_PORT") + "/" + System.getenv("BET_DB_NAME");
        String user = System.getenv("BET_DB_USER");
        String password = System.getenv("BET_DB_PASSWORD");

        dbConnection = DriverManager.getConnection(url, user, password);
        System.out.println("Connected to the PostgreSQL server successfully.");
    }

    private static void connectToRabbitMQ() {
        System.out.println("Connecting to RabbitMQ...");
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(System.getenv("RABBITMQ_HOST"));
        factory.setPort(Integer.parseInt(System.getenv("RABBITMQ_PORT")));
        //factory.setUsername(System.getenv("RABBITMQ_USER"));
        //factory.setPassword(System.getenv("RABBITMQ_PASSWORD"));
        try {
            rabbitMQConnection = factory.newConnection();
            rabbitMQChannel = rabbitMQConnection.createChannel();
            System.out.println("Connected to RabbitMQ successfully.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
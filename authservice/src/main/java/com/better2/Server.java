/***********************************************************************************************************************
 *  File Name:       Server.java
 *  Project:         Better2/authservice
 *  Author:          Bryan SebaRaj
 *  Description:     Intializes JWT cache/Postgres/RabbitMQ connections/HTTP server for auth service
 **********************************************************************************************************************/
package com.better2.authservice;

import com.sun.net.httpserver.HttpServer;
import com.rabbitmq.client.ConnectionFactory;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.lang.System;
import redis.clients.jedis.Jedis;

public class Server {

    private static java.sql.Connection dbConnection;
    private static com.rabbitmq.client.Connection rabbitMQConnection;
    private static com.rabbitmq.client.Channel rabbitMQChannel;
    private static Jedis jedis;

    public static void main(String[] args) {
        try {
            // Connecting to postgres database
            dbConnection = connectToDatabase();
            System.out.println("Server: Established connection to psql db.");

            // Connecting to RabbitMQ (using default exchange)
            rabbitMQChannel = connectToRabbitMQ();
            System.out.println("Server: Established connection to RabbitMQ.");

            // Connecting to JWT cache (writes to master)
            jedis = new Jedis(System.getenv("JWT_REDIS_MASTER_HOST"), Integer.parseInt(System.getenv("JWT_REDIS_PORT")));
            System.out.println("Server: Established connection to JWT cache.");

            // Initializing an HttpServer, contexts, and executor
            HttpServer server = HttpServer.create(new InetSocketAddress(System.getenv("AUTH_HTTP_SERVER_HOST"), Integer.parseInt(System.getenv("AUTH_HTTP_SERVER_PORT"))), Integer.parseInt(System.getenv("AUTH_HTTP_SERVER_BACKLOG")));
            server.createContext("/login", new LoginHandler(dbConnection));
            server.createContext("/signup", new SignUpHandler(dbConnection, rabbitMQChannel));
            server.createContext("/forgotpassword", new ForgotPasswordHandler(dbConnection, rabbitMQChannel));
            server.createContext("/validate", new JWTAuthHandler(jedis));
            PausableThreadPoolExecutor executor = new PausableThreadPoolExecutor(Integer.parseInt(System.getenv("AUTH_THREAD_POOL_CORE_SIZE")), Integer.parseInt(System.getenv("AUTH_THREAD_POOL_MAX_SIZE")), Integer.parseInt(System.getenv("AUTH_THREAD_POOL_KEEP_ALIVE")), TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
            int startedThreads = executor.prestartAllCoreThreads();
            System.out.println("Server: Prestarted " + startedThreads + " threads for HTTP server");

            // Starting the server
            server.setExecutor(executor);
            server.start();
            System.out.println("Server: HTTP server listening on port " + System.getenv("AUTH_HTTP_SERVER_PORT"));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static java.sql.Connection connectToDatabase() throws SQLException {
        String url = "jdbc:postgresql://"+ System.getenv("AUTH_DB_HOST") +":" + System.getenv("AUTH_DB_PORT") + "/" + System.getenv("AUTH_DB_NAME");
        String user = System.getenv("AUTH_DB_USER");
        String password = System.getenv("AUTH_DB_PASSWORD");
        return DriverManager.getConnection(url, user, password);
    }

    private static com.rabbitmq.client.Channel connectToRabbitMQ() {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(System.getenv("RABBITMQ_HOST"));
            factory.setPort(Integer.parseInt(System.getenv("RABBITMQ_PORT")));
            rabbitMQConnection = factory.newConnection();
            return rabbitMQConnection.createChannel();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
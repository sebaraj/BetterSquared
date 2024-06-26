package com.gatewayservice.server;

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
//import org.postgresql.Driver;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import java.util.HashSet;
import java.util.Set;

public class Server {

//    static class StopServerException extends Exception {
//        public StopServerException(String message) {
//            super(message);
//        }
//    }

    public static void main(String[] args) {
        try {
            Jedis jwtCacheConnection = new Jedis(System.getenv("JWT_REDIS_SLAVE_HOST"), Integer.parseInt(System.getenv("JWT_REDIS_PORT")));

            Set<HostAndPort> jedisClusterNodes = new HashSet<>();
            int rateLimiterPort = Integer.parseInt(System.getenv("RL_PORT"));
            jedisClusterNodes.add(new HostAndPort(System.getenv("RL_HOST_0"), rateLimiterPort));
            jedisClusterNodes.add(new HostAndPort(System.getenv("RL_HOST_1"), rateLimiterPort));
            jedisClusterNodes.add(new HostAndPort(System.getenv("RL_HOST_2"), rateLimiterPort));
            jedisClusterNodes.add(new HostAndPort(System.getenv("RL_HOST_3"), rateLimiterPort));
            jedisClusterNodes.add(new HostAndPort(System.getenv("RL_HOST_4"), rateLimiterPort));
            jedisClusterNodes.add(new HostAndPort(System.getenv("RL_HOST_5"), rateLimiterPort));
            JedisCluster rateLimiterConnection = new JedisCluster(jedisClusterNodes);
            System.out.println("Successfully connected to rate limiter/redis cluster.");
            // Create an HttpServer instance, listening on port HTTP_SERVER_PORT with backlog HTTP_SERVER_BACKLOG
            //System.out.println("GATEWAY HTTP Server started.");
            HttpServer server = HttpServer.create(new InetSocketAddress(System.getenv("GATEWAY_HTTP_SERVER_HOST"), Integer.parseInt(System.getenv("GATEWAY_HTTP_SERVER_PORT"))), Integer.parseInt(System.getenv("GATEWAY_HTTP_SERVER_BACKLOG")));
            System.out.println("GATEWAY HTTP Server started.");
            // Create a context for the endpoints
            server.createContext("/login", new LoginHandler(rateLimiterConnection)); // connection
            server.createContext("/signup", new SignUpHandler(rateLimiterConnection));
            server.createContext("/forgotpassword", new ForgotPasswordHandler(rateLimiterConnection));
            //server.createContext("/testvalidate", new TestValidateHandler(jwtCacheConnection));
            server.createContext("/group", new GroupHandler(jwtCacheConnection, rateLimiterConnection));
            server.createContext("/groups", new GroupHandler(jwtCacheConnection, rateLimiterConnection));
            server.createContext("/bet", new BetHandler(jwtCacheConnection, rateLimiterConnection));

            // New pausable thread pool executor
            PausableThreadPoolExecutor executor = new PausableThreadPoolExecutor(Integer.parseInt(System.getenv("GATEWAY_THREAD_POOL_CORE_SIZE")), Integer.parseInt(System.getenv("GATEWAY_THREAD_POOL_MAX_SIZE")), Integer.parseInt(System.getenv("GATEWAY_THREAD_POOL_KEEP_ALIVE")), TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
            int startedThreads = executor.prestartAllCoreThreads();
            System.out.println("Current # of active threads in pool for gateway service: " + startedThreads);
            // Start the server
            server.setExecutor(executor);
            server.start();
            System.out.println("Server started on port "+System.getenv("GATEWAY_HTTP_SERVER_PORT"));

            // artifact from running program outside of docker/k8s. why pause/resume threads/shutdown server when i can just spin up/down containers
//            Scanner scanner = new Scanner(System.in);
//            System.out.print("Enter command: ");
//            boolean shutdown = true;
//            while (shutdown) {
//                if (scanner.hasNextLine()) {
//                    String input = scanner.nextLine();
//                    System.out.println("You entered: " + input);
//
//                    if ("gateway-server-stop".equalsIgnoreCase(input)) {
//                        shutdown = false;
//                    } else if ("gateway-thread-pool-pause".equalsIgnoreCase(input)) {
//                        executor.pause();
//                        System.out.println("Thread pool paused.");
//                    } else if ("gateway-thread-pool-resume".equalsIgnoreCase(input)) {
//                        executor.resume();
//                        System.out.println("Thread pool resumed.");
//                    }
//
//                }
//            }
//            System.out.println("Shutting down server...");
//            executor.shutdown();
//            server.stop(0); // delay of 0
//            System.out.println("Server shut down.");



        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
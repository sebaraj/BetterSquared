/***********************************************************************************************************************
 *  File Name:       Server.java
 *  Project:         Better2/gatewayservice
 *  Author:          Bryan SebaRaj
 *  Description:     Intializes JWT cache/rate limiter connections/HTTP server for gateway service
 **********************************************************************************************************************/
package com.better2.gatewayservice;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.lang.System;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import java.util.HashSet;
import java.util.Set;

public class Server {
    public static void main(String[] args) {
        try {
            // Establishing redis connection to JWT cache
            Jedis jwtCacheConnection = new Jedis(System.getenv("JWT_REDIS_SLAVE_HOST"), Integer.parseInt(System.getenv("JWT_REDIS_PORT")));
            System.out.println("Server: Established JWT cache connection to redis slaves via twemproxy");

            // Establishing redis cluster connection to rate limiter
            Set<HostAndPort> jedisClusterNodes = new HashSet<>();
            int rateLimiterPort = Integer.parseInt(System.getenv("RL_PORT"));
            jedisClusterNodes.add(new HostAndPort(System.getenv("RL_HOST_0"), rateLimiterPort));
            jedisClusterNodes.add(new HostAndPort(System.getenv("RL_HOST_1"), rateLimiterPort));
            jedisClusterNodes.add(new HostAndPort(System.getenv("RL_HOST_2"), rateLimiterPort));
            jedisClusterNodes.add(new HostAndPort(System.getenv("RL_HOST_3"), rateLimiterPort));
            jedisClusterNodes.add(new HostAndPort(System.getenv("RL_HOST_4"), rateLimiterPort));
            jedisClusterNodes.add(new HostAndPort(System.getenv("RL_HOST_5"), rateLimiterPort));
            JedisCluster rateLimiterConnection = new JedisCluster(jedisClusterNodes);
            System.out.println("Server: Established redis cluster connection to rate limiter");

            // Creating an HTTP server instance to route all traffic to respective services/endpoints
            HttpServer server = HttpServer.create(new InetSocketAddress(System.getenv("GATEWAY_HTTP_SERVER_HOST"), Integer.parseInt(System.getenv("GATEWAY_HTTP_SERVER_PORT"))), Integer.parseInt(System.getenv("GATEWAY_HTTP_SERVER_BACKLOG")));
            server.createContext("/login", new LoginHandler(rateLimiterConnection));
            server.createContext("/signup", new SignUpHandler(rateLimiterConnection));
            server.createContext("/forgotpassword", new ForgotPasswordHandler(rateLimiterConnection));
            server.createContext("/group", new GroupHandler(jwtCacheConnection, rateLimiterConnection));
            server.createContext("/groups", new GroupHandler(jwtCacheConnection, rateLimiterConnection));
            server.createContext("/bet", new BetHandler(jwtCacheConnection, rateLimiterConnection));
            System.out.println("Server: Established HTTP server context");

            // Creating new pausable thread executor for the server
            PausableThreadPoolExecutor executor = new PausableThreadPoolExecutor(Integer.parseInt(System.getenv("GATEWAY_THREAD_POOL_CORE_SIZE")), Integer.parseInt(System.getenv("GATEWAY_THREAD_POOL_MAX_SIZE")), Integer.parseInt(System.getenv("GATEWAY_THREAD_POOL_KEEP_ALIVE")), TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
            int startedThreads = executor.prestartAllCoreThreads();
            System.out.println("Server: Prestarted " + startedThreads + " threads for HTTP server");

            // Starting the server
            server.setExecutor(executor);
            server.start();
            System.out.println("Server: HTTP server listening on port " + System.getenv("GATEWAY_HTTP_SERVER_PORT"));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
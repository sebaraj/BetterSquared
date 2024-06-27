/***********************************************************************************************************************
 *  File Name:       JWTAuthHandler.java
 *  Project:         Better2/authservice
 *  Author:          Bryan SebaRaj
 *  Description:     Auth-service-level, non-client-facing handler to validate JWT
 **********************************************************************************************************************/
package com.better2.authservice;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.lang.System;
import redis.clients.jedis.Jedis;
import java.util.Date;

public class JWTAuthHandler implements HttpHandler {

    private final String jwtSecret = System.getenv("AUTH_JWT_SECRET");
    private final Jedis jedis;

    public JWTAuthHandler(Jedis jedis) {
        this.jedis = jedis;
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(response.getBytes());
            outputStream.close();
        }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Getting HTTP request headers and extract JWT
        Map<String, List<String>> headers = exchange.getRequestHeaders();
        List<String> authorizationHeader = headers.get("Authorization");
        if (authorizationHeader == null || authorizationHeader.isEmpty()) {
            System.out.println("JWTAuthHandler: Authorization header is empty.");
            sendResponse(exchange, 401, "{\"error\": \"Authorization header is empty\"}");
            return;
        }
        String token = authorizationHeader.get(0).replace("Bearer ", "");

        try {
            // Validating JWT
            Algorithm algorithm = Algorithm.HMAC256(jwtSecret);
            JWTVerifier verifier = JWT.require(algorithm).withIssuer("auth0").build();
            DecodedJWT jwt = verifier.verify(token);
            System.out.println("JWTAuthHandler: Token is valid.");

            // Adding valid JWT to cache
            String username = jwt.getClaim("username").asString();
            Date expiresAt = jwt.getExpiresAt();
            if (expiresAt == null) {
                throw new RuntimeException("JWT does not have an expiration time");
            }
            long ttlMillis = expiresAt.getTime() - System.currentTimeMillis();
            int ttl = (int) (ttlMillis / 1000); // convert milliseconds to seconds
            jedis.setex(token, ttl, username);

            // Sending HTTP response back to gateway
            String response = "Authenticated" + "/" + username;
            sendResponse(exchange, 200, response);

        } catch (Exception e) {
            System.out.println("JWTAuthHandler: Invalid token.");
            sendResponse(exchange, 403, "{\"error\": \"Invalid token\"}");
        }

        exchange.close();
    }

}

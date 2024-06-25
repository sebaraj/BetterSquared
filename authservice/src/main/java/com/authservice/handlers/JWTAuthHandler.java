package com.authservice.server;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.sql.PreparedStatement;
import java.sql.SQLException;
//import io.github.cdimascio.dotenv.Dotenv;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.lang.System;
import redis.clients.jedis.Jedis;
import java.util.Date;

public class JWTAuthHandler implements HttpHandler {

    private Connection dbConnection;
    private final String jwtSecret;
    private final Jedis jedis;

    public JWTAuthHandler(Connection connection, Jedis jedis) {
        this.dbConnection = connection;
        this.jwtSecret = System.getenv("AUTH_JWT_SECRET");
        this.jedis = jedis;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        Map<String, List<String>> headers = exchange.getRequestHeaders();
        List<String> authorizationHeader = headers.get("Authorization");

        if (authorizationHeader == null || authorizationHeader.isEmpty()) {
            System.out.println("Authorization header is empty");
            exchange.sendResponseHeaders(401, -1); // 401 Unauthorized
            exchange.close();
            return;
        }

        // Extract  JWT from the Authorization header
        String token = authorizationHeader.get(0).replace("Bearer ", "");

        try {
            // Validate the JWT
            Algorithm algorithm = Algorithm.HMAC256(jwtSecret);
            JWTVerifier verifier = JWT.require(algorithm)
                    .withIssuer("auth0")
                    .build();
            DecodedJWT jwt = verifier.verify(token);
            System.out.println("Authenticated");
            // JWT is valid, proceed with handling the request and returning role
            String username = jwt.getClaim("username").asString();
            Date expiresAt = jwt.getExpiresAt();
            if (expiresAt == null) {
                throw new RuntimeException("JWT does not have an expiration time");
            }
            long ttlMillis = expiresAt.getTime() - System.currentTimeMillis();
            String response = "Authenticated" + "/" + username;
            // write to jwt cache
            int ttl = (int) (ttlMillis / 1000); // convert milliseconds to seconds
            jedis.setex(token, ttl, username);
            System.out.println("(token, username) written to Redis master");
            //System.out.println(response);
            //System.out.println("username: " + username);
            exchange.sendResponseHeaders(200, response.getBytes().length);
            //exchange.getResponseHeaders().add("username", username);
            // add username: username to the HTTP exchange response header
            OutputStream outputStream = exchange.getResponseBody();
            outputStream.write(response.getBytes());
            outputStream.close();
        } catch (Exception e) {
            // Invalid token
            String response = "Invalid";
            exchange.sendResponseHeaders(401, -1); // 401 Unauthorized
            OutputStream outputStream = exchange.getResponseBody();
            outputStream.write(response.getBytes());
            outputStream.close();
            System.out.println("Invalid token");
        }

        exchange.close();
    }

}

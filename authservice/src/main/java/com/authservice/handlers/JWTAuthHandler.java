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

public class JWTAuthHandler implements HttpHandler {

    private Connection dbConnection;
    private final String jwtSecret;

    public JWTAuthHandler(Connection connection) {
        this.dbConnection = connection;
        this.jwtSecret = System.getenv("AUTH_JWT_SECRET");
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
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
                String username = jwt.getSubject();
                exchange.setAttribute("username", username);
                String response = "Authenticated";
                exchange.sendResponseHeaders(200, response.getBytes().length);
                OutputStream outputStream = exchange.getResponseBody();
                outputStream.write(response.getBytes());
                outputStream.close();
            } catch (JWTVerificationException | SQLException exception) {
                // Invalid token
                String response = "Invalid";
                exchange.sendResponseHeaders(401, -1); // 401 Unauthorized
                OutputStream outputStream = exchange.getResponseBody();
                outputStream.write(response.getBytes());
                outputStream.close();
                System.out.println("Invalid token");
            }
        } else {
            exchange.sendResponseHeaders(405, -1); // 405 Method Not Allowed
        }
        exchange.close();
    }

}

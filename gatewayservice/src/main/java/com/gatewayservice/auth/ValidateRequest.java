package com.gatewayservice.server;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.Headers;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.lang.System;
import java.net.HttpURLConnection;
import java.net.URL;

//import io.github.cdimascio.dotenv.Dotenv;

public class ValidateRequest {

    private final String authServiceUrl = System.getenv("AUTH_SERVICE_HOST") + ":" + System.getenv("AUTH_SERVICE_PORT");

    public boolean validateRequest(Headers requestHeaders) throws IOException {
        System.out.println("Routing test validate request to " + authServiceUrl);
        URL url = new URL("http://" + authServiceUrl + "/validate");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; utf-8");

        // Get headers from the HttpExchange request
        //Headers requestHeaders = exchange.getRequestHeaders();
        for (Map.Entry<String, List<String>> header : requestHeaders.entrySet()) {
            for (String value : header.getValue()) {
                conn.setRequestProperty(header.getKey(), value);
            }
        }

        conn.connect();

        // Read and print out the body of the HTTP response
        int responseCode = conn.getResponseCode();
        System.out.println("Response Code: " + responseCode);
        StringBuilder response = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String inputLine;

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }

            // Print out the response body
            System.out.println("Response Body: " + response.toString());
        }

        return "Authenticated".equals(response.toString()); // Validated

    }

}


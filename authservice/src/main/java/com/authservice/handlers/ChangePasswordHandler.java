package com.authservice.server;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class ChangePasswordHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("PUT".equals(exchange.getRequestMethod())) {
            // Read the request body
            InputStream inputStream = exchange.getRequestBody();
            String requestBody = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            System.out.println("Received JSON payload: " + requestBody);

            // Send a response
            String response = "JSON received";
            exchange.sendResponseHeaders(200, response.getBytes().length);
            OutputStream outputStream = exchange.getResponseBody();
            outputStream.write(response.getBytes());
            outputStream.close();
        } else {
            exchange.sendResponseHeaders(405, -1); // 405 Method Not Allowed
        }
    }
}

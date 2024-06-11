package com.authservice.server;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;

public class ChangePasswordHandler implements HttpHandler {
    private Connection connection;

    public ChangePasswordHandler(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            // use rabbitmq, send email with unique token to use to verify/reset password?
        } else {
            exchange.sendResponseHeaders(405, -1); // 405 Method Not Allowed
        }
    }
}

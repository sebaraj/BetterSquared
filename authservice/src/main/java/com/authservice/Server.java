package com.authservice.server;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.stream.Collectors;
import org.json.JSONObject;
import io.github.cdimascio.dotenv.Dotenv;


public class Server {

    private static Connection connection;

    public static void main(String[] args) {
        try {
            // Load the .env file
            Dotenv dotenv = Dotenv.load();
            // connect to DB
            connectToDatabase(dotenv);

            // Create an HttpServer instance, listening on port 8080
            HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

            // Create a context for the "/printJson" endpoint
            server.createContext("/login", new LoginHandler());
            server.createContext("/signup", new SignupHandler());
            server.createContext("/changepassword", new ChangePassHandler());
            server.createContext("/JWTauth", new JWTauthHandler()); // cannot be accessed directly by client. called by gateway for jwt auth

            // Start the server
            server.setExecutor(null); // creates a default executor
            server.start();
            System.out.println("Server started on port 8080");
        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }
    }

    private static void connectToDatabase(Dotenv dotenv) throws SQLException {
        String url = "jdbc:postgresql://localhost:" + dotenv.get("DBPORT") + "/" + dotenv.get("DBNAME");
        String user = dotenv.get("DBUSER");
        String password = dotenv.get("DBPASS");

        connection = DriverManager.getConnection(url, user, password);
        System.out.println("Connected to the PostgreSQL server successfully.");
    }

    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
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

    static class SignupHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                // Read the request body
                InputStream inputStream = exchange.getRequestBody();
                String requestBody = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                System.out.println("Received JSON payload: " + requestBody);

                try {
                    // Parse the JSON payload
                    JSONObject jsonObject = new JSONObject(requestBody);
                    String username = jsonObject.getString("username");
                    String email = jsonObject.getString("email");
                    String password = jsonObject.getString("password");

                    // Insert the sign-up information into the database
                    insertUserIntoDatabase(username, email, password);

                    // Send a response
                    String response = "User signed up successfully";
                    exchange.sendResponseHeaders(200, response.getBytes().length);
                    OutputStream outputStream = exchange.getResponseBody();
                    outputStream.write(response.getBytes());
                    outputStream.close();

                    // future: add message to RabbitMQ so gmail SMTP server can send email notification of new account to email provided
                } catch (Exception e) {
                    e.printStackTrace();
                    String response = "Error processing sign-up request";
                    exchange.sendResponseHeaders(500, response.getBytes().length);
                    OutputStream outputStream = exchange.getResponseBody();
                    outputStream.write(response.getBytes());
                    outputStream.close();
                }
            } else {
                // Method not allowed
                exchange.sendResponseHeaders(405, -1); // 405 Method Not Allowed
            }
        }

        private void insertUserIntoDatabase(String username, String email, String password) throws SQLException {
            String insertSQL = "INSERT INTO users (username, email, password) VALUES (?, ?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(insertSQL)) {
                preparedStatement.setString(1, username);
                preparedStatement.setString(2, email);
                preparedStatement.setString(3, password);
                preparedStatement.executeUpdate();
            }
        }
    }

    static class ChangePassHandler implements HttpHandler {
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

    static class JWTauthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
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
}
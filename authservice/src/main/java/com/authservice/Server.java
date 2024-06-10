package com.authservice.server;

//import com.authservice;
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
import java.lang.Integer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
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

            // Create an HttpServer instance, listening on port HTTP_SERVER_PORT with backlog HTTP_SERVER_BACKLOG
            HttpServer server = HttpServer.create(new InetSocketAddress(Integer.parseInt(dotenv.get("HTTP_SERVER_PORT"))), Integer.parseInt(dotenv.get("HTTP_SERVER_BACKLOG")));

            // Create a context for the endpoints
            server.createContext("/login", new LoginHandler());
            server.createContext("/signup", new SignupHandler());
            server.createContext("/changepassword", new ChangePassHandler());
            server.createContext("/JWTauth", new JWTauthHandler()); // cannot be accessed directly by client. called by gateway for jwt auth

            // New pausable thread pool executor
            PausableThreadPoolExecutor executor = new PausableThreadPoolExecutor(10, 10, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
            int startedThreads = executor.prestartAllCoreThreads();
            System.out.println("Current # of active threads in pool: " + startedThreads);
            // Start the server
            server.setExecutor(executor);
            server.start();
            System.out.println("Server started on port 8080");

            // intercept signal to pause/resume executor (executor.pause() and executor.resume())


            // gracefully shut down server on command/^c
            // executor.shutdown();
            // server.stop();
        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }
    }

    private static void connectToDatabase(Dotenv dotenv) throws SQLException {
        String url = "jdbc:postgresql://"+ dotenv.get("DB_HOST") +":" + dotenv.get("DB_PORT") + "/" + dotenv.get("DB_NAME");
        String user = dotenv.get("DB_USER");
        String password = dotenv.get("DB_PASS");

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

                    // future: add message to RabbitMQ so gmail SMTP microservice can send email notification of new account to email provided
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
package com.emailservice.consumer;

import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.Scanner;
import java.lang.System;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import jakarta.mail.*;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.Address;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

public class Consumer {

    private static com.rabbitmq.client.Connection rabbitMQConnection;
    private static com.rabbitmq.client.Channel rabbitMQChannel;
    private static final String RESET_PASSWORD_QUEUE = "reset_password_email_queue";
    private static final String SIGNUP_EMAIL_QUEUE = "signup_email_queue";

    public static void main(String[] args) {
        try {
            connectToRabbitMQ();
        } catch (IOException | TimeoutException e) { // Handle IOException and TimeoutException
            e.printStackTrace();
        }
    }


    private static void connectToRabbitMQ() throws IOException, TimeoutException {
        System.out.println("Connecting to RabbitMQ...");
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(System.getenv("RABBITMQ_HOST"));
        factory.setPort(Integer.parseInt(System.getenv("RABBITMQ_PORT")));
        rabbitMQConnection = factory.newConnection();
        rabbitMQChannel = rabbitMQConnection.createChannel();
        rabbitMQChannel.queueDeclare(RESET_PASSWORD_QUEUE, true, false, false, null);
        rabbitMQChannel.queueDeclare(SIGNUP_EMAIL_QUEUE, true, false, false, null);
        setupConsumer(rabbitMQChannel, RESET_PASSWORD_QUEUE, "Reset Password");
        setupConsumer(rabbitMQChannel, SIGNUP_EMAIL_QUEUE, "Sign Up Email");
        System.out.println("Connected to RabbitMQ successfully.");
    }

    private static void setupConsumer(Channel channel, String queueName, String consumerName) {
        try {
            // Set up the consumer callback
            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String message = new String(delivery.getBody(), "UTF-8");

                switch(queueName) {
                    case SIGNUP_EMAIL_QUEUE:
                        sendSignUpEmail(message);
                        break;
                    case RESET_PASSWORD_QUEUE:
                        sendResetPasswordEmail(message);
                        break;
                    default:
                        System.out.println("Unknown queue " + queueName);
                        return;
                }
                System.out.println(consumerName + " Received '" + message + "'");
            };

            // Start consuming messages
            channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {});
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void sendSignUpEmail(String message) {
        try {
            // Email details
            System.out.println("Sending email...");
            JSONObject jsonObject = new JSONObject(message);
            String userusername = jsonObject.getString("username");
            String to = jsonObject.getString("email"); // needs to be personal without own domain using maintrap for testing, gmail shutting down access
            String from = System.getenv("SMTP_SERVER_SENDER_EMAIL");
            final String username = System.getenv("SMTP_SERVER_USER");
            final String password = System.getenv("SMTP_SERVER_PASSWORD");
            String host = System.getenv("SMTP_SERVER_HOST");
            //System.out.println("Setting up props...");
            // Configure SMTP
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", host);
            props.put("mail.smtp.port", System.getenv("SMTP_SERVER_PORT"));

            //System.out.println("Connecting to session...");
            //props.put("mail.debug", "true");
            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });
            //System.out.println("Connected to session.");
            Message emailmessage = new MimeMessage(session);
            emailmessage.setFrom(new InternetAddress(from));
            emailmessage.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            emailmessage.setSubject("New account created");
            emailmessage.setText("Hi " + userusername + "! Thanks for signing up");
            //System.out.println("Sending email...");
            Transport.send(emailmessage);

            System.out.println("Sign Up email sent successfully to: " + to);
        } catch (MessagingException me) {
            System.err.println("MessagingException: " + me.getMessage());
            me.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void sendResetPasswordEmail(String message) {
        try {
            // Email details
            System.out.println("Sending email...");
            JSONObject jsonObject = new JSONObject(message);
            String userpassword = jsonObject.getString("password");
            String to = jsonObject.getString("email");
            String from = System.getenv("SMTP_SERVER_SENDER_EMAIL");
            final String username = System.getenv("SMTP_SERVER_USER");
            final String password = System.getenv("SMTP_SERVER_PASSWORD");
            String host = System.getenv("SMTP_SERVER_HOST");
            //System.out.println("Setting up props...");
            // Configure SMTP
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", host);
            props.put("mail.smtp.port", System.getenv("SMTP_SERVER_PORT"));

            //System.out.println("Connecting to session...");
            //props.put("mail.debug", "true");
            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });
            //System.out.println("Connected to session.");
            Message emailmessage = new MimeMessage(session);
            emailmessage.setFrom(new InternetAddress(from));
            emailmessage.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            emailmessage.setSubject("New account created");
            emailmessage.setText("Your password has been reset. Your new password is: " + userpassword);
            //System.out.println("Sending email...");
            Transport.send(emailmessage);

            System.out.println("Reset password email sent successfully to: " + to);
        } catch (MessagingException me) {
            System.err.println("MessagingException: " + me.getMessage());
            me.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
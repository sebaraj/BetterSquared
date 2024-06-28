/***********************************************************************************************************************
 *  File Name:       Consumer.java
 *  Project:         Better2/emailservice
 *  Author:          Bryan SebaRaj
 *  Description:     Reads messages from RabbitMQ and sends emails to client email addresses via external SMTP server
 **********************************************************************************************************************/
package com.better2.emailservice;

import org.json.JSONObject;
import java.io.IOException;
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
    private static final String from = System.getenv("SMTP_SERVER_SENDER_EMAIL");
    private static final String senderusername = System.getenv("SMTP_SERVER_USER");
    private static final String password = System.getenv("SMTP_SERVER_PASSWORD");
    private static final String host = System.getenv("SMTP_SERVER_HOST");

    public static void main(String[] args) {
        try {
            // Connecting to RabbitMQ (using default exchange)
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(System.getenv("RABBITMQ_HOST"));
            factory.setPort(Integer.parseInt(System.getenv("RABBITMQ_PORT")));
            rabbitMQConnection = factory.newConnection();
            rabbitMQChannel = rabbitMQConnection.createChannel();
            System.out.println("Consumer: Established connection to RabbitMQ.");

            // Initializing consumers for two queues
            rabbitMQChannel.queueDeclare(RESET_PASSWORD_QUEUE, true, false, false, null);
            rabbitMQChannel.queueDeclare(SIGNUP_EMAIL_QUEUE, true, false, false, null);
            setupConsumer(rabbitMQChannel, RESET_PASSWORD_QUEUE, "Reset Password");
            setupConsumer(rabbitMQChannel, SIGNUP_EMAIL_QUEUE, "Sign Up Email");
            System.out.println("Consumer: Initialized consumers.");

        } catch (IOException | TimeoutException e) {
            e.printStackTrace();
        }
    }


    private static void setupConsumer(Channel channel, String queueName, String consumerName) {
        try {
            // Setting up consumer callback
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
                        System.out.println("Consumer: Unknown queue: " + queueName);
                        return;
                }
            };
            // Start consuming messages
            channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {});
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void sendSignUpEmail(String message) {
        try {
            // Parsing RabbitMQ message
            JSONObject jsonObject = new JSONObject(message);
            String userusername = jsonObject.getString("username");
            String to = jsonObject.getString("email"); // needs to be personal without own domain using maintrap for testing, gmail shutting down access

            // Configuring SMTP connection
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", host);
            props.put("mail.smtp.port", System.getenv("SMTP_SERVER_PORT"));

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(senderusername, password);
                }
            });
            System.out.println("Consumer: Connected to SMTP server");

            // Sending SMTP message
            Message emailmessage = new MimeMessage(session);
            emailmessage.setFrom(new InternetAddress(from));
            emailmessage.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            emailmessage.setSubject("New account created");
            emailmessage.setText("Hi " + userusername + "! Thanks for signing up");
            Transport.send(emailmessage);
            System.out.println("Consumer: Sign-up email sent successfully to: " + to);
        } catch (Exception e) {
            System.err.println("Exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void sendResetPasswordEmail(String message) {
        try {
            // Parsing RabbitMQ message
            JSONObject jsonObject = new JSONObject(message);
            String userpassword = jsonObject.getString("password");
            String to = jsonObject.getString("email");

            // Configuring SMTP connection
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", host);
            props.put("mail.smtp.port", System.getenv("SMTP_SERVER_PORT"));

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(senderusername, password);
                }
            });
            System.out.println("Consumer: Connected to SMTP server");

            // Sending SMTP message
            Message emailmessage = new MimeMessage(session);
            emailmessage.setFrom(new InternetAddress(from));
            emailmessage.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            emailmessage.setSubject("New account created");
            emailmessage.setText("Your password has been reset. Your new password is: " + userpassword);
            Transport.send(emailmessage);

            System.out.println("Consumer: Reset password email sent successfully to: " + to);
        } catch (Exception e) {
            System.err.println("Exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
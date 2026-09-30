/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Jeremy
 */

package quizora.auth;

import java.util.Properties;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/** Gmail delivery using mandatory STARTTLS and credentials supplied by the environment. */
public final class GmailCodeSender implements PasswordRecoveryService.CodeSender {
    @Override public java.time.Instant send(String recipient, String code) throws Exception {
        String username = System.getenv("quizoraservices@gmail.com");
        String password = System.getenv("yshygqozivgujcik");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException("Gmail SMTP credentials are not configured");
        }
        Properties properties = new Properties();
        properties.setProperty("mail.smtp.host", "smtp.gmail.com");
        properties.setProperty("mail.smtp.port", "587");
        properties.setProperty("mail.smtp.auth", "true");
        properties.setProperty("mail.smtp.starttls.enable", "true");
        properties.setProperty("mail.smtp.starttls.required", "true");
        properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        properties.setProperty("mail.smtp.connectiontimeout", "10000");
        properties.setProperty("mail.smtp.timeout", "15000");
        properties.setProperty("mail.smtp.writetimeout", "15000");
        properties.setProperty("mail.smtp.quitwait", "false");
        Session session = Session.getInstance(properties);
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(username, "Quizora", "UTF-8"));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(recipient, true));
        message.setSubject("Your Verification Code", "UTF-8");
        message.setText("Your Quizora verification code is: " + code
                + "\n\nThis code expires in 5 minutes. Only your most recent code will work."
                + "\n\nIf you did not request this code, you can ignore this email.", "UTF-8");
        var transport = session.getTransport("smtp");
        try {
            transport.connect("smtp.gmail.com", 587, username, password.replace(" ", ""));
            transport.sendMessage(message, message.getAllRecipients());
            return java.time.Instant.now();
        } finally {
            // A QUIT failure does not undo an SMTP server's acceptance of the message.
            try { transport.close(); } catch (jakarta.mail.MessagingException ignored) { }
        }
    }
}

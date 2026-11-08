package dev.beacon.infrastructure.mail;

import dev.beacon.application.digest.DigestException;
import dev.beacon.application.digest.Mailer;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Sends mail through the SMTP server in {@code beacon.mail.*}: any provider's SMTP works,
 * such as a Gmail app password, Resend or Mailgun. With no host set, nothing is sent and
 * the settings page says email isn't set up.
 */
@Component
public class SmtpMailer implements Mailer {

    private final JavaMailSenderImpl sender;
    private final String from;

    public SmtpMailer(@Value("${beacon.mail.host:}") String host,
                      @Value("${beacon.mail.port:587}") int port,
                      @Value("${beacon.mail.username:}") String username,
                      @Value("${beacon.mail.password:}") String password,
                      @Value("${beacon.mail.from:}") String from) {
        this.from = from;
        if (host.isBlank() || from.isBlank()) {
            this.sender = null;
            return;
        }
        var s = new JavaMailSenderImpl();
        s.setHost(host);
        s.setPort(port);
        s.setDefaultEncoding(StandardCharsets.UTF_8.name());
        if (!username.isBlank()) {
            s.setUsername(username);
            s.setPassword(password);
        }
        var props = new Properties();
        props.put("mail.smtp.auth", String.valueOf(!username.isBlank()));
        // 587 upgrades to TLS; 465 is TLS from the start.
        props.put("mail.smtp.starttls.enable", String.valueOf(port != 465));
        props.put("mail.smtp.starttls.required", String.valueOf(port == 587));
        props.put("mail.smtp.ssl.enable", String.valueOf(port == 465));
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "20000");
        s.setJavaMailProperties(props);
        this.sender = s;
    }

    @Override
    public boolean isConfigured() {
        return sender != null;
    }

    @Override
    public void send(String to, String subject, String html, String text) {
        if (sender == null) {
            throw new DigestException("Email isn't set up on this Beacon server yet, so nothing can be sent.", false);
        }
        try {
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);
            sender.send(message);
        } catch (MessagingException | MailException ex) {
            throw new DigestException("The mail server didn't accept the email: " + ex.getMessage(), true);
        }
    }
}

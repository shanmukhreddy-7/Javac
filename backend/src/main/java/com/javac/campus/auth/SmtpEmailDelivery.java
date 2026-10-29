package com.javac.campus.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@Profile("!dev")
public class SmtpEmailDelivery implements EmailDelivery {
  private final JavaMailSender mail;
  private final String from;

  public SmtpEmailDelivery(JavaMailSender mail, @Value("${app.mail-from}") String from) {
    this.mail = mail;
    this.from = from;
  }

  public void send(String recipient, String subject, String link) {
    var message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(recipient);
    message.setSubject("JAVAC — " + subject);
    message.setText(
        subject + "\n\n" + link + "\n\nIf you did not request this, you can ignore this email.");
    mail.send(message);
  }
}

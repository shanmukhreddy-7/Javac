package com.javac.campus.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Non-development EmailDelivery implementation that sends plain-text messages through configured SMTP.
 * Account ownership codes can separately use Neon Auth through OtpService.
 *
 * @Profile("!dev") makes this the non-development implementation of EmailDelivery. Spring
 * injects the mail transport and configured sender; the interface lets callers remain
 * independent of that transport.
 */
@Component
@Profile("!dev")
public class SmtpEmailDelivery implements EmailDelivery {
  // Spring-configured SMTP transport.
  private final JavaMailSender mail;
  // Configured sender address for outgoing JAVAC messages.
  private final String from;

  /**
   * Constructor injection: Spring supplies the collaborators declared here, so this class can
   * delegate work without constructing its own repositories or services.
   */
  public SmtpEmailDelivery(JavaMailSender mail, @Value("${app.mail-from}") String from) {
    this.mail = mail;
    this.from = from;
  }

  /**
   * Builds a plain-text SMTP message using the configured sender and the supplied
   * recipient/content. JavaMailSender performs actual transport; MailException is translated by
   * the shared API error handler. The parameter named link can contain a code or other message
   * text.
   */
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

package com.javac.campus.auth;

import com.javac.campus.common.ApiException;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Development-only EmailDelivery implementation that writes private local HTML messages.
 * It simulates email delivery and does not send to a real inbox.
 *
 * @Profile("dev") selects this implementation only for local development. Files can contain
 * login/recovery secrets, so this folder is local runtime data rather than an asset folder to
 * publish.
 */
@Component
@Profile("dev")
public class DevelopmentMailbox implements EmailDelivery {
  // Private local output directory for development email previews.
  private final Path folder;

  /**
   * Constructor injection: Spring supplies the collaborators declared here, so this class can
   * delegate work without constructing its own repositories or services.
   */
  public DevelopmentMailbox(@Value("${app.dev-mailbox}") String folder) {
    this.folder = Path.of(folder);
  }

  /**
   * Creates a local HTML email preview, escaping recipient and message content before embedding
   * it. A timestamp plus UUID avoids filename collisions; CREATE_NEW prevents accidental
   * overwrites. POSIX systems restrict file access to its owner. I/O failure becomes a safe 503
   * error.
   */
  public void send(String recipient, String subject, String link) {
    try {
      Files.createDirectories(folder);
      String safeRecipient = org.springframework.web.util.HtmlUtils.htmlEscape(recipient);
      String html =
          "<!doctype html><html lang=\"en\"><meta charset=\"utf-8\"><title>JAVAC development"
              + " mailbox</title><body><h1>Development email — JAVAC</h1><p>To: "
              + safeRecipient
              + "</p><h2>"
              + subject
              + "</h2><p><strong>"
              + org.springframework.web.util.HtmlUtils.htmlEscape(link)
              + "</strong></p><p>Local preview only. Treat this file as a secret; delete it after your"
              + " demonstration.</p></body></html>";
      Path file = folder.resolve(System.currentTimeMillis() + "-" + UUID.randomUUID() + ".html");
      Files.writeString(file, html, StandardOpenOption.CREATE_NEW);
      try {
        Files.setPosixFilePermissions(
            file, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
      } catch (UnsupportedOperationException ignored) {
        /* Windows uses directory ACLs. */
      }
    } catch (IOException e) {
      throw new ApiException(503, "Development mailbox could not be written.");
    }
  }
}

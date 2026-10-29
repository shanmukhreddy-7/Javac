package com.javac.campus.auth;

import com.javac.campus.common.ApiException;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DevelopmentMailbox implements EmailDelivery {
  private final Path folder;

  public DevelopmentMailbox(@Value("${app.dev-mailbox}") String folder) {
    this.folder = Path.of(folder);
  }

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
              + "</h2><p><a href=\""
              + org.springframework.web.util.HtmlUtils.htmlEscape(link)
              + "\">"
              + subject
              + "</a></p><p>Local preview only. Treat this file as a secret; delete it after your"
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

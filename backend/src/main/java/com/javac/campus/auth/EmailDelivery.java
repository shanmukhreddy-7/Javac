package com.javac.campus.auth;

/**
 * Delivery contract shared by SMTP and the development mailbox.
 * The third argument carries message content such as a code or link, despite its parameter name.
 *
 * An example of abstraction and polymorphism: services call one contract, and the active Spring
 * profile chooses its implementation. There is no superclass relationship between the SMTP and
 * development implementations.
 */
public interface EmailDelivery {
  /**
   * Delivers message content to a recipient. Callers depend on this interface so Spring can
   * supply either SmtpEmailDelivery or DevelopmentMailbox according to the active profile; the
   * third parameter can be a URL, code, or explanatory text.
   */
  void send(String recipient, String subject, String link);
}

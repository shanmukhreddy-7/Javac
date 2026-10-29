package com.javac.campus.auth;

public interface EmailDelivery {
  void send(String recipient, String subject, String link);
}

package com.javac.campus.auth;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

public final class TokenGenerator {
  private static final SecureRandom RANDOM = new SecureRandom();

  private TokenGenerator() {}

  public static String create() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public static String hash(String raw) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}

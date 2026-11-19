package com.javac.campus.auth;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/**
 * Creates unpredictable URL-safe link tokens and SHA-256 lookup hashes.
 * Passwords and six-digit codes use the configured PasswordEncoder instead of this helper.
 *
 * Utility class with a private constructor and static operations. SecureRandom creates
 * unpredictable secrets; SHA-256 supplies stable lookup hashes for those random tokens. This
 * does not replace slow hashing for passwords.
 */
public final class TokenGenerator {
  // One reusable cryptographically secure random source for token creation.
  private static final SecureRandom RANDOM = new SecureRandom();

  /**
   * Private constructor prevents instantiation of this holder of static helpers or DTO types.
   */
  private TokenGenerator() {}

  /**
   * Fills 32 bytes with cryptographically secure randomness and encodes them with URL-safe
   * Base64 without padding. The result can travel in a link; its raw value must not be stored
   * in account-token tables.
   */
  public static String create() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * Converts a high-entropy link token into a deterministic 64-character SHA-256 hexadecimal
   * lookup key. This fast hash is for random link secrets; human passwords and six-digit codes
   * instead need the configured slow PasswordEncoder.
   */
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

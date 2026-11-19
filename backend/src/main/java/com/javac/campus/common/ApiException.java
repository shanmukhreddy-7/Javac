package com.javac.campus.common;

/**
 * Expected application failure carrying a safe message and the HTTP status used by ApiErrors.
 *
 * Extends RuntimeException so expected service failures can travel to ApiErrors and ordinarily
 * roll back a service transaction. The chosen message is safe for clients; database/provider
 * internals should not be placed in it.
 */
public class ApiException extends RuntimeException {
  // HTTP status used when ApiErrors translates this expected exception.
  private final int status;

  /**
   * Stores a safe client-facing failure message in RuntimeException and retains its intended
   * HTTP status for the shared error handler.
   */
  public ApiException(int status, String message) {
    super(message);
    this.status = status;
  }

  /**
   * Returns the intended HTTP failure status; ApiErrors combines it with the safe exception
   * message into a JSON response.
   */
  public int getStatus() {
    return status;
  }
}

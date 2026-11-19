package com.javac.campus.common;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mail.MailException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

/**
 * Maps Java exceptions to a consistent HTTP error body with a message and optional field errors.
 * Internal exception details are deliberately kept out of client responses.
 *
 * @RestControllerAdvice applies these handlers across REST controllers. Each @ExceptionHandler
 * selects an exception type; the Error record produces the same JSON structure so the frontend
 * can render failures consistently.
 */
@RestControllerAdvice
public class ApiErrors {
  /**
   * Uniform failure response: safe summary message, per-field validation messages, and
   * response-generation timestamp.
   */
  public record Error(String message, Map<String, String> fields, Instant timestamp) {}

  /**
   * Converts an expected business-rule failure into its chosen HTTP status and a safe message
   * with no field-specific errors.
   */
  @ExceptionHandler(ApiException.class)
  public ResponseEntity<Error> api(ApiException e) {
    return response(e.getStatus(), e.getMessage(), Map.of());
  }

  /**
   * Collects the first Jakarta Validation error for each field into an ordered map. Returns
   * HTTP 400 so forms can display a general message and highlight individual inputs.
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Error> validation(MethodArgumentNotValidException e) {
    Map<String, String> fields = new LinkedHashMap<>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
    return response(400, "Please check the highlighted fields.", fields);
  }

  /**
   * Maps database integrity failures, such as unique-key conflicts, to HTTP 409 without
   * exposing SQL text, table contents, or vendor messages.
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Error> duplicate() {
    return response(
        409,
        "This action conflicts with an existing record. Please refresh and try again.",
        Map.of());
  }

  /**
   * Returns HTTP 400 when Spring cannot convert a URL/query argument to its declared Java type,
   * such as a nonnumeric job ID.
   */
  @ExceptionHandler(
      org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
  public ResponseEntity<Error> badParameter() {
    return response(400, "Invalid request parameter.", Map.of());
  }

  /**
   * Returns HTTP 400 for unreadable JSON or incompatible request data. Parsing internals are
   * deliberately excluded from the response.
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Error> malformed() {
    return response(400, "The request could not be read.", Map.of());
  }

  /**
   * Returns a JSON HTTP 404 for a missing static resource or unmapped endpoint handled by this
   * exception.
   */
  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  public ResponseEntity<Error> notFound() {
    return response(404, "This endpoint does not exist.", Map.of());
  }

  /**
   * Returns HTTP 405 when an endpoint exists but does not accept the requested HTTP method.
   */
  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<Error> method() {
    return response(405, "This HTTP method is not supported.", Map.of());
  }

  /**
   * Converts SMTP transport failures into HTTP 503 so callers receive a useful delivery-
   * unavailable message.
   */
  @ExceptionHandler(MailException.class)
  public ResponseEntity<Error> mail() {
    return response(503, "Email delivery is unavailable. Please try again later.", Map.of());
  }

  /**
   * Final fallback for unhandled exceptions. Returns a generic HTTP 500 and deliberately avoids
   * echoing exception details, request bodies, or private database content.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Error> unexpected(Exception e, HttpServletRequest r) {
    // Never echo request bodies, credentials, or database exception details.
    return response(500, "Something went wrong. Please try again.", Map.of());
  }

  /**
   * Creates the consistent error body with message, field map, and current timestamp, then
   * wraps it in the selected HTTP status.
   */
  private ResponseEntity<Error> response(int code, String message, Map<String, String> fields) {
    return ResponseEntity.status(code).body(new Error(message, fields, Instant.now()));
  }
}

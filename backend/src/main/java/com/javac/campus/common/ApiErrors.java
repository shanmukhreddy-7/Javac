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

@RestControllerAdvice
public class ApiErrors {
  public record Error(String message, Map<String, String> fields, Instant timestamp) {}

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<Error> api(ApiException e) {
    return response(e.getStatus(), e.getMessage(), Map.of());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Error> validation(MethodArgumentNotValidException e) {
    Map<String, String> fields = new LinkedHashMap<>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
    return response(400, "Please check the highlighted fields.", fields);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Error> duplicate() {
    return response(409, "This email or registration number is already registered.", Map.of());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Error> malformed() {
    return response(400, "The request could not be read.", Map.of());
  }

  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  public ResponseEntity<Error> notFound() {
    return response(404, "This endpoint does not exist.", Map.of());
  }

  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<Error> method() {
    return response(405, "This HTTP method is not supported.", Map.of());
  }

  @ExceptionHandler(MailException.class)
  public ResponseEntity<Error> mail() {
    return response(503, "Email delivery is unavailable. Please try again later.", Map.of());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Error> unexpected(Exception e, HttpServletRequest r) {
    // Never echo request bodies, credentials, or database exception details.
    return response(500, "Something went wrong. Please try again.", Map.of());
  }

  private ResponseEntity<Error> response(int code, String message, Map<String, String> fields) {
    return ResponseEntity.status(code).body(new Error(message, fields, Instant.now()));
  }
}

package ru.nexus.controller;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class Errors {

  private ResponseEntity<?> response(int status, String message) {
    return ResponseEntity.status(status).body(
      Map.of(
        "status",
        status,
        "message",
        message,
        "timestamp",
        java.time.Instant.now().toString()
      )
    );
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<?> expected(ResponseStatusException e) {
    return response(e.getStatusCode().value(), e.getReason());
  }

  @ExceptionHandler({
    org.springframework.web.bind.MethodArgumentNotValidException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
  })
  public ResponseEntity<?> invalid(Exception e) {
    return response(
      400,
      "Проверьте поля: email, пароль от 8 до 72 символов, длину сообщения и обязательные значения"
    );
  }

  @ExceptionHandler(
    org.springframework.dao.DataIntegrityViolationException.class
  )
  public ResponseEntity<?> conflict(Exception e) {
    return response(409, "Такая запись уже существует");
  }
}

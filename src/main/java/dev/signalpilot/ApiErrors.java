package dev.signalpilot;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String, String>> known(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode())
        .body(Map.of("error", e.getReason() == null ? "Request rejected" : e.getReason()));
  }

  @ExceptionHandler({
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    IllegalArgumentException.class
  })
  ResponseEntity<Map<String, String>> invalid(Exception e) {
    return ResponseEntity.badRequest()
        .body(Map.of("error", "Invalid request. Check required fields and value types."));
  }
}

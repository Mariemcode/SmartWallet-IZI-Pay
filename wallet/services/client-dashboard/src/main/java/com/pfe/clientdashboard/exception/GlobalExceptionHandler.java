package com.pfe.clientdashboard.exception;

import com.pfe.clientdashboard.recommendation.dtos.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(errorBody("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorBody("BAD_REQUEST", ex.getMessage()));
    }

    // FastAPI indisponible
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(errorBody("SERVICE_UNAVAILABLE",
                        e.getMessage() != null ? e.getMessage() : "Service IA indisponible"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody("INTERNAL_ERROR", ex.getMessage()));
    }

    private Map<String, Object> errorBody(String error, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);
        body.put("message", message);
        body.put("timestamp", LocalDateTime.now().toString());
        return body;
    }



    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Validation : " + errors));
    }


}
/*
 * ══════════════════════════════════════════════════════════════
 *  ROUTE À AJOUTER DANS TON API GATEWAY
 *
 *  Dans le application.yml de api-gateway, ajoute :
 *
 *  spring:
 *    cloud:
 *      gateway:
 *        routes:
 *          # ... tes routes existantes ...
 *
 *          - id: ai-service
 *            uri: lb://ai-service          # lb:// = Eureka load balancing
 *            predicates:
 *              - Path=/api/ia/**
 *            filters:
 *              - StripPrefix=0
 *
 *  Après ça :
 *    Flutter appelle : GET http://gateway:8080/api/ia/predictions/{clientId}
 *    Gateway forward : GET http://ai-service:8091/api/ia/predictions/{clientId}
 *    JWT déjà validé par le Gateway avant d'arriver ici ✅
 * ══════════════════════════════════════════════════════════════ */



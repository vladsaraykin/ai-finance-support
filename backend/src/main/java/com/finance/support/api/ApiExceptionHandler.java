package com.finance.support.api;

import com.finance.support.application.TransactionNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.format.DateTimeParseException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, DateTimeParseException.class})
    public ResponseEntity<ApiError> badRequest(RuntimeException exception) {
        return ResponseEntity.badRequest().body(new ApiError(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst().map(error -> error.getField() + ": некорректное значение")
                .orElse("Некорректный запрос.");
        return ResponseEntity.badRequest().body(new ApiError(message));
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ApiError> notFound(TransactionNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("Запись не найдена."));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> serviceUnavailable(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiError(exception.getMessage()));
    }

    public record ApiError(String message) {}
}

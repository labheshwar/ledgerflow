package com.ledgerflow.web;

import com.ledgerflow.exception.AccountNotFoundException;
import com.ledgerflow.exception.UnbalancedTransactionException;
import com.ledgerflow.web.dto.ErrorResponse;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UnbalancedTransactionException.class)
    public ResponseEntity<ErrorResponse> handleUnbalanced(UnbalancedTransactionException e) {
        return error(HttpStatus.BAD_REQUEST, "UNBALANCED_TRANSACTION", e.getMessage());
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoSuchElementException e) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(SortWhitelist.InvalidSortException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSort(SortWhitelist.InvalidSortException e) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_SORT", e.getMessage());
    }

    /** An unparseable enum in a query parameter, e.g. ?type=NOPE. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_PARAMETER",
                "Invalid value for parameter '" + e.getName() + "'");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException e) {
        return error(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.value(), status.getReasonPhrase(), code, message));
    }
}

package com.stech.schat.exception;

import com.stech.schat.dto.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<String> messages = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .toList();

        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "VALIDATION_FAILED", messages));
    }

    @ExceptionHandler(DuplicateUserException.class)
    public ResponseEntity<ApiError> handleDuplicateUser(DuplicateUserException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "USER_ALREADY_EXISTS", ex.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(
                        401,
                        "INVALID_CREDENTIALS",
                        "Incorrect username/email or password"
                ));
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ApiError> handleLocked(AccountLockedException ex) {
        return ResponseEntity.status(HttpStatus.LOCKED)
                .body(ApiError.of(423, "ACCOUNT_LOCKED", ex.getMessage()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiError> handleRateLimit(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiError.of(429, "RATE_LIMITED", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(
                        400,
                        "BAD_REQUEST",
                        ex.getMessage() == null ? "Invalid request" : ex.getMessage()
                ));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenActionException.class)
    public ResponseEntity<ApiError> handleForbidden(ForbiddenActionException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(403, "FORBIDDEN", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleServiceUnavailable(IllegalStateException ex) {
        // Used for "temporarily unavailable" style failures (e.g. the AI provider is
        // unreachable or not configured) — distinct from a bad request from the client.
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of(503, "SERVICE_UNAVAILABLE",
                        ex.getMessage() == null ? "Service temporarily unavailable" : ex.getMessage()));
    }

    @ExceptionHandler({
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            jakarta.validation.ConstraintViolationException.class})
    public ResponseEntity<ApiError> handleMalformedRequest(Exception ex) {
        return ResponseEntity.badRequest().body(ApiError.of(400, "BAD_REQUEST", "Invalid request"));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleTooLarge(Exception ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiError.of(413, "PAYLOAD_TOO_LARGE", "That file is too large (50 MB maximum)."));
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(Exception ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiError.of(405, "METHOD_NOT_ALLOWED", "Method not allowed"));
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMedia(Exception ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiError.of(415, "UNSUPPORTED_MEDIA_TYPE", "Unsupported content type"));
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(Exception ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(404, "NOT_FOUND", "Not found"));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(Exception ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.of(403, "FORBIDDEN", "You don't have access to this."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        if (ReadOnlyModeDetector.isReadOnlyViolation(ex)) {
            log.warn("Write rejected: fallback database is read-only.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiError.of(503, "READ_ONLY_MODE", ReadOnlyModeDetector.USER_MESSAGE));
        }

        // Log the complete exception and stack trace in Render logs
        log.error("Unhandled exception occurred:", ex);

        // Keep internal details hidden from the frontend
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(
                        500,
                        "INTERNAL_ERROR",
                        "Something went wrong. Please try again."
                ));
    }
}
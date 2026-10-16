
package com.example.recruitment.exception;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** One place that turns exceptions into clean HTTP error responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({
            ResourceNotFoundException.class,
            NoResourceFoundException.class
    })
    public ResponseEntity<ApiError> notFound(
            Exception ex,
            HttpServletRequest request) {

        String msg = ex instanceof ResourceNotFoundException
                ? ex.getMessage()
                : "Resource not found";

        log.warn(
                "HTTP 404: method={} uri={} exception={} message={}",
                request.getMethod(),
                request.getRequestURI(),
                ex.getClass().getSimpleName(),
                ex.getMessage(),
                ex
        );

        return build(HttpStatus.NOT_FOUND, msg, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(
            MethodArgumentNotValidException ex) {

        Map<String, String> fields = new LinkedHashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(
                fe -> fields.putIfAbsent(
                        fe.getField(),
                        fe.getDefaultMessage()
                )
        );

        return build(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                fields
        );
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestPartException.class
    })
    public ResponseEntity<ApiError> badRequest(Exception ex) {

        String msg = ex instanceof IllegalArgumentException
                ? ex.getMessage()
                : "Invalid request";

        return build(HttpStatus.BAD_REQUEST, msg, null);
    }

    /** Permission errors return HTTP 403. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> forbidden(
            AccessDeniedException ex) {

        return build(
                HttpStatus.FORBIDDEN,
                ex.getMessage(),
                null
        );
    }

    /** The client portal is frozen. */
    @ExceptionHandler(PortalFrozenException.class)
    public ResponseEntity<ApiError> frozen(
            PortalFrozenException ex) {

        return build(
                HttpStatus.LOCKED,
                ex.getMessage(),
                null
        );
    }

    /** Handles simultaneous candidate updates. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> conflict(
            OptimisticLockingFailureException ex) {

        return build(
                HttpStatus.CONFLICT,
                "This candidate was just changed by someone else. Please refresh and try again",
                null
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> tooLarge(
            MaxUploadSizeExceededException ex) {

        return build(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "File is too large",
                null
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> methodNotAllowed(Exception ex) {

        log.warn("HTTP method not supported: {}", ex.getMessage());

        return build(
                HttpStatus.METHOD_NOT_ALLOWED,
                "HTTP method not supported for this endpoint",
                null
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> unsupportedMedia(
            HttpMediaTypeNotSupportedException ex) {

        return build(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Unsupported content type",
                null
        );
    }

    /** Safety net: log unexpected errors without exposing details to the client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex) {

        log.error("Unexpected error", ex);

        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred",
                null
        );
    }

    private ResponseEntity<ApiError> build(
            HttpStatus status,
            String message,
            Map<String, String> fields) {

        return ResponseEntity.status(status)
                .body(new ApiError(
                        Instant.now(),
                        status.value(),
                        status.getReasonPhrase(),
                        message,
                        fields
                ));
    }
}

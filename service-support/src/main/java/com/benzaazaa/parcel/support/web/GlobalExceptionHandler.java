package com.benzaazaa.parcel.support.web;

import com.benzaazaa.parcel.support.correlation.CorrelationId;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every error leaves the services as RFC 9457 {@code application/problem+json}, enriched with the
 * correlation id so a support ticket can be traced across services.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String TYPE_BASE = "https://parcel.benzaazaa.com/problems/";

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Resource not found", ex.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail businessRule(BusinessRuleException ex) {
        ProblemDetail pd = problem(HttpStatus.CONFLICT, ex.code(), "Business rule violated", ex.getMessage());
        pd.setProperty("code", ex.code());
        return pd;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail concurrentUpdate(ObjectOptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "concurrent-update", "Concurrent update",
                "The resource was modified by another request, retry with fresh data");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail accessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", "Forbidden", ex.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "validation", "Validation failed",
                "One or more fields are invalid");
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        pd.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(pd);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(@Nullable Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail pd) {
            enrich(pd);
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create(TYPE_BASE + type));
        pd.setTitle(title);
        enrich(pd);
        return pd;
    }

    private static void enrich(ProblemDetail pd) {
        String correlationId = CorrelationId.current();
        if (correlationId != null) {
            pd.setProperty("correlationId", correlationId);
        }
    }
}

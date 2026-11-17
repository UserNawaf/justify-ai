package com.justify.api;

import com.justify.core.AiUnavailableException;
import com.justify.firewall.FirewallFormValidator;
import com.justify.firewall.FormInvalidException;
import com.justify.service.ConflictException;
import com.justify.service.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Errors as application/problem+json (RFC 9457). Validation errors come from the base class. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException e) {
        return problem(HttpStatus.CONFLICT, "Conflict", e.getMessage());
    }

    /** Bean-validation errors with the same per-field shape as the form errors below. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail p = problem(HttpStatus.BAD_REQUEST, "Invalid input", "Some fields need fixing.");
        p.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FirewallFormValidator.Problem(e.getField(), e.getDefaultMessage()))
                .toList());
        return ResponseEntity.badRequest().contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON).body(p);
    }

    /** Field-level problems of the firewall form, so the UI can show them next to each input. */
    @ExceptionHandler(FormInvalidException.class)
    ProblemDetail formInvalid(FormInvalidException e) {
        ProblemDetail p = problem(HttpStatus.BAD_REQUEST, "Invalid request form", "Some fields need fixing.");
        p.setProperty("errors", e.problems());
        return p;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid input", e.getMessage());
    }

    @ExceptionHandler(AiUnavailableException.class)
    ProblemDetail aiUnavailable(AiUnavailableException e) {
        log.error("Model unavailable", e);
        // No internal detail (hostnames, model errors) in the response.
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Model unavailable",
                "The AI model is not available right now. Please try again in a minute.");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setTitle(title);
        return p;
    }
}

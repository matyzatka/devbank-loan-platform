package com.example.loanplatform.api;

import com.example.loanplatform.application.IdempotencyKeyConflictException;
import com.example.loanplatform.application.LoanApplicationNotFoundException;
import com.example.loanplatform.application.OptimisticLockingConflictException;
import com.example.loanplatform.domain.InvalidLoanApplicationTransitionException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(LoanApplicationNotFoundException.class)
    ProblemDetail handleNotFound(LoanApplicationNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler({
            InvalidLoanApplicationTransitionException.class,
            IdempotencyKeyConflictException.class,
            OptimisticLockingConflictException.class
    })
    ProblemDetail handleConflict(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, "BUSINESS_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleBadRequest(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        var detail = problem(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Request validation failed");
        List<Map<String, String>> violations = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(ApiExceptionHandler::violation)
                .toList();
        detail.setProperty("violations", violations);
        return handleExceptionInternal(exception, detail, headers, status, request);
    }

    private static Map<String, String> violation(FieldError error) {
        return Map.of(
                "field", error.getField(),
                "message", error.getDefaultMessage() == null ? "invalid value" : error.getDefaultMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create("https://loan-platform.example/problems/" + code.toLowerCase()));
        problem.setProperty("code", code);
        return problem;
    }
}


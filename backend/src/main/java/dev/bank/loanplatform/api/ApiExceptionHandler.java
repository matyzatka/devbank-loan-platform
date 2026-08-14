package dev.bank.loanplatform.api;

import dev.bank.loanplatform.application.IdempotencyKeyConflictException;
import dev.bank.loanplatform.application.LoanApplicationNotFoundException;
import dev.bank.loanplatform.application.OptimisticLockingConflictException;
import dev.bank.loanplatform.domain.InvalidLoanApplicationTransitionException;
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
import org.slf4j.MDC;
import dev.bank.loanplatform.configuration.CorrelationIds;
import lombok.extern.slf4j.Slf4j;

/** Maps domain and application failures to stable RFC 9457 responses without exposing internals. */
@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(LoanApplicationNotFoundException.class)
    ProblemDetail handleNotFound(LoanApplicationNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "Požadovaná úvěrová žádost nebyla nalezena.");
    }

    @ExceptionHandler({
            InvalidLoanApplicationTransitionException.class,
            IdempotencyKeyConflictException.class,
            OptimisticLockingConflictException.class
    })
    ProblemDetail handleConflict(RuntimeException exception) {
        log.warn("Business command rejected: exceptionType={}, reason={}",
                exception.getClass().getSimpleName(), exception.getMessage());
        var detail = exception instanceof OptimisticLockingConflictException
                ? "Žádost mezitím změnil jiný uživatel. Obnovte stránku a akci zopakujte."
                : exception instanceof InvalidLoanApplicationTransitionException
                ? "Požadovaná změna není v aktuálním stavu žádosti povolena."
                : "Požadavek nelze dokončit, protože koliduje s dříve zpracovaným požadavkem.";
        return problem(HttpStatus.CONFLICT, "BUSINESS_CONFLICT", detail);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleBadRequest(IllegalArgumentException exception) {
        log.debug("Invalid request rejected: reason={}", exception.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Požadavek obsahuje neplatné údaje.");
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
                "Zkontrolujte označená pole a požadavek odešlete znovu.");
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
                "message", error.getDefaultMessage() == null ? "Neplatná hodnota." : error.getDefaultMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(switch (status) {
            case BAD_REQUEST -> "Neplatný požadavek";
            case NOT_FOUND -> "Žádost nenalezena";
            case CONFLICT -> "Požadavek je v konfliktu";
            default -> "Požadavek se nepodařilo zpracovat";
        });
        problem.setType(URI.create("https://loan-platform.example/problems/" + code.toLowerCase()));
        problem.setProperty("code", code);
        var correlationId = MDC.get(CorrelationIds.MDC_KEY);
        if (correlationId != null) {
            problem.setProperty(CorrelationIds.MDC_KEY, correlationId);
        }
        return problem;
    }
}

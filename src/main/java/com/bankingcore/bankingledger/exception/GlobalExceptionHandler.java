package com.bankingcore.bankingledger.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GlobalExceptionHandler — Centralised RFC-7807 Problem Detail error responses.
 *
 * <p>Every exception surface in the application is caught here and mapped to a
 * consistent {@link ProblemDetail} JSON body, preventing accidental leakage of
 * stack traces, Hibernate internals, or security-sensitive messages.
 *
 * <p>Response shape (RFC 7807):
 * <pre>{@code
 * {
 *   "type":       "https://banking-ledger.io/errors/insufficient-funds",
 *   "title":      "Insufficient Funds",
 *   "status":     422,
 *   "detail":     "Account ACC-001 has insufficient balance for this debit.",
 *   "instance":   "/api/v1/transactions/transfer",
 *   "timestamp":  "2025-01-01T12:00:00Z",
 *   "requestId":  "a1b2c3d4"
 * }
 * }</pre>
 *
 * <p>Logging strategy:
 * <ul>
 *   <li>Client errors (4xx) → {@code WARN} — operator interest, not PagerDuty noise.</li>
 *   <li>Server errors (5xx) → {@code ERROR} with full stack trace.</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // Problem type URI base — replace with your actual domain in production
    private static final String PROBLEM_BASE_URI = "https://banking-ledger.io/errors/";

    // MDC key matching the pattern in application.yml
    private static final String MDC_REQUEST_ID = "requestId";

    // =========================================================================
    // Spring MVC Validation — @Valid / @Validated on request bodies
    // =========================================================================

    /**
     * Handles bean validation failures on {@code @RequestBody} arguments.
     * Returns a structured map of {@code fieldName -> [errorMessage, ...]} pairs.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Map<String, List<String>> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.groupingBy(
                        FieldError::getField,
                        LinkedHashMap::new,
                        Collectors.mapping(FieldError::getDefaultMessage, Collectors.toList())
                ));

        ProblemDetail problem = buildProblem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "validation-error",
                "Request Validation Failed",
                "One or more fields failed validation. See 'fieldErrors' for details.",
                request
        );
        problem.setProperty("fieldErrors", fieldErrors);

        log.warn("[{}] Validation failed: {}",
                getRequestId(request), fieldErrors);

        return ResponseEntity.unprocessableEntity()
                .headers(headers)
                .body(problem);
    }

    // =========================================================================
    // Jakarta Bean Validation — path/query param violations
    // =========================================================================

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException ex, WebRequest request) {

        Map<String, String> violations = ex.getConstraintViolations()
                .stream()
                .collect(Collectors.toMap(
                        cv -> cv.getPropertyPath().toString(),
                        ConstraintViolation::getMessage,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        ProblemDetail problem = buildProblem(
                HttpStatus.BAD_REQUEST,
                "constraint-violation",
                "Constraint Violation",
                "One or more constraints were violated.",
                request
        );
        problem.setProperty("violations", violations);

        log.warn("[{}] Constraint violations: {}",
                getRequestId(request), violations);

        return ResponseEntity.badRequest().body(problem);
    }

    // =========================================================================
    // Domain — Resource Not Found
    // =========================================================================

    @ExceptionHandler({EntityNotFoundException.class, ResourceNotFoundException.class})
    public ResponseEntity<ProblemDetail> handleNotFound(
            RuntimeException ex, WebRequest request) {

        ProblemDetail problem = buildProblem(
                HttpStatus.NOT_FOUND,
                "resource-not-found",
                "Resource Not Found",
                ex.getMessage(),
                request
        );

        log.warn("[{}] Resource not found: {}", getRequestId(request), ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    // =========================================================================
    // Domain — Business Rule Violations
    // =========================================================================

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ProblemDetail> handleInsufficientFunds(
            InsufficientFundsException ex, WebRequest request) {

        ProblemDetail problem = buildProblem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "insufficient-funds",
                "Insufficient Funds",
                ex.getMessage(),
                request
        );

        log.warn("[{}] Insufficient funds: {}", getRequestId(request), ex.getMessage());

        return ResponseEntity.unprocessableEntity().body(problem);
    }

    @ExceptionHandler(DuplicateTransactionException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateTransaction(
            DuplicateTransactionException ex, WebRequest request) {

        ProblemDetail problem = buildProblem(
                HttpStatus.CONFLICT,
                "duplicate-transaction",
                "Duplicate Transaction",
                ex.getMessage(),
                request
        );

        log.warn("[{}] Duplicate transaction: {}", getRequestId(request), ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(InvalidTransactionStateException.class)
    public ResponseEntity<ProblemDetail> handleInvalidTransactionState(
            InvalidTransactionStateException ex, WebRequest request) {

        ProblemDetail problem = buildProblem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "invalid-transaction-state",
                "Invalid Transaction State",
                ex.getMessage(),
                request
        );
        if (ex.getCurrentState() != null) {
            problem.setProperty("currentState", ex.getCurrentState());
        }
        if (ex.getTargetState() != null) {
            problem.setProperty("targetState", ex.getTargetState());
        }

        log.warn("[{}] Invalid state transition: {}", getRequestId(request), ex.getMessage());

        return ResponseEntity.unprocessableEntity().body(problem);
    }

    @ExceptionHandler(AccountBlockedException.class)
    public ResponseEntity<ProblemDetail> handleAccountBlocked(
            AccountBlockedException ex, WebRequest request) {

        ProblemDetail problem = buildProblem(
                HttpStatus.FORBIDDEN,
                "account-blocked",
                "Account Blocked",
                ex.getMessage(),
                request
        );

        log.warn("[{}] Blocked account access: {}", getRequestId(request), ex.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    public ResponseEntity<ProblemDetail> handleCurrencyMismatch(
            CurrencyMismatchException ex, WebRequest request) {

        ProblemDetail problem = buildProblem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "currency-mismatch",
                "Currency Mismatch",
                ex.getMessage(),
                request
        );

        log.warn("[{}] Currency mismatch: {}", getRequestId(request), ex.getMessage());

        return ResponseEntity.unprocessableEntity().body(problem);
    }

    @ExceptionHandler(DistributedLockException.class)
    public ResponseEntity<ProblemDetail> handleDistributedLock(
            DistributedLockException ex, WebRequest request) {

        ProblemDetail problem = buildProblem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "lock-acquisition-failed",
                "Resource Temporarily Unavailable",
                "The requested operation could not acquire a distributed lock. "
                        + "The resource may be in use. Please retry after a moment.",
                request
        );
        // Surface retry-after hint
        problem.setProperty("retryAfterSeconds", 5);

        log.warn("[{}] Lock acquisition failed: {}", getRequestId(request), ex.getMessage());

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }

    // =========================================================================
    // Spring Security — Authentication & Authorisation
    // =========================================================================

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleBadCredentials(
            BadCredentialsException ex, WebRequest request) {

        // Deliberately vague message — do not confirm whether user exists
        ProblemDetail problem = buildProblem(
                HttpStatus.UNAUTHORIZED,
                "authentication-failed",
                "Authentication Failed",
                "Invalid credentials. Please verify your username and password.",
                request
        );

        log.warn("[{}] Bad credentials for request: {}", getRequestId(request),
                request.getDescription(false));

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(
            AuthenticationException ex, WebRequest request) {

        ProblemDetail problem = buildProblem(
                HttpStatus.UNAUTHORIZED,
                "authentication-required",
                "Authentication Required",
                "You must be authenticated to access this resource.",
                request
        );

        log.warn("[{}] Authentication required: {}", getRequestId(request), ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(
            AccessDeniedException ex, WebRequest request) {

        ProblemDetail problem = buildProblem(
                HttpStatus.FORBIDDEN,
                "access-denied",
                "Access Denied",
                "You do not have permission to perform this action.",
                request
        );

        log.warn("[{}] Access denied: {}", getRequestId(request), ex.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ProblemDetail> handleDisabledAccount(
            DisabledException ex, WebRequest request) {

        ProblemDetail problem = buildProblem(
                HttpStatus.FORBIDDEN,
                "account-disabled",
                "Account Disabled",
                "Your account has been disabled. Please contact support.",
                request
        );

        log.warn("[{}] Disabled account login attempt", getRequestId(request));

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ProblemDetail> handleLockedAccount(
            LockedException ex, WebRequest request) {

        ProblemDetail problem = buildProblem(
                HttpStatus.FORBIDDEN,
                "account-locked",
                "Account Locked",
                "Your account has been temporarily locked due to repeated failed attempts.",
                request
        );

        log.warn("[{}] Locked account login attempt", getRequestId(request));

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    // =========================================================================
    // JPA / Database — Concurrency & Integrity
    // =========================================================================

    /**
     * Optimistic lock conflict — two concurrent writes to the same entity row.
     * The caller should retry the operation with fresh data (HTTP 409 Conflict).
     */
    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(
            OptimisticLockException ex, WebRequest request) {

        ProblemDetail problem = buildProblem(
                HttpStatus.CONFLICT,
                "optimistic-lock-conflict",
                "Concurrent Modification Conflict",
                "The resource was modified by another request. Please reload and retry.",
                request
        );

        log.warn("[{}] Optimistic lock conflict on entity: {}",
                getRequestId(request), ex.getEntity());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    /**
     * Database constraint violation — e.g. unique index, FK integrity.
     * Intentionally strips the underlying SQL message to avoid leaking schema info.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, WebRequest request) {

        ProblemDetail problem = buildProblem(
                HttpStatus.CONFLICT,
                "data-integrity-violation",
                "Data Integrity Violation",
                "The request conflicts with existing data. "
                        + "A unique constraint or foreign key constraint was violated.",
                request
        );

        // Log full cause for ops; never expose to client
        log.warn("[{}] Data integrity violation: {}",
                getRequestId(request), ex.getMostSpecificCause().getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    // =========================================================================
    // Catch-all — 500 Internal Server Error
    // =========================================================================

    /**
     * Safety net for any unhandled exception. Logs the full stack trace and
     * returns a generic 500 without leaking internal details.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleAll(Exception ex, WebRequest request) {

        ProblemDetail problem = buildProblem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal-server-error",
                "Internal Server Error",
                "An unexpected error occurred. Our team has been notified.",
                request
        );

        log.error("[{}] Unhandled exception on {}: ",
                getRequestId(request), request.getDescription(false), ex);

        return ResponseEntity.internalServerError().body(problem);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Builds an RFC-7807 {@link ProblemDetail} with standard banking-specific
     * extension properties ({@code timestamp}, {@code requestId}).
     */
    private ProblemDetail buildProblem(
            HttpStatus status,
            String errorSlug,
            String title,
            String detail,
            WebRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(PROBLEM_BASE_URI + errorSlug));
        problem.setTitle(title);
        problem.setInstance(URI.create(
                request.getDescription(false).replace("uri=", "")));
        problem.setProperty("timestamp", Instant.now().toString());
        problem.setProperty("requestId", getRequestId(request));
        return problem;
    }

    /**
     * Extracts the request ID from the MDC (injected in Phase 6) or falls back
     * to "n/a" so the field is always present in the JSON body.
     */
    private String getRequestId(WebRequest request) {
        String id = org.slf4j.MDC.get(MDC_REQUEST_ID);
        return (id != null && !id.isBlank()) ? id : "n/a";
    }
}
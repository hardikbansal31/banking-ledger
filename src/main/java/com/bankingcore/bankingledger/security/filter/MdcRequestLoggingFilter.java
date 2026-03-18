package com.bankingcore.bankingledger.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * MdcRequestLoggingFilter — injects tracing context into the SLF4J MDC.
 *
 * Runs FIRST (Order.HIGHEST_PRECEDENCE) so every subsequent filter,
 * service, and repository log line carries the same requestId.
 *
 * MDC keys injected:
 *   requestId  : UUID per HTTP request — correlates all log lines for one call
 *   userId     : authenticated username (or "anonymous") — set after JWT filter
 *   httpMethod : GET / POST / etc.
 *   requestUri : /api/v1/accounts/...
 *
 * MDC is thread-local — cleared in a finally block to prevent leakage
 * in thread-pool environments (Tomcat reuses threads).
 *
 * These keys match the logging pattern in application.yml:
 *   %X{requestId}  and  %X{userId}
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcRequestLoggingFilter extends OncePerRequestFilter {

    private static final String MDC_REQUEST_ID  = "requestId";
    private static final String MDC_USER_ID     = "userId";
    private static final String MDC_HTTP_METHOD = "httpMethod";
    private static final String MDC_REQUEST_URI = "requestUri";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        long startTime = System.currentTimeMillis();

        try {
            // ── Inject request-scoped MDC values ──────────────────────────────
            String requestId = resolveRequestId(request);
            MDC.put(MDC_REQUEST_ID,  requestId);
            MDC.put(MDC_HTTP_METHOD, request.getMethod());
            MDC.put(MDC_REQUEST_URI, request.getRequestURI());

            // userId is "anonymous" here; updated after JWT filter sets the principal
            MDC.put(MDC_USER_ID, resolveUserId());

            // Propagate requestId as a response header — useful for client-side debugging
            response.setHeader("X-Request-Id", requestId);

            log.info(">> {} {} [requestId={}]",
                    request.getMethod(), request.getRequestURI(), requestId);

            filterChain.doFilter(request, response);

            // Update userId MDC after JWT filter has run (SecurityContext now populated)
            MDC.put(MDC_USER_ID, resolveUserId());

            long durationMs = System.currentTimeMillis() - startTime;
            log.info("<< {} {} → {} [{}ms] [requestId={}]",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMs,
                    requestId);

        } finally {
            // CRITICAL: always clear MDC to prevent thread-local leaks in Tomcat
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_USER_ID);
            MDC.remove(MDC_HTTP_METHOD);
            MDC.remove(MDC_REQUEST_URI);
        }
    }

    /**
     * Uses the X-Request-Id header if provided by a gateway/load balancer,
     * otherwise generates a fresh UUID. This enables end-to-end trace correlation
     * when placed behind an API Gateway (Phase 6).
     */
    private String resolveRequestId(HttpServletRequest request) {
        String incoming = request.getHeader("X-Request-Id");
        return (incoming != null && !incoming.isBlank())
                ? incoming
                : UUID.randomUUID().toString();
    }

    /**
     * Reads the authenticated principal from the SecurityContext.
     * Returns "anonymous" before JWT filter has run or on public endpoints.
     */
    private String resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return "anonymous";
    }
}
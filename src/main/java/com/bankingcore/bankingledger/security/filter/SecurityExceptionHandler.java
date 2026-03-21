package com.bankingcore.bankingledger.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityExceptionHandler
        implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    /** Called when request has NO valid credentials → 401 */
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        log.warn("401 Unauthorized — {} {} | {}",
                request.getMethod(), request.getRequestURI(), authException.getMessage());

        writeProblem(response, HttpStatus.UNAUTHORIZED,
                "authentication-required",
                "Authentication Required",
                "A valid Bearer token is required to access this resource.",
                request.getRequestURI());
    }

    /** Called when request IS authenticated but role is insufficient → 403 */
    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {

        log.warn("403 Forbidden — {} {} | insufficient role",
                request.getMethod(), request.getRequestURI());

        writeProblem(response, HttpStatus.FORBIDDEN,
                "access-denied",
                "Access Denied",
                "You do not have the required role to access this resource.",
                request.getRequestURI());
    }

    private void writeProblem(HttpServletResponse response,
                              HttpStatus status,
                              String slug,
                              String title,
                              String detail,
                              String instance) throws IOException {

        // Guard: if response already committed, writing will silently fail
        if (response.isCommitted()) {
            log.warn("Response already committed — cannot write {} error body", status.value());
            return;
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://banking-ledger.io/errors/" + slug));
        problem.setTitle(title);
        problem.setInstance(URI.create(instance));
        problem.setProperty("timestamp", Instant.now().toString());

        String requestId = org.slf4j.MDC.get("requestId");
        problem.setProperty("requestId", requestId != null ? requestId : "n/a");

        // Set headers BEFORE writing body — order matters in Servlet API
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        // Write and explicitly flush — without flush() some containers
        // buffer the output and the client receives an empty body
        PrintWriter writer = response.getWriter();
        objectMapper.writeValue(writer, problem);
        writer.flush();
    }
}
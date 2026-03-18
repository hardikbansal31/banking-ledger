package com.bankingcore.bankingledger.security.filter;

import com.bankingcore.bankingledger.security.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JwtAuthenticationFilter — intercepts every HTTP request and validates the JWT.
 *
 * Execution flow per request:
 *  1. Extract "Authorization: Bearer <token>" header
 *  2. If missing → skip (Spring Security will reject if the endpoint requires auth)
 *  3. Extract username from token claims (no DB call yet)
 *  4. If SecurityContext already has an authenticated principal → skip (already done)
 *  5. Load UserDetails from DB (one SELECT per request)
 *  6. Validate token signature + expiry + subject match
 *  7. If valid → set Authentication in SecurityContext
 *  8. Always continue the filter chain — never short-circuit here
 *
 * Why OncePerRequestFilter?
 *   Guarantees this filter runs exactly once per request, even in async
 *   dispatch scenarios (Spring's RequestDispatcher can invoke filters twice).
 *
 * Why not throw exceptions here?
 *   Throwing inside a filter bypasses @RestControllerAdvice. We let the
 *   request proceed unauthenticated — Spring Security's ExceptionTranslationFilter
 *   then produces the 401 through the AuthenticationEntryPoint.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        // ── Step 1: No token → pass through, let Security handle the rejection ──
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            log.trace("No Bearer token on request: {} {}", request.getMethod(), request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(BEARER_PREFIX.length());
        String username = null;

        // ── Step 2: Extract username (validates signature implicitly) ──────────
        try {
            username = jwtService.extractUsername(jwt);
        } catch (Exception e) {
            // extractUsername logs the specific failure; we just proceed unauthenticated
            log.debug("Could not extract username from JWT on {} {}: {}",
                    request.getMethod(), request.getRequestURI(), e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        // ── Step 3: Authenticate if not already in SecurityContext ─────────────
        if (username != null &&
                SecurityContextHolder.getContext().getAuthentication() == null) {

            UserDetails userDetails = null;
            try {
                userDetails = userDetailsService.loadUserByUsername(username);
            } catch (Exception e) {
                log.warn("Failed to load UserDetails for '{}': {}", username, e.getMessage());
                filterChain.doFilter(request, response);
                return;
            }

            if (jwtService.isTokenValid(jwt, userDetails)) {
                // Build an authenticated token with authorities (roles)
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,                          // credentials null = already verified
                                userDetails.getAuthorities()
                        );
                // Attaches request metadata (IP, session) to the Authentication
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Store in SecurityContext — Spring Security reads this on every downstream call
                SecurityContextHolder.getContext().setAuthentication(authToken);

                log.debug("Authenticated user '{}' with roles {} on {} {}",
                        username,
                        userDetails.getAuthorities(),
                        request.getMethod(),
                        request.getRequestURI());
            } else {
                log.warn("Invalid JWT for user '{}' on {} {}",
                        username, request.getMethod(), request.getRequestURI());
            }
        }

        // ── Step 4: Always continue — never block in this filter ──────────────
        filterChain.doFilter(request, response);
    }
}
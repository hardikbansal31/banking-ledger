package com.bankingcore.bankingledger.config;

import com.bankingcore.bankingledger.security.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * SecurityConfig — defines the Spring Security filter chain.
 *
 * Key decisions explained:
 *
 * 1. STATELESS session — no HttpSession is created. Every request must carry
 *    a valid JWT. This is the correct model for a REST API consumed by React.
 *
 * 2. CSRF disabled — CSRF attacks require a browser session cookie. Since we
 *    use JWT in the Authorization header (not a cookie), CSRF is not a threat.
 *    If you ever switch to cookie-based auth, re-enable CSRF.
 *
 * 3. @EnableMethodSecurity — enables @PreAuthorize("hasRole('ADMIN')") on
 *    individual service methods, giving fine-grained RBAC beyond URL patterns.
 *
 * 4. AuthenticationProvider bean — wires our UserDetailsService and BCrypt
 *    encoder into Spring Security's authentication pipeline.
 *
 * 5. CORS — driven entirely by the CORS_ALLOWED_ORIGINS environment variable.
 *    No origin is hardcoded.
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity               // enables @PreAuthorize at service/method level
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    @Value("${app.security.cors.allowed-origins}")
    private String allowedOriginsRaw;

    // ── Filter Chain ──────────────────────────────────────────────────────────

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Spring Security filter chain");

        http
                // ── CSRF: off for stateless JWT API ──────────────────────────────
                .csrf(AbstractHttpConfigurer::disable)

                // ── CORS: env-variable-driven ─────────────────────────────────────
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // ── URL authorisation rules ───────────────────────────────────────
                .authorizeHttpRequests(auth -> auth

                        // Public endpoints — no token required
                        .requestMatchers(HttpMethod.POST,
                                "/auth/register",
                                "/auth/login",
                                "/auth/refresh"
                        ).permitAll()

                        // Actuator health/info — open for load balancer health checks
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()

                        // Actuator sensitive endpoints — ADMIN only
                        .requestMatchers("/actuator/**").hasRole("ADMIN")

                        // Admin-only management endpoints
                        .requestMatchers(HttpMethod.GET,  "/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT,  "/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/admin/**").hasRole("ADMIN")

                        // Everything else requires a valid JWT (any authenticated user)
                        .anyRequest().authenticated()
                )

                // ── Session: stateless — no HttpSession ───────────────────────────
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // ── Authentication provider ───────────────────────────────────────
                .authenticationProvider(authenticationProvider())

                // ── JWT filter before username/password filter ────────────────────
                // This is the critical ordering: JWT filter runs BEFORE Spring's
                // default UsernamePasswordAuthenticationFilter so it can set the
                // Authentication in SecurityContext before any auth check happens.
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ── CORS Configuration ────────────────────────────────────────────────────

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Parse comma-separated origins from env variable
        List<String> origins = Arrays.asList(allowedOriginsRaw.split(","));
        origins.replaceAll(String::trim);
        config.setAllowedOrigins(origins);
        log.info("CORS allowed origins: {}", origins);

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
        config.setExposedHeaders(List.of("X-Request-Id", "Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);    // preflight cache: 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // ── Authentication Beans ─────────────────────────────────────────────────

    /**
     * DaoAuthenticationProvider wires our UserDetailsService (DB lookup) and
     * BCryptPasswordEncoder (hash comparison) into Spring's auth pipeline.
     * Spring calls this during the /auth/login flow.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * AuthenticationManager is needed by AuthService to trigger the full
     * authentication pipeline (loads user, checks password, checks enabled/locked).
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * BCrypt with strength 12 — the industry standard for password hashing.
     * Strength 10 = ~100ms per hash. 12 = ~400ms. 14 = ~1.5s.
     * 12 balances security vs. login latency. Never go below 10.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
package com.bankingcore.bankingledger.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * ActuatorSecurityConfig — HTTP Basic auth for /actuator/** endpoints.
 *
 * @Order(1) means this chain is evaluated before the main JWT chain (@Order(2)).
 *
 * The InMemoryUserDetailsManager is NOT exposed as a @Bean — it is wired
 * directly into the filter chain via .userDetailsService(). This prevents
 * Spring from registering it as a UserDetailsService bean, which would
 * conflict with UserDetailsServiceImpl and cause an ambiguous bean error.
 */
@Configuration
@Order(1)
public class ActuatorSecurityConfig {

    @Value("${management.actuator.username:actuator_admin}")
    private String actuatorUsername;

    @Value("${management.actuator.password:ActuatorDev@55}")
    private String actuatorPassword;

    @Bean
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {

        // Build the in-memory user HERE — not as a @Bean — to avoid polluting
        // the UserDetailsService bean pool
        UserDetails actuatorUser = User.builder()
                .username(actuatorUsername)
                .password(new BCryptPasswordEncoder(12).encode(actuatorPassword))
                .roles("ACTUATOR")
                .build();
        InMemoryUserDetailsManager actuatorUsers = new InMemoryUserDetailsManager(actuatorUser);

        http
                .securityMatcher("/actuator/**")
                .userDetailsService(actuatorUsers)   // scoped to this chain only
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(s ->
                        s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable());

        return http.build();
    }
}
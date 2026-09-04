package com.fares.demo1.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Base auth layer: which GET under {@code /api/**} needs a login is decided per-request
 * by {@link MetricAuthorizationManager} (see that class for why it isn't a fixed rule
 * here); anything that changes state (POST/PUT/DELETE/PATCH) needs the {@code ADMIN}
 * role via HTTP Basic, always. One hardcoded in-memory user, not a real user store or
 * OAuth - the minimal version this project needs, not a final design.
 *
 * <p>Swagger's own UI/docs, and the two admin control-plane reads ({@code
 * GET /api/admin/thresholds}, {@code GET /api/admin/policies}), are left public - useful
 * for exploring the API locally, and they're the admin's own settings, not collected
 * data, so they don't belong in {@code MetricAuthorizationManager}'s per-metric registry.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                            MetricAuthorizationManager metricAuthorizationManager) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/admin/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Spring's default error page is an internal forward, not a real client
                        // request - if it isn't public, an unhandled exception on a PUBLIC endpoint
                        // (e.g. a database outage on a metric nobody protected) gets its /error
                        // render blocked by the anyRequest().authenticated() catch-all below, so the
                        // client sees 401 instead of the real 500. Found via chaos testing.
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/**").access(metricAuthorizationManager)
                        .requestMatchers("/api/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable()) // stateless JSON API, no browser session/cookie to forge
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder,
                                                  @Value("${monitor.admin.username}") String username,
                                                  @Value("${monitor.admin.password}") String password) {
        return new InMemoryUserDetailsManager(
                User.withUsername(username)
                        .password(encoder.encode(password))
                        .roles("ADMIN")
                        .build());
    }
}

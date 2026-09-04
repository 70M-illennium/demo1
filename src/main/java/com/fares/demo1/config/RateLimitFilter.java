package com.fares.demo1.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Standard token-bucket rate limiting for {@code /api/**}, one bucket per client. Keyed
 * by the {@code X-API-Key} request header; falls back to the remote address when no key
 * is sent, since there is no auth layer issuing keys yet - once one exists, this starts
 * limiting per real client identity with no change here.
 *
 * <p>Each bucket holds {@link #CAPACITY} tokens and refills at {@link #REFILL_PER_MINUTE}
 * tokens/minute (a "greedy" refill: tokens trickle back continuously rather than all at
 * once at the top of the minute). A request that finds an empty bucket gets HTTP 429
 * (RFC 6585) with a {@code Retry-After} header instead of being served.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int CAPACITY = 120;
    private static final int REFILL_PER_MINUTE = 120;

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        Bucket bucket = buckets.computeIfAbsent(clientKey(request), key -> newBucket());
        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(429); // Too Many Requests
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"rate limit exceeded, retry after 60s\"}");
        }
    }

    private static String clientKey(HttpServletRequest request) {
        String apiKey = request.getHeader("X-API-Key");
        return (apiKey != null && !apiKey.isBlank()) ? "key:" + apiKey : "ip:" + request.getRemoteAddr();
    }

    private static Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(CAPACITY, Refill.greedy(REFILL_PER_MINUTE, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }
}

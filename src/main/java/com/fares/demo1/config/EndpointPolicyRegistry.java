package com.fares.demo1.config;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-metric "speciality" flags: which read endpoints are cached, and which require the
 * ADMIN role instead of being public GETs. One shared, mutable, runtime-editable
 * registry - the same shape as {@link HealthCheckProperties} - seeded with sane
 * defaults at startup and changeable via {@code AdminController} without a redeploy.
 *
 * <p><b>This is step 1 only: the policy store itself, with nothing reading it yet.</b>
 * Flipping a flag here doesn't cache or protect anything on its own - that's step 2
 * (a cache helper each read service calls, gated on {@code cached}) and step 3 (a
 * dynamic {@code AuthorizationManager} in {@code SecurityConfig}, gated on {@code
 * protectedAccess}). Until those land, this endpoint just remembers what the admin
 * asked for.
 *
 * <p>Keys are logical metric names, not raw paths, so a client reasons about "protect
 * security findings" rather than URL patterns. The key -&gt; path mapping this will
 * drive lives in the pieces that consume it (step 2/3), not here.
 */
@Component
public class EndpointPolicyRegistry {

    /**
     * Keys that can never be set {@code protectedAccess=false} through this registry,
     * regardless of what a PATCH asks for - real account names, {@code host='%'}
     * patterns and TLS posture (security.findings) and security-relevant GLOBAL
     * VARIABLES (config.latest) are too easy to turn into a roadmap for an attacker to
     * leave that decision to a single mistaken API call.
     */
    private static final Set<String> ALWAYS_PROTECTED = Set.of("security.findings", "config.latest");

    private final Map<String, EndpointPolicy> policies = new ConcurrentHashMap<>();

    public EndpointPolicyRegistry() {
        policies.put("database.latest", new EndpointPolicy(true, false));
        policies.put("host.latest", new EndpointPolicy(true, false));
        policies.put("activity.latest", new EndpointPolicy(true, false));
        policies.put("workload.queries", new EndpointPolicy(true, false));
        // sessions carry PROCESSLIST's currentSql, which can hold literal query values -
        // unlike QueryDigestSample.digestText, which is normalized. Also not cached:
        // it's live activity, serving a stale answer defeats the point.
        policies.put("workload.sessions", new EndpointPolicy(false, true));
        policies.put("workload.waits", new EndpointPolicy(true, false));
        policies.put("workload.tables", new EndpointPolicy(true, false));
        policies.put("config.latest", new EndpointPolicy(true, true));
        policies.put("security.findings", new EndpointPolicy(true, true));
        // event state should never look stale, and it isn't sensitive on its own.
        policies.put("events", new EndpointPolicy(false, false));
    }

    /** A stable snapshot for API responses - callers can't mutate the live registry through it. */
    public Map<String, EndpointPolicy> all() {
        return new LinkedHashMap<>(policies);
    }

    public EndpointPolicy get(String key) {
        return policies.get(key);
    }

    public boolean exists(String key) {
        return policies.containsKey(key);
    }

    public static boolean isAlwaysProtected(String key) {
        return ALWAYS_PROTECTED.contains(key);
    }

    public void update(String key, EndpointPolicy policy) {
        policies.put(key, policy);
    }
}

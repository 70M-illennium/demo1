package com.fares.demo1.config;

import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Step 3 of the per-metric "speciality" flags: decides, per GET request under {@code
 * /api/**} (excluding {@code /api/admin/**}, carved out separately in {@code
 * SecurityConfig} since it's the admin's own control-plane reads, not collected data),
 * whether the ADMIN role is required - by mapping the request path to a metric key and
 * reading that key's live {@code protectedAccess} flag from {@link
 * EndpointPolicyRegistry} <em>at request time</em>.
 *
 * <p>This has to be a custom {@link AuthorizationManager} rather than the static
 * {@code .permitAll()}/{@code .hasRole(...)} rules used everywhere else in {@code
 * SecurityConfig}: those are baked into the filter chain once, when the app starts.
 * This one is checked on every request, so a policy flip via {@code AdminController}
 * takes effect on the very next request with no restart - the same live-mutable-bean
 * pattern as the threshold tuning and the caching layer, just applied to auth instead.
 *
 * <p>Each controller's <em>entire</em> path prefix maps to the one registry key for
 * that domain - a row from {@code /api/database/snapshots} (recent history) carries
 * the same sensitivity as one from {@code /snapshots/latest}, so there's no reason to
 * key them separately. A path that matches nothing here is denied, not allowed (fail
 * closed): nothing currently unmapped exists, so this changes no existing behavior -
 * it only matters for a future controller nobody registered here yet, and it's safer
 * for that to show up as "needs a login" than as silently wide open.
 */
@Component
public class MetricAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final Map<String, String> PATH_TO_METRIC_KEY = new LinkedHashMap<>();

    static {
        // the 4 workload sub-resources first (specific, no wildcard) - none of them
        // overlap with each other or with anything below, so declaration order doesn't
        // actually matter here, but specific-before-broad is the safer habit
        PATH_TO_METRIC_KEY.put("/api/workload/queries", "workload.queries");
        PATH_TO_METRIC_KEY.put("/api/workload/sessions", "workload.sessions");
        PATH_TO_METRIC_KEY.put("/api/workload/waits", "workload.waits");
        PATH_TO_METRIC_KEY.put("/api/workload/tables", "workload.tables");

        PATH_TO_METRIC_KEY.put("/api/database/**", "database.latest");
        PATH_TO_METRIC_KEY.put("/api/host/**", "host.latest");
        PATH_TO_METRIC_KEY.put("/api/activity/**", "activity.latest");
        PATH_TO_METRIC_KEY.put("/api/config/**", "config.latest");
        PATH_TO_METRIC_KEY.put("/api/security/**", "security.findings");
        PATH_TO_METRIC_KEY.put("/api/events/**", "events");
    }

    private final EndpointPolicyRegistry endpointPolicyRegistry;

    /** Delegates the "is this caller ADMIN" question to Spring Security's own, already-correct logic. */
    private final AuthorizationManager<RequestAuthorizationContext> adminOnly = AuthorityAuthorizationManager.hasRole("ADMIN");

    public MetricAuthorizationManager(EndpointPolicyRegistry endpointPolicyRegistry) {
        this.endpointPolicyRegistry = endpointPolicyRegistry;
    }

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication,
                                          RequestAuthorizationContext context) {
        String metricKey = metricKeyFor(context.getRequest().getServletPath());
        if (metricKey == null) {
            return new AuthorizationDecision(false);
        }
        EndpointPolicy policy = endpointPolicyRegistry.get(metricKey);
        boolean protectedAccess = policy != null && policy.protectedAccess();
        return protectedAccess ? adminOnly.authorize(authentication, context) : new AuthorizationDecision(true);
    }

    private static String metricKeyFor(String path) {
        for (Map.Entry<String, String> entry : PATH_TO_METRIC_KEY.entrySet()) {
            if (PATH_MATCHER.match(entry.getKey(), path)) {
                return entry.getValue();
            }
        }
        return null;
    }
}

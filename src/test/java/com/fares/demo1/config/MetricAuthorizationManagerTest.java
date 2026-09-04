package com.fares.demo1.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Pure unit test - no Spring context, no HTTP server. {@link HttpServletRequest} is
 * mocked to stub just the servlet path {@link MetricAuthorizationManager} reads;
 * {@link EndpointPolicyRegistry} is mocked so each test controls the policy directly.
 * These check the same behavior the live curl checks proved during step 3 (default
 * public, default protected, fail-closed, admin override), just as a repeatable,
 * millisecond-fast test instead of a running Docker stack.
 */
@ExtendWith(MockitoExtension.class)
class MetricAuthorizationManagerTest {

    private static final Supplier<Authentication> ANONYMOUS =
            () -> new UsernamePasswordAuthenticationToken("anon", "n/a", List.of());
    private static final Supplier<Authentication> ADMIN =
            () -> new UsernamePasswordAuthenticationToken("fares", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

    @Mock
    private EndpointPolicyRegistry endpointPolicyRegistry;

    @Mock
    private HttpServletRequest request;

    private MetricAuthorizationManager manager;

    @BeforeEach
    void setUp() {
        manager = new MetricAuthorizationManager(endpointPolicyRegistry);
    }

    private RequestAuthorizationContext contextFor(String path) {
        lenient().when(request.getServletPath()).thenReturn(path);
        return new RequestAuthorizationContext(request);
    }

    private static boolean granted(AuthorizationResult result) {
        return ((AuthorizationDecision) result).isGranted();
    }

    @Test
    void unmappedPath_deniedEvenForAnAuthenticatedAdmin() {
        AuthorizationResult result = manager.authorize(ADMIN, contextFor("/api/doesnotexist"));

        assertThat(granted(result)).isFalse();
    }

    @Test
    void unprotectedMetric_grantedAnonymously() {
        when(endpointPolicyRegistry.get("database.latest")).thenReturn(new EndpointPolicy(true, false));

        AuthorizationResult result = manager.authorize(ANONYMOUS, contextFor("/api/database/snapshots/latest"));

        assertThat(granted(result)).isTrue();
    }

    @Test
    void protectedMetric_deniedAnonymously() {
        when(endpointPolicyRegistry.get("security.findings")).thenReturn(new EndpointPolicy(true, true));

        AuthorizationResult result = manager.authorize(ANONYMOUS, contextFor("/api/security/findings"));

        assertThat(granted(result)).isFalse();
    }

    @Test
    void protectedMetric_grantedForAdmin() {
        when(endpointPolicyRegistry.get("security.findings")).thenReturn(new EndpointPolicy(true, true));

        AuthorizationResult result = manager.authorize(ADMIN, contextFor("/api/security/findings"));

        assertThat(granted(result)).isTrue();
    }

    @Test
    void workloadSubResourcesResolveToTheirOwnDistinctKey() {
        when(endpointPolicyRegistry.get("workload.sessions")).thenReturn(new EndpointPolicy(false, true));
        when(endpointPolicyRegistry.get("workload.queries")).thenReturn(new EndpointPolicy(true, false));

        assertThat(granted(manager.authorize(ANONYMOUS, contextFor("/api/workload/sessions")))).isFalse();
        assertThat(granted(manager.authorize(ANONYMOUS, contextFor("/api/workload/queries")))).isTrue();
    }

    @Test
    void prefixMapping_coversSubPathsNotJustTheExactLatestPath() {
        when(endpointPolicyRegistry.get("database.latest")).thenReturn(new EndpointPolicy(true, true));

        AuthorizationResult result = manager.authorize(ANONYMOUS, contextFor("/api/database/snapshots/latest/storage"));

        assertThat(granted(result)).isFalse();
    }

    @Test
    void adminControlPlaneReadsAreNotRoutedThroughThisManagerAtAll_noKeyMapsToApiAdmin() {
        // AdminController's GET /thresholds and /policies are carved out as a separate,
        // always-public rule in SecurityConfig, ahead of this manager - so a path under
        // /api/admin/** correctly falls through to "unmapped -> denied" here, proving
        // this class never accidentally grants it; the actual public access comes from
        // the earlier rule this test does not exercise.
        AuthorizationResult result = manager.authorize(ANONYMOUS, contextFor("/api/admin/thresholds"));

        assertThat(granted(result)).isFalse();
    }
}

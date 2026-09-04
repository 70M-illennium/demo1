package com.fares.demo1.controller;

import com.fares.demo1.config.HealthCheckProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Full-stack integration test - real HTTP over a random port, real Spring Security
 * filter chain, real {@code HealthCheckProperties} / {@code EndpointPolicyRegistry}
 * beans, against the already-running store on :3307 (same as {@code
 * Demo1ApplicationTests}). This is the same shape of check the manual curl runs did
 * during development, just captured as something that runs on every {@code mvn test}
 * instead of by hand.
 *
 * <p>{@code AdminController}'s threshold and policy beans are process-wide singletons
 * shared with the real running app (see {@code MetricAuthorizationManager} /
 * {@code AdminController} javadoc) - every test here restores whatever it changed in
 * an {@code @AfterEach} so a test run leaves no side effects on the live values.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AdminControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private HealthCheckProperties healthCheckProperties;

    @Value("${monitor.admin.username}")
    private String adminUser;

    @Value("${monitor.admin.password}")
    private String adminPassword;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpEntity<Map<String, Object>> adminJson(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(adminUser, adminPassword);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    // ---------- thresholds: auth ----------

    @Test
    void getThresholds_isPublic() {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/api/admin/thresholds"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void patchThresholds_withoutCreds_isRejected() {
        HttpEntity<Map<String, Object>> body = new HttpEntity<>(Map.of("diskWarnPercent", 80.0));

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/admin/thresholds"), HttpMethod.PATCH, body, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---------- thresholds: validation ----------

    @AfterEach
    void restoreThresholds() {
        healthCheckProperties.setOpenAfter(2);
        healthCheckProperties.setResolveAfter(3);
        healthCheckProperties.setDiskWarnPercent(90.0);
        healthCheckProperties.setDiskCritPercent(95.0);
    }

    @Test
    void patchThresholds_theBugFromTheReview_openAfterZero_isRejected() {
        // openAfter=0 used to make breached.subList(0, 0) vacuously match every rule,
        // and a negative value crashed the whole evaluation loop - see AdminController's
        // validate() javadoc. This is a regression test for that exact fix.
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/admin/thresholds"), HttpMethod.PATCH,
                adminJson(Map.of("openAfter", 0)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("openAfter");
        assertThat(healthCheckProperties.getOpenAfter()).isEqualTo(2);   // unchanged
    }

    @Test
    void patchThresholds_invertedWarnCritPair_isRejected() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/admin/thresholds"), HttpMethod.PATCH,
                adminJson(Map.of("diskWarnPercent", 99.0, "diskCritPercent", 50.0)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(healthCheckProperties.getDiskWarnPercent()).isEqualTo(90.0);   // unchanged
    }

    @Test
    void patchThresholds_crossFieldCheck_usesLiveValueForTheFieldNotSentInTheRequest() {
        // only sends diskCritPercent, but it's checked against the CURRENT diskWarnPercent
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/admin/thresholds"), HttpMethod.PATCH,
                adminJson(Map.of("diskCritPercent", 10.0)), String.class);   // well below the live warn=90

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void patchThresholds_partialUpdateOfOneField_appliesAndIsReadableImmediately() {
        // proves the live-mutable-bean mechanism: no restart between write and read
        ResponseEntity<String> patch = restTemplate.exchange(
                url("/api/admin/thresholds"), HttpMethod.PATCH,
                adminJson(Map.of("diskWarnPercent", 77.0)), String.class);
        assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(healthCheckProperties.getDiskWarnPercent()).isEqualTo(77.0);
    }

    @Test
    void patchThresholds_aRejectedRequestNeverPartiallyApplies() {
        // valid diskWarnPercent alongside an invalid openAfter in the SAME request -
        // the whole request must be rejected, diskWarnPercent must stay untouched
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/admin/thresholds"), HttpMethod.PATCH,
                adminJson(Map.of("diskWarnPercent", 55.0, "openAfter", -3)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(healthCheckProperties.getDiskWarnPercent()).isEqualTo(90.0);
        assertThat(healthCheckProperties.getOpenAfter()).isEqualTo(2);
    }

    // ---------- policies: the always-protected guard ----------

    @Test
    void patchPolicy_cannotUnprotectSecurityFindings() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/admin/policies/security.findings"), HttpMethod.PATCH,
                adminJson(Map.of("protectedAccess", false)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void patchPolicy_cannotUnprotectConfigLatest() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/admin/policies/config.latest"), HttpMethod.PATCH,
                adminJson(Map.of("protectedAccess", false)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void patchPolicy_unknownKey_is404() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/admin/policies/not.a.real.key"), HttpMethod.PATCH,
                adminJson(Map.of("cached", true)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---------- policies: live cache/auth toggle proof ----------

    @Test
    void patchPolicy_flippingCachedOff_makesTheNextReadHitTheDatabaseAgain() throws InterruptedException {
        try {
            // first read establishes a cached answer
            Map<?, ?> first = restTemplate.getForObject(url("/api/activity/snapshots/latest"), Map.class);
            String t1 = (String) first.get("timestamp");

            // force a fresh row while still cached=true - the next read should be STALE
            restTemplate.exchange(url("/api/admin/collect"), HttpMethod.POST, adminJson(Map.of()), String.class);
            Map<?, ?> stillCached = restTemplate.getForObject(url("/api/activity/snapshots/latest"), Map.class);
            assertThat(stillCached.get("timestamp")).isEqualTo(t1);

            // turn caching off - the next read must be fresh
            restTemplate.exchange(url("/api/admin/policies/activity.latest"), HttpMethod.PATCH,
                    adminJson(Map.of("cached", false)), String.class);
            Map<?, ?> fresh = restTemplate.getForObject(url("/api/activity/snapshots/latest"), Map.class);
            assertThat(fresh.get("timestamp")).isNotEqualTo(t1);
        } finally {
            restTemplate.exchange(url("/api/admin/policies/activity.latest"), HttpMethod.PATCH,
                    adminJson(Map.of("cached", true)), String.class);
        }
    }

    @Test
    void patchPolicy_flippingProtectedOn_immediatelyRequiresAuthWithNoRestart() {
        try {
            ResponseEntity<String> beforePublic = restTemplate.getForEntity(
                    url("/api/database/snapshots/latest"), String.class);
            assertThat(beforePublic.getStatusCode()).isEqualTo(HttpStatus.OK);

            restTemplate.exchange(url("/api/admin/policies/database.latest"), HttpMethod.PATCH,
                    adminJson(Map.of("protectedAccess", true)), String.class);

            ResponseEntity<String> afterNoCreds = restTemplate.getForEntity(
                    url("/api/database/snapshots/latest"), String.class);
            assertThat(afterNoCreds.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        } finally {
            restTemplate.exchange(url("/api/admin/policies/database.latest"), HttpMethod.PATCH,
                    adminJson(Map.of("protectedAccess", false)), String.class);
        }
    }

    // ---------- admin actions ----------

    @Test
    void postCollect_requiresAdmin_thenSucceeds() {
        ResponseEntity<String> noCreds = restTemplate.postForEntity(url("/api/admin/collect"), null, String.class);
        assertThat(noCreds.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> withCreds = restTemplate.exchange(
                url("/api/admin/collect"), HttpMethod.POST, adminJson(Map.of()), String.class);
        assertThat(withCreds.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void postPurge_requiresAdmin_thenSucceeds() {
        ResponseEntity<String> noCreds = restTemplate.postForEntity(url("/api/admin/purge"), null, String.class);
        assertThat(noCreds.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> withCreds = restTemplate.exchange(
                url("/api/admin/purge"), HttpMethod.POST, adminJson(Map.of()), String.class);
        assertThat(withCreds.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ---------- known gap: activity http/login metrics stay 0 ----------

    @Test
    @SuppressWarnings("unchecked")
    void activityHttpAndLoginMetrics_stayZeroDespiteRealTraffic_documentsTheKnownGap() {
        // Generate real traffic against the API, then force a collection cycle, then
        // check the fields that need Actuator/Micrometer (http) and Spring Security
        // auth events (login) to populate - deliberately deferred, per project memory.
        // This test documents that gap on purpose: if it ever starts failing, that
        // means someone wired the collection up, and this test (and its comment)
        // should be deleted, not "fixed".
        for (int i = 0; i < 30; i++) {
            restTemplate.getForEntity(url("/api/events"), String.class);
        }
        restTemplate.getForEntity(url("/api/doesnotexist"), String.class);   // a 404, still "traffic"
        restTemplate.exchange(url("/api/admin/collect"), HttpMethod.POST, adminJson(Map.of()), String.class);

        Map<String, Object> latest = restTemplate.getForObject(url("/api/activity/snapshots/latest"), Map.class);
        Map<String, Object> http = (Map<String, Object>) latest.get("http");

        assertThat(((Number) http.get("requestsTotal")).longValue()).isZero();
        assertThat(((Number) http.get("requests5xx")).longValue()).isZero();
        assertThat(((Number) http.get("requestDurationP95Ms")).longValue()).isZero();
        assertThat(((Number) http.get("loginFailures")).longValue()).isZero();
    }
}

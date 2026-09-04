package com.fares.demo1.controller;

import com.fares.demo1.model.EventType;
import com.fares.demo1.model.MonitorEventEntity;
import com.fares.demo1.model.Severity;
import com.fares.demo1.repo.MonitorEventRepo;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Full-stack integration test for the ack/resolve write path, seeding real rows in the
 * live store (same one {@code Demo1ApplicationTests} connects to) via {@code
 * MonitorEventRepo} rather than waiting for a real alert to fire. Each test cleans up
 * the row it created in {@code @AfterEach} so nothing it seeds lingers in {@code
 * GET /api/events} after the test run.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureTestRestTemplate
class EventControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MonitorEventRepo monitorEventRepo;

    @Value("${monitor.admin.username}")
    private String adminUser;

    @Value("${monitor.admin.password}")
    private String adminPassword;

    private Long seededId;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private MonitorEventEntity seedOpenEvent() {
        MonitorEventEntity event = new MonitorEventEntity();
        event.setType(EventType.TARGET_ERROR_LOG);
        event.setSeverity(Severity.WARNING);
        event.setMessage("test-seeded event");
        Instant now = Instant.now();
        event.setOccurredAt(now);
        event.setLastSeenAt(now);
        event = monitorEventRepo.save(event);
        seededId = event.getId();
        return event;
    }

    @AfterEach
    void cleanUp() {
        if (seededId != null) {
            monitorEventRepo.deleteById(seededId);
            seededId = null;
        }
    }

    private HttpEntity<Map<String, Object>> adminJson(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(adminUser, adminPassword);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void ack_withoutCreds_isRejected() {
        MonitorEventEntity event = seedOpenEvent();

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/events/" + event.getId() + "/ack"), HttpMethod.PUT,
                new HttpEntity<>(Map.of("note", "x")), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ack_setsFlagAndNote_withoutResolvingTheEvent() {
        MonitorEventEntity event = seedOpenEvent();

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/events/" + event.getId() + "/ack"), HttpMethod.PUT,
                adminJson(Map.of("note", "investigating")), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body.get("acknowledged")).isEqualTo(true);
        assertThat(body.get("ackNote")).isEqualTo("investigating");
        assertThat(body.get("resolvedAt")).isNull();
        assertThat(body.get("active")).isEqualTo(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void resolve_closesTheEvent() {
        MonitorEventEntity event = seedOpenEvent();

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/events/" + event.getId() + "/resolve"), HttpMethod.PUT,
                adminJson(Map.of()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body.get("resolvedAt")).isNotNull();
        assertThat(body.get("active")).isEqualTo(false);
    }

    @Test
    void resolve_isIdempotent_secondCallStillReturns200WithTheSameResolvedAt() {
        MonitorEventEntity event = seedOpenEvent();

        ResponseEntity<Map> first = restTemplate.exchange(
                url("/api/events/" + event.getId() + "/resolve"), HttpMethod.PUT,
                adminJson(Map.of()), Map.class);
        ResponseEntity<Map> second = restTemplate.exchange(
                url("/api/events/" + event.getId() + "/resolve"), HttpMethod.PUT,
                adminJson(Map.of()), Map.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Not an exact string match: the first response serializes the in-memory
        // Instant.now() at nanosecond precision (never round-tripped through MySQL,
        // since forceResolve's early-return branch skips the save), while the second
        // call's MonitorEventService.find() re-fetches from the DB, where the
        // datetime(6) column rounds to microseconds - same instant, different
        // sub-microsecond digits. Comparing as parsed Instants within a 1ms tolerance
        // is the correct check; an exact string/Instant.equals() comparison is not.
        Instant firstResolvedAt = Instant.parse((String) first.getBody().get("resolvedAt"));
        Instant secondResolvedAt = Instant.parse((String) second.getBody().get("resolvedAt"));
        assertThat(Duration.between(firstResolvedAt, secondResolvedAt).abs()).isLessThan(Duration.ofMillis(1));
    }

    @Test
    void ack_unknownId_is404() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/events/999999999/ack"), HttpMethod.PUT,
                adminJson(Map.of("note", "x")), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void resolve_unknownId_is404() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/events/999999999/resolve"), HttpMethod.PUT,
                adminJson(Map.of()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void seededEvent_showsUpInTheActiveList_thenDisappearsAfterResolve() {
        MonitorEventEntity event = seedOpenEvent();

        Map[] activeBefore = restTemplate.getForObject(url("/api/events/active"), Map[].class);
        assertThat(idsOf(activeBefore)).contains(event.getId());

        restTemplate.exchange(url("/api/events/" + event.getId() + "/resolve"), HttpMethod.PUT,
                adminJson(Map.of()), Map.class);

        Map[] activeAfter = restTemplate.getForObject(url("/api/events/active"), Map[].class);
        assertThat(idsOf(activeAfter)).doesNotContain(event.getId());
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<Long> idsOf(Map[] events) {
        return java.util.Arrays.stream(events)
                .map(e -> ((Number) e.get("id")).longValue())
                .toList();
    }
}

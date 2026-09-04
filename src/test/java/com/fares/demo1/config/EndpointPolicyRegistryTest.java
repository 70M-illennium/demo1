package com.fares.demo1.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit test - no Spring context, no database. {@link EndpointPolicyRegistry} is a
 * plain in-memory map, so this just checks the seeded defaults and the always-protected
 * guard are what {@code AdminController} relies on.
 */
class EndpointPolicyRegistryTest {

    private final EndpointPolicyRegistry registry = new EndpointPolicyRegistry();

    @Test
    void seedsAllTenKnownMetricKeys() {
        assertThat(registry.all()).containsOnlyKeys(
                "database.latest", "host.latest", "activity.latest",
                "workload.queries", "workload.sessions", "workload.waits", "workload.tables",
                "config.latest", "security.findings", "events");
    }

    @Test
    void securityFindingsAndConfigLatestAreProtectedByDefault() {
        assertThat(registry.get("security.findings").protectedAccess()).isTrue();
        assertThat(registry.get("config.latest").protectedAccess()).isTrue();
    }

    @Test
    void workloadSessionsIsNotCachedByDefault_liveDataShouldNotGoStale() {
        assertThat(registry.get("workload.sessions").cached()).isFalse();
    }

    @Test
    void eventsIsNeitherCachedNorProtectedByDefault() {
        EndpointPolicy events = registry.get("events");
        assertThat(events.cached()).isFalse();
        assertThat(events.protectedAccess()).isFalse();
    }

    @Test
    void updateOverwritesAnExistingKey() {
        registry.update("database.latest", new EndpointPolicy(false, true));

        assertThat(registry.get("database.latest")).isEqualTo(new EndpointPolicy(false, true));
    }

    @Test
    void unknownKeyIsAbsent() {
        assertThat(registry.exists("not.a.real.key")).isFalse();
        assertThat(registry.get("not.a.real.key")).isNull();
    }

    @Test
    void alwaysProtectedGuardCoversOnlyTheTwoSensitiveKeys() {
        assertThat(EndpointPolicyRegistry.isAlwaysProtected("security.findings")).isTrue();
        assertThat(EndpointPolicyRegistry.isAlwaysProtected("config.latest")).isTrue();
        assertThat(EndpointPolicyRegistry.isAlwaysProtected("database.latest")).isFalse();
        assertThat(EndpointPolicyRegistry.isAlwaysProtected("events")).isFalse();
    }

    @Test
    void allReturnsASnapshot_mutatingItDoesNotAffectTheRegistry() {
        registry.all().remove("database.latest");

        assertThat(registry.exists("database.latest")).isTrue();
    }
}

package com.fares.demo1.dto;

import com.fares.demo1.model.ActivitySnapshotEntity;

import java.time.Instant;

/**
 * API view of one activity snapshot. Read-only: the JPA entity is never serialised
 * directly. Grouped by concern (http, jvm); field names shorten inside a group.
 * {@code jvm.heapUsagePercent} is computed here, not stored. The http group is not
 * populated yet - it needs Actuator/Micrometer and Spring Security.
 */
public record ActivitySnapshotResponseDTO(
        long id,
        Instant timestamp,
        Http http,
        Jvm jvm
) {

    public record Http(
            long requestsTotal,
            long requests5xx,
            long requestDurationP95Ms,
            long loginFailures
    ) {}

    public record Jvm(
            double heapUsedMb,
            double heapMaxMb,
            double heapUsagePercent
    ) {}

    public static ActivitySnapshotResponseDTO from(ActivitySnapshotEntity e) {
        return new ActivitySnapshotResponseDTO(
                e.getId(),
                e.getTimestamp(),
                new Http(
                        e.getHttpRequestsTotal(),
                        e.getHttpRequests5xx(),
                        e.getHttpRequestDurationP95Ms(),
                        e.getLoginFailures()
                ),
                new Jvm(
                        Units.toMb(e.getJvmHeapUsedBytes()),
                        Units.toMb(e.getJvmHeapMaxBytes()),
                        heapUsagePercent(e.getJvmHeapUsedBytes(), e.getJvmHeapMaxBytes())
                )
        );
    }

    /** Heap used as a percentage of max, rounded to 2 decimals. 0 when max is undefined. */
    private static double heapUsagePercent(long used, long max) {
        if (max <= 0) {
            return 0.0;
        }
        return Math.round((double) used / max * 100.0 * 100.0) / 100.0;
    }
}

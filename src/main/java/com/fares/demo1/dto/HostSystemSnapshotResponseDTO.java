package com.fares.demo1.dto;

import com.fares.demo1.model.HostSystemSnapshotEntity;

import java.time.Instant;

/**
 * API view of one host-system snapshot. Read-only: the JPA entity is never serialised
 * directly. Metrics are grouped by resource (cpu, memory, disk); field names shorten
 * inside a group. Computed values (freeGb) are derived here, not stored.
 */
public record HostSystemSnapshotResponseDTO(
        long id,
        Instant timestamp,
        Cpu cpu,
        Memory memory,
        Disk disk
) {

    public record Cpu(
            double usagePercent,
            double loadAverage1m
    ) {}

    public record Memory(
            double usagePercent,
            double swapUsedMb
    ) {}

    public record Disk(
            double usagePercent,
            double freeGb
    ) {}

    public static HostSystemSnapshotResponseDTO from(HostSystemSnapshotEntity e) {
        return new HostSystemSnapshotResponseDTO(
                e.getId(),
                e.getTimestamp(),
                new Cpu(
                        Units.round2(e.getCpuUsagePercent()),
                        Units.round2(e.getLoadAverage1m())
                ),
                new Memory(
                        Units.round2(e.getMemoryUsagePercent()),
                        Units.toMb(e.getSwapUsedBytes())
                ),
                new Disk(
                        Units.round2(e.getDiskUsagePercent()),
                        Units.toGb(e.getFilesystemFreeBytes())
                )
        );
    }
}

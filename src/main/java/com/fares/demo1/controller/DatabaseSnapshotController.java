package com.fares.demo1.controller;

import com.fares.demo1.dto.DatabaseSnapshotResponseDTO;
import com.fares.demo1.service.DatabaseSnapshotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.function.Function;

/**
 * Read-only HTTP view of the collected database snapshots.
 *
 * <pre>
 *   GET /api/database/snapshots?limit=20              most recent snapshots, newest first
 *   GET /api/database/snapshots/latest                the single most recent snapshot
 *   GET /api/database/snapshots/latest/availability   just that section of the latest snapshot
 *   GET /api/database/snapshots/latest/connections
 *   GET /api/database/snapshots/latest/workload
 *   GET /api/database/snapshots/latest/bufferPool
 *   GET /api/database/snapshots/latest/locking
 *   GET /api/database/snapshots/latest/storage
 * </pre>
 */
@RestController
@RequestMapping("/api/database")
@RequiredArgsConstructor
@Tag(name = "Database", description = "Core MySQL health and performance snapshots - availability, connections, buffer pool, locking, storage")
public class DatabaseSnapshotController {

    private final DatabaseSnapshotService databaseSnapshotService;

    @GetMapping("/snapshots")
    @Operation(summary = "List recent database snapshots, newest first")
    public List<DatabaseSnapshotResponseDTO> recent(@RequestParam(defaultValue = "20") int limit) {
        int capped = Math.min(Math.max(limit, 1), 500);
        return databaseSnapshotService.recentSnapshots(capped).stream()
                .map(DatabaseSnapshotResponseDTO::from)
                .toList();
    }

    @GetMapping("/snapshots/latest")
    @Operation(summary = "Get the most recent database snapshot")
    public ResponseEntity<DatabaseSnapshotResponseDTO> latest() {
        return latestSection(Function.identity());
    }

    @GetMapping("/snapshots/latest/availability")
    @Operation(summary = "Get just the availability section (reachable, latency, uptime) of the latest database snapshot")
    public ResponseEntity<DatabaseSnapshotResponseDTO.Availability> availability() {
        return latestSection(DatabaseSnapshotResponseDTO::availability);
    }

    @GetMapping("/snapshots/latest/connections")
    @Operation(summary = "Get just the connections section of the latest database snapshot")
    public ResponseEntity<DatabaseSnapshotResponseDTO.Connections> connections() {
        return latestSection(DatabaseSnapshotResponseDTO::connections);
    }

    @GetMapping("/snapshots/latest/workload")
    @Operation(summary = "Get just the workload section (questions/sec, slow queries) of the latest database snapshot")
    public ResponseEntity<DatabaseSnapshotResponseDTO.Workload> workload() {
        return latestSection(DatabaseSnapshotResponseDTO::workload);
    }

    @GetMapping("/snapshots/latest/bufferPool")
    @Operation(summary = "Get just the InnoDB buffer pool section of the latest database snapshot")
    public ResponseEntity<DatabaseSnapshotResponseDTO.BufferPool> bufferPool() {
        return latestSection(DatabaseSnapshotResponseDTO::bufferPool);
    }

    @GetMapping("/snapshots/latest/locking")
    @Operation(summary = "Get just the locking and deadlocks section of the latest database snapshot")
    public ResponseEntity<DatabaseSnapshotResponseDTO.Locking> locking() {
        return latestSection(DatabaseSnapshotResponseDTO::locking);
    }

    @GetMapping("/snapshots/latest/storage")
    @Operation(summary = "Get just the storage size section of the latest database snapshot")
    public ResponseEntity<DatabaseSnapshotResponseDTO.Storage> storage() {
        return latestSection(DatabaseSnapshotResponseDTO::storage);
    }

    /**
     * Shared plumbing for the "latest snapshot" endpoints: fetch the latest snapshot,
     * map it to the response DTO, then apply {@code section} to pick the piece to return
     * (or the whole DTO via {@link Function#identity()}). 204 when nothing is collected yet.
     */
    private <T> ResponseEntity<T> latestSection(Function<DatabaseSnapshotResponseDTO, T> section) {
        return databaseSnapshotService.latestSnapshot()
                .map(DatabaseSnapshotResponseDTO::from)
                .map(section)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}

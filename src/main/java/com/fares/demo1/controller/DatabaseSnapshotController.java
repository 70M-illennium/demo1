package com.fares.demo1.controller;

import com.fares.demo1.dto.DatabaseSnapshotResponseDTO;
import com.fares.demo1.service.DatabaseSnapshotService;
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
public class DatabaseSnapshotController {

    private final DatabaseSnapshotService databaseSnapshotService;

    @GetMapping("/snapshots")
    public List<DatabaseSnapshotResponseDTO> recent(@RequestParam(defaultValue = "20") int limit) {
        int capped = Math.min(Math.max(limit, 1), 500);
        return databaseSnapshotService.recentSnapshots(capped).stream()
                .map(DatabaseSnapshotResponseDTO::from)
                .toList();
    }

    @GetMapping("/snapshots/latest")
    public ResponseEntity<DatabaseSnapshotResponseDTO> latest() {
        return latestSection(Function.identity());
    }

    @GetMapping("/snapshots/latest/availability")
    public ResponseEntity<DatabaseSnapshotResponseDTO.Availability> availability() {
        return latestSection(DatabaseSnapshotResponseDTO::availability);
    }

    @GetMapping("/snapshots/latest/connections")
    public ResponseEntity<DatabaseSnapshotResponseDTO.Connections> connections() {
        return latestSection(DatabaseSnapshotResponseDTO::connections);
    }

    @GetMapping("/snapshots/latest/workload")
    public ResponseEntity<DatabaseSnapshotResponseDTO.Workload> workload() {
        return latestSection(DatabaseSnapshotResponseDTO::workload);
    }

    @GetMapping("/snapshots/latest/bufferPool")
    public ResponseEntity<DatabaseSnapshotResponseDTO.BufferPool> bufferPool() {
        return latestSection(DatabaseSnapshotResponseDTO::bufferPool);
    }

    @GetMapping("/snapshots/latest/locking")
    public ResponseEntity<DatabaseSnapshotResponseDTO.Locking> locking() {
        return latestSection(DatabaseSnapshotResponseDTO::locking);
    }

    @GetMapping("/snapshots/latest/storage")
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

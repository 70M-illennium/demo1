package com.fares.demo1.controller;

import com.fares.demo1.dto.HostSystemSnapshotResponseDTO;
import com.fares.demo1.service.HostSystemSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.function.Function;

/**
 * Read-only HTTP view of the collected host-system snapshots.
 *
 * <pre>
 *   GET /api/host/snapshots?limit=20         most recent snapshots, newest first
 *   GET /api/host/snapshots/latest           the single most recent snapshot
 *   GET /api/host/snapshots/latest/cpu       just that section of the latest snapshot
 *   GET /api/host/snapshots/latest/memory
 *   GET /api/host/snapshots/latest/disk
 * </pre>
 */
@RestController
@RequestMapping("/api/host")
@RequiredArgsConstructor
public class HostSystemSnapshotController {

    private final HostSystemSnapshotService hostSystemSnapshotService;

    @GetMapping("/snapshots")
    public List<HostSystemSnapshotResponseDTO> recent(@RequestParam(defaultValue = "20") int limit) {
        int capped = Math.min(Math.max(limit, 1), 500);
        return hostSystemSnapshotService.recentSnapshots(capped).stream()
                .map(HostSystemSnapshotResponseDTO::from)
                .toList();
    }

    @GetMapping("/snapshots/latest")
    public ResponseEntity<HostSystemSnapshotResponseDTO> latest() {
        return latestSection(Function.identity());
    }

    @GetMapping("/snapshots/latest/cpu")
    public ResponseEntity<HostSystemSnapshotResponseDTO.Cpu> cpu() {
        return latestSection(HostSystemSnapshotResponseDTO::cpu);
    }

    @GetMapping("/snapshots/latest/memory")
    public ResponseEntity<HostSystemSnapshotResponseDTO.Memory> memory() {
        return latestSection(HostSystemSnapshotResponseDTO::memory);
    }

    @GetMapping("/snapshots/latest/disk")
    public ResponseEntity<HostSystemSnapshotResponseDTO.Disk> disk() {
        return latestSection(HostSystemSnapshotResponseDTO::disk);
    }

    /**
     * Shared plumbing for the "latest snapshot" endpoints: fetch the latest snapshot,
     * map it to the response DTO, then apply {@code section} to pick the piece to return
     * (or the whole DTO via {@link Function#identity()}). 204 when nothing is collected yet.
     */
    private <T> ResponseEntity<T> latestSection(Function<HostSystemSnapshotResponseDTO, T> section) {
        return hostSystemSnapshotService.latestSnapshot()
                .map(HostSystemSnapshotResponseDTO::from)
                .map(section)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}

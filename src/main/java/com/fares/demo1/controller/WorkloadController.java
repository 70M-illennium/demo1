package com.fares.demo1.controller;

import com.fares.demo1.dto.QueryDigestResponseDTO;
import com.fares.demo1.dto.SessionResponseDTO;
import com.fares.demo1.dto.TableSizeResponseDTO;
import com.fares.demo1.dto.WaitResponseDTO;
import com.fares.demo1.service.WorkloadSnapshotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The target's workload as of the most recent collection cycle.
 *
 * <pre>
 *   GET /api/workload/queries    top query digests, slowest first
 *   GET /api/workload/sessions   live (non-idle) sessions, longest-running first
 *   GET /api/workload/waits      top wait events, most time first
 *   GET /api/workload/tables     per-table sizes, largest first (slow cadence)
 * </pre>
 */
@RestController
@RequestMapping("/api/workload")
@RequiredArgsConstructor
@Tag(name = "Workload", description = "Query digests, live sessions, wait events, and table sizes from the most recent collection cycle")
public class WorkloadController {

    private final WorkloadSnapshotService workloadSnapshotService;

    @GetMapping("/queries")
    @Operation(summary = "List the top query digests by total execution time, slowest first")
    public List<QueryDigestResponseDTO> queries() {
        return workloadSnapshotService.latestDigests().stream()
                .map(QueryDigestResponseDTO::from)
                .toList();
    }

    @GetMapping("/sessions")
    @Operation(summary = "List non-idle sessions as of the last scheduled snapshot (up to 60s old), longest-running first")
    public List<SessionResponseDTO> sessions() {
        return workloadSnapshotService.latestSessions().stream()
                .map(SessionResponseDTO::from)
                .toList();
    }

    @GetMapping("/waits")
    @Operation(summary = "List the top wait events by total time")
    public List<WaitResponseDTO> waits() {
        return workloadSnapshotService.latestWaits().stream()
                .map(WaitResponseDTO::from)
                .toList();
    }

    @GetMapping("/tables")
    @Operation(summary = "List per-table sizes, largest first (updated every 10 minutes)")
    public List<TableSizeResponseDTO> tables() {
        return workloadSnapshotService.latestTableSizes().stream()
                .map(TableSizeResponseDTO::from)
                .toList();
    }
}

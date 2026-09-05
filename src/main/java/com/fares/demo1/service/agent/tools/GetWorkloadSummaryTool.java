package com.fares.demo1.service.agent.tools;

import com.fares.demo1.dto.QueryDigestResponseDTO;
import com.fares.demo1.dto.SessionResponseDTO;
import com.fares.demo1.dto.TableSizeResponseDTO;
import com.fares.demo1.dto.WaitResponseDTO;
import com.fares.demo1.service.agent.AgentTool;
import com.fares.demo1.service.WorkloadSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Wraps all four {@link WorkloadSnapshotService} reads in one call - top query digests,
 * live sessions, top wait events, and table sizes. This is the tool that actually
 * answers "why is it slow": the workload data already explains most slowdowns without
 * needing a live query against the target.
 */
@Component
@RequiredArgsConstructor
public class GetWorkloadSummaryTool implements AgentTool {

    private final WorkloadSnapshotService workloadSnapshotService;

    public record WorkloadView(
            List<QueryDigestResponseDTO> queries,
            List<SessionResponseDTO> sessions,
            List<WaitResponseDTO> waits,
            List<TableSizeResponseDTO> tables
    ) {
    }

    @Override
    public String name() {
        return "get_workload_summary";
    }

    @Override
    public String description() {
        return "The most recent workload snapshot: top query digests by time, live "
                + "(non-idle) sessions, top wait events, and per-table sizes. Use for "
                + "questions about slow queries, blocking sessions, or what the database "
                + "is spending its time on.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of(), "required", List.of());
    }

    @Override
    public Object execute(Map<String, Object> input) {
        return new WorkloadView(
                workloadSnapshotService.latestDigests().stream().map(QueryDigestResponseDTO::from).toList(),
                workloadSnapshotService.latestSessions().stream().map(SessionResponseDTO::from).toList(),
                workloadSnapshotService.latestWaits().stream().map(WaitResponseDTO::from).toList(),
                workloadSnapshotService.latestTableSizes().stream().map(TableSizeResponseDTO::from).toList());
    }
}

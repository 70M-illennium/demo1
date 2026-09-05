package com.fares.demo1.service.agent.tools;

import com.fares.demo1.dto.SessionResponseDTO;
import com.fares.demo1.service.WorkloadSnapshotService;
import com.fares.demo1.service.agent.AgentTool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Wraps {@link WorkloadSnapshotService#currentActiveQueries()} - a live {@code
 * PROCESSLIST} read taken at ask time, not the last scheduled snapshot. Distinct from
 * {@code get_workload_summary}'s {@code sessions} field, which can be up to 60 seconds
 * stale: most queries finish well within that window, so that field is often empty even
 * while something actually just ran. This tool exists for "what is running right now".
 */
@Component
@RequiredArgsConstructor
public class GetCurrentActiveQueriesTool implements AgentTool {

    private final WorkloadSnapshotService workloadSnapshotService;

    @Override
    public String name() {
        return "get_current_active_queries";
    }

    @Override
    public String description() {
        return "Sessions running RIGHT NOW on the target, read live (not from the last "
                + "scheduled snapshot, which can be up to a minute stale). An empty "
                + "result means nothing is running at this exact instant - use for "
                + "'what is running right now' or 'is anything blocking currently'.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of(), "required", List.of());
    }

    @Override
    public Object execute(Map<String, Object> input) {
        return workloadSnapshotService.currentActiveQueries().stream()
                .map(SessionResponseDTO::from)
                .toList();
    }
}

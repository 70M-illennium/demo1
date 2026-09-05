package com.fares.demo1.service.agent.tools;

import com.fares.demo1.dto.ConfigSnapshotResponseDTO;
import com.fares.demo1.model.ConfigSnapshotEntity;
import com.fares.demo1.service.ConfigSnapshotService;
import com.fares.demo1.service.agent.AgentTool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Wraps {@link ConfigSnapshotService#recentSnapshots}, same shape as {@code ConfigSnapshotController}. */
@Component
@RequiredArgsConstructor
public class GetConfigDriftTool implements AgentTool {

    private static final int DEFAULT_LIMIT = 10;

    private final ConfigSnapshotService configSnapshotService;

    @Override
    public String name() {
        return "get_config_drift";
    }

    @Override
    public String description() {
        return "Recent changes to tracked GLOBAL VARIABLES (max_connections, "
                + "innodb_flush_log_at_trx_commit, TLS/security settings, ...), newest "
                + "first - only a new row exists when something actually changed. Use "
                + "for questions about whether a config change caused a problem.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "limit", Map.of("type", "integer",
                                "description", "How many recent config snapshots to return (default 10).")),
                "required", List.of());
    }

    @Override
    public Object execute(Map<String, Object> input) {
        int limit = AgentToolInputs.intOrDefault(input, "limit", DEFAULT_LIMIT);
        List<ConfigSnapshotEntity> snapshots = configSnapshotService.recentSnapshots(limit);
        return snapshots.stream()
                .map(s -> ConfigSnapshotResponseDTO.from(s, configSnapshotService.valuesOf(s)))
                .toList();
    }
}

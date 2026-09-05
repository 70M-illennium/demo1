package com.fares.demo1.service.agent.tools;

import com.fares.demo1.dto.DatabaseSnapshotResponseDTO;
import com.fares.demo1.service.DatabaseSnapshotService;
import com.fares.demo1.service.agent.AgentTool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Wraps {@link DatabaseSnapshotService#recentSnapshots}, unchanged - same data the REST API serves. */
@Component
@RequiredArgsConstructor
public class GetRecentDatabaseSnapshotsTool implements AgentTool {

    private static final int DEFAULT_LIMIT = 5;

    private final DatabaseSnapshotService databaseSnapshotService;

    @Override
    public String name() {
        return "get_recent_database_snapshots";
    }

    @Override
    public String description() {
        return "Recent MySQL health snapshots (availability, connections, buffer pool, "
                + "locking, storage), newest first. Use for questions about database health right now.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "limit", Map.of("type", "integer",
                                "description", "How many recent snapshots to return (default 5).")),
                "required", List.of());
    }

    @Override
    public Object execute(Map<String, Object> input) {
        int limit = AgentToolInputs.intOrDefault(input, "limit", DEFAULT_LIMIT);
        return databaseSnapshotService.recentSnapshots(limit).stream()
                .map(DatabaseSnapshotResponseDTO::from)
                .toList();
    }
}

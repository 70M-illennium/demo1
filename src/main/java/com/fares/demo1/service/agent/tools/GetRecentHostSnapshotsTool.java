package com.fares.demo1.service.agent.tools;

import com.fares.demo1.dto.HostSystemSnapshotResponseDTO;
import com.fares.demo1.service.HostSystemSnapshotService;
import com.fares.demo1.service.agent.AgentTool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Wraps {@link HostSystemSnapshotService#recentSnapshots}, unchanged - same data the REST API serves. */
@Component
@RequiredArgsConstructor
public class GetRecentHostSnapshotsTool implements AgentTool {

    private static final int DEFAULT_LIMIT = 5;

    private final HostSystemSnapshotService hostSystemSnapshotService;

    @Override
    public String name() {
        return "get_recent_host_snapshots";
    }

    @Override
    public String description() {
        return "Recent host machine snapshots (CPU, memory, disk, load average), newest "
                + "first. Use for questions about the server the database runs on.";
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
        return hostSystemSnapshotService.recentSnapshots(limit).stream()
                .map(HostSystemSnapshotResponseDTO::from)
                .toList();
    }
}

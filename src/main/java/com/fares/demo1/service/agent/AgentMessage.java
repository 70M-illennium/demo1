package com.fares.demo1.service.agent;

import java.util.List;

/**
 * One turn of agent conversation, independent of any vendor's wire format.
 * {@link AgentOrchestrator} only ever deals in these; each {@link AgentBackend}
 * translates to and from its own provider's request/response shape.
 */
public record AgentMessage(
        String role,
        String content,
        List<ToolCall> toolCalls,
        String toolCallId
) {
    public record ToolCall(String id, String name, String argumentsJson) {
    }
}

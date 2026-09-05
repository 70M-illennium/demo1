package com.fares.demo1.service.agent;

import java.util.List;

/**
 * One AI provider's chat/tool-calling endpoint, translating between its own wire
 * format and the provider-agnostic {@link AgentMessage}/{@link ToolDefinition} shapes
 * {@link AgentOrchestrator} works in. Exactly one implementation is active at a time,
 * chosen by {@code monitor.ai.provider} (see AgentConfig).
 */
public interface AgentBackend {

    /**
     * Sends the full conversation so far plus the available tools and returns the
     * model's next message - either free-text (the final answer) or one or more tool
     * calls for {@link AgentOrchestrator} to execute and feed back in.
     */
    AgentMessage nextMessage(String systemPrompt, List<AgentMessage> history, List<ToolDefinition> tools);
}

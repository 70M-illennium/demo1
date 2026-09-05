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
     *
     * <p>{@code toolsAllowed} is false only for the final call once the tool-use loop
     * is done (see {@code AgentOrchestrator.finalizeAnswer}). The {@code tools}
     * definitions are still passed even then - some models' chat templates render
     * earlier tool-result messages differently (or drop them) when no tools are
     * declared on the request at all, so removing the definitions to stop further tool
     * calls can silently make the model forget everything the tools returned. Instead,
     * a backend that supports it (see {@code OpenAiCompatibleBackend}) uses {@code
     * tool_choice: "none"} to disable calling a tool this turn while still declaring
     * them, keeping template rendering consistent across every turn of one
     * conversation.
     */
    AgentMessage nextMessage(String systemPrompt, List<AgentMessage> history, List<ToolDefinition> tools, boolean toolsAllowed);
}

package com.fares.demo1.service.agent.openai;

import com.fares.demo1.service.agent.AgentBackend;
import com.fares.demo1.service.agent.AgentMessage;
import com.fares.demo1.service.agent.ToolDefinition;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Backs both the "ollama" and "openai" providers: Ollama's {@code /v1/chat/completions}
 * endpoint and the real OpenAI API speak the identical wire format, so one class
 * handles both - only the base URL, model, and whether an Authorization header is sent
 * differ (Ollama is a local server, no key needed; OpenAI requires a bearer token).
 */
public class OpenAiCompatibleBackend implements AgentBackend {

    // A hard backstop against a model ignoring the system prompt's brevity rules and
    // producing a long prose report - 700 is generous for even a dozen key:value lines
    // or several simultaneous tool calls (small JSON each), but cuts off an essay.
    private static final int MAX_OUTPUT_TOKENS = 700;

    private final RestClient restClient;
    private final String model;
    private final String apiKey;

    /** {@code apiKey} may be null/blank - no Authorization header is sent in that case (Ollama). */
    public OpenAiCompatibleBackend(RestClient restClient, String model, String apiKey) {
        this.restClient = restClient;
        this.model = model;
        this.apiKey = apiKey;
    }

    @Override
    public AgentMessage nextMessage(String systemPrompt, List<AgentMessage> history, List<ToolDefinition> tools, boolean toolsAllowed) {
        List<ChatMessage> messages = new ArrayList<>(history.size() + 1);
        messages.add(new ChatMessage("system", systemPrompt, null, null));
        history.forEach(m -> messages.add(toWireMessage(m)));

        // The tool definitions are always sent, even on the finalize call - some models'
        // chat templates render earlier tool-result messages differently (or drop them
        // outright) when the request declares no tools at all, silently making the model
        // "forget" everything the tools returned. Instead, tool_choice:"none" disables
        // calling a tool THIS turn while keeping the declarations (and template
        // rendering) consistent; JSON mode is then safe to enable, since offering it
        // together with an actually-available tool choice is what let the model fake a
        // tool call as plain-text JSON instead of using the real tool_calls mechanism.
        List<ChatRequest.Tool> wireTools = tools.isEmpty() ? null : tools.stream().map(OpenAiCompatibleBackend::toWireTool).toList();
        ChatRequest request = new ChatRequest(
                model,
                messages,
                wireTools,
                MAX_OUTPUT_TOKENS,
                toolsAllowed ? null : ChatRequest.ResponseFormat.JSON_OBJECT,
                (!toolsAllowed && wireTools != null) ? "none" : null);

        ChatResponse response = restClient.post()
                .uri("/v1/chat/completions")
                .headers(headers -> {
                    if (apiKey != null && !apiKey.isBlank()) {
                        headers.setBearerAuth(apiKey);
                    }
                })
                .body(request)
                .retrieve()
                .body(ChatResponse.class);

        return toAgentMessage(response.choices().get(0).message());
    }

    private static ChatMessage toWireMessage(AgentMessage m) {
        List<ChatMessage.ToolCall> calls = m.toolCalls() == null ? null : m.toolCalls().stream()
                .map(c -> new ChatMessage.ToolCall(c.id(), "function", new ChatMessage.ToolCall.Function(c.name(), c.argumentsJson())))
                .toList();
        return new ChatMessage(m.role(), m.content(), calls, m.toolCallId());
    }

    private static AgentMessage toAgentMessage(ChatMessage m) {
        List<AgentMessage.ToolCall> calls = m.toolCalls() == null ? null : m.toolCalls().stream()
                .map(c -> new AgentMessage.ToolCall(c.id(), c.function().name(), c.function().arguments()))
                .toList();
        return new AgentMessage(m.role(), m.content(), calls, m.toolCallId());
    }

    private static ChatRequest.Tool toWireTool(ToolDefinition tool) {
        return new ChatRequest.Tool("function", new ChatRequest.Tool.Function(tool.name(), tool.description(), tool.parameters()));
    }
}

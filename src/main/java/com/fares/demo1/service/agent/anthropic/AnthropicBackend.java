package com.fares.demo1.service.agent.anthropic;

import com.fares.demo1.service.agent.AgentBackend;
import com.fares.demo1.service.agent.AgentMessage;
import com.fares.demo1.service.agent.ToolDefinition;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Talks to Anthropic's Messages API directly over REST - no vendor SDK, same reasoning
 * as {@link com.fares.demo1.service.agent.openai.OpenAiCompatibleBackend}. Anthropic's
 * wire format differs from OpenAI's in two structural ways this class has to bridge:
 * (1) a message's content is a list of typed blocks (text / tool_use / tool_result)
 * rather than a plain string plus a separate tool_calls array, and (2) every tool
 * result produced in one turn must be sent back as ONE user message containing
 * multiple tool_result blocks, not one message per result the way OpenAI/Ollama want it.
 */
public class AnthropicBackend implements AgentBackend {

    private static final int MAX_OUTPUT_TOKENS = 4096;
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final RestClient restClient;
    private final String model;
    private final String apiKey;
    private final ObjectMapper objectMapper;

    public AnthropicBackend(RestClient restClient, String model, String apiKey, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.model = model;
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentMessage nextMessage(String systemPrompt, List<AgentMessage> history, List<ToolDefinition> tools) {
        MessagesRequest request = new MessagesRequest(
                model,
                MAX_OUTPUT_TOKENS,
                systemPrompt,
                toWireMessages(history),
                tools.stream()
                        .map(t -> new MessagesRequest.Tool(t.name(), t.description(), t.parameters()))
                        .toList());

        MessagesResponse response = restClient.post()
                .uri("/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .body(request)
                .retrieve()
                .body(MessagesResponse.class);

        return toAgentMessage(response);
    }

    /**
     * Folds the flat, one-message-per-tool-result {@link AgentMessage} history into
     * Anthropic's shape, where every tool result belonging to the same turn must be
     * grouped into a single user message.
     */
    private List<MessagesRequest.Message> toWireMessages(List<AgentMessage> history) {
        List<MessagesRequest.Message> messages = new ArrayList<>();
        List<ContentBlock> pendingToolResults = new ArrayList<>();

        for (AgentMessage m : history) {
            if ("tool".equals(m.role())) {
                pendingToolResults.add(new ContentBlock("tool_result", null, null, null, null, m.toolCallId(), m.content()));
                continue;
            }
            flushPendingToolResults(messages, pendingToolResults);

            if ("assistant".equals(m.role())) {
                messages.add(new MessagesRequest.Message("assistant", toAssistantBlocks(m)));
            } else {
                messages.add(new MessagesRequest.Message(m.role(), List.of(textBlock(m.content()))));
            }
        }
        flushPendingToolResults(messages, pendingToolResults);
        return messages;
    }

    private void flushPendingToolResults(List<MessagesRequest.Message> messages, List<ContentBlock> pendingToolResults) {
        if (!pendingToolResults.isEmpty()) {
            messages.add(new MessagesRequest.Message("user", List.copyOf(pendingToolResults)));
            pendingToolResults.clear();
        }
    }

    private List<ContentBlock> toAssistantBlocks(AgentMessage m) {
        List<ContentBlock> blocks = new ArrayList<>();
        if (m.content() != null) {
            blocks.add(textBlock(m.content()));
        }
        if (m.toolCalls() != null) {
            for (AgentMessage.ToolCall call : m.toolCalls()) {
                Object input = objectMapper.readValue(call.argumentsJson(), Object.class);
                blocks.add(new ContentBlock("tool_use", null, call.id(), call.name(), input, null, null));
            }
        }
        return blocks;
    }

    private static ContentBlock textBlock(String text) {
        return new ContentBlock("text", text, null, null, null, null, null);
    }

    private AgentMessage toAgentMessage(MessagesResponse response) {
        StringBuilder text = new StringBuilder();
        List<AgentMessage.ToolCall> calls = new ArrayList<>();
        for (ContentBlock block : response.content()) {
            if ("text".equals(block.type())) {
                text.append(block.text());
            } else if ("tool_use".equals(block.type())) {
                calls.add(new AgentMessage.ToolCall(block.id(), block.name(), objectMapper.writeValueAsString(block.input())));
            }
        }
        return new AgentMessage("assistant", text.isEmpty() ? null : text.toString(), calls.isEmpty() ? null : calls, null);
    }
}

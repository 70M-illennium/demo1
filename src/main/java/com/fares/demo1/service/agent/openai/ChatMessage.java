package com.fares.demo1.service.agent.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * One message in the OpenAI-compatible chat-completions wire format - the same shape
 * Ollama, real OpenAI, and Groq all speak, which is exactly why this package is named
 * "openai" rather than "ollama": switching between those providers later is a base URL
 * and a model string, not a rewrite of these types.
 *
 * <p>One record covers every role (system/user/assistant/tool) since the wire format
 * does too - only the fields relevant to a given role are populated, the rest stay
 * null and get omitted from the request JSON via {@link JsonInclude}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatMessage(
        String role,
        String content,
        @JsonProperty("tool_calls") List<ToolCall> toolCalls,
        @JsonProperty("tool_call_id") String toolCallId
) {

    /** A tool the assistant wants to call - part of an assistant-role message. */
    public record ToolCall(String id, String type, Function function) {

        /** Note: {@code arguments} is a JSON-encoded STRING per the wire format, not a nested object. */
        public record Function(String name, String arguments) {
        }
    }
}

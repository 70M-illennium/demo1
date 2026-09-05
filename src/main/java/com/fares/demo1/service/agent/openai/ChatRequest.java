package com.fares.demo1.service.agent.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** The OpenAI-compatible {@code POST /v1/chat/completions} request body. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatRequest(
        String model,
        List<ChatMessage> messages,
        List<Tool> tools,
        @JsonProperty("max_tokens") int maxTokens
) {

    public record Tool(String type, Function function) {

        /** {@code parameters} is the tool's raw JSON-schema {@code Map} - no schema-tree building needed. */
        public record Function(String name, String description, Object parameters) {
        }
    }
}

package com.fares.demo1.service.agent.anthropic;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** The Anthropic {@code POST /v1/messages} request body. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MessagesRequest(
        String model,
        @JsonProperty("max_tokens") int maxTokens,
        String system,
        List<Message> messages,
        List<Tool> tools
) {

    public record Message(String role, List<ContentBlock> content) {
    }

    /** {@code inputSchema} is the tool's raw JSON-schema {@code Map} - no schema-tree building needed. */
    public record Tool(String name, String description, @JsonProperty("input_schema") Object inputSchema) {
    }
}

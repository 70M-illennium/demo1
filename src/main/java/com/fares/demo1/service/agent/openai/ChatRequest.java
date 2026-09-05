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
        @JsonProperty("max_tokens") int maxTokens,
        @JsonProperty("response_format") ResponseFormat responseFormat,
        @JsonProperty("tool_choice") String toolChoice
) {

    public record Tool(String type, Function function) {

        /** {@code parameters} is the tool's raw JSON-schema {@code Map} - no schema-tree building needed. */
        public record Function(String name, String description, Object parameters) {
        }
    }

    /**
     * {@code {"type": "json_object"}} - grammar-constrained JSON output, enforced at the
     * decoding level rather than merely requested via the system prompt. Both Ollama and
     * real OpenAI support this field on this endpoint. Only affects the model's free-text
     * {@code content} when it isn't calling a tool - tool-call structure is unaffected.
     */
    public record ResponseFormat(String type) {
        public static final ResponseFormat JSON_OBJECT = new ResponseFormat("json_object");
    }
}

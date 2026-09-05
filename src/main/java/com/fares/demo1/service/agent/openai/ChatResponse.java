package com.fares.demo1.service.agent.openai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** The OpenAI-compatible {@code POST /v1/chat/completions} response body. */
public record ChatResponse(List<Choice> choices) {

    public record Choice(ChatMessage message, @JsonProperty("finish_reason") String finishReason) {
    }
}

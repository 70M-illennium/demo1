package com.fares.demo1.service.agent.anthropic;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** The Anthropic {@code POST /v1/messages} response body. */
public record MessagesResponse(List<ContentBlock> content, @JsonProperty("stop_reason") String stopReason) {
}

package com.fares.demo1.service.agent.tools;

import java.util.Map;

/** Shared by every tool in this package that accepts an optional integer argument. */
final class AgentToolInputs {

    private AgentToolInputs() {
    }

    static int intOrDefault(Map<String, Object> input, String key, int fallback) {
        Object value = input.get(key);
        return value instanceof Number n ? n.intValue() : fallback;
    }
}

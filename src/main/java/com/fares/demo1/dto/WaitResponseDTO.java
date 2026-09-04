package com.fares.demo1.dto;

import com.fares.demo1.model.WaitSample;

import java.time.Instant;

/** API view of one wait event from the latest cycle. */
public record WaitResponseDTO(
        Instant capturedAt,
        String eventName,
        long count,
        double totalWaitMs,
        double avgWaitMs
) {

    public static WaitResponseDTO from(WaitSample s) {
        return new WaitResponseDTO(
                s.getCapturedAt(),
                s.getEventName(),
                s.getCount(),
                Math.round(s.getTotalWaitMs() * 100.0) / 100.0,
                Math.round(s.getAvgWaitMs() * 1000.0) / 1000.0
        );
    }
}

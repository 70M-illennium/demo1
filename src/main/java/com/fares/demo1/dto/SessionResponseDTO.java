package com.fares.demo1.dto;

import com.fares.demo1.model.SessionSample;

import java.time.Instant;

/** API view of one live session from the latest cycle. */
public record SessionResponseDTO(
        Instant capturedAt,
        long connectionId,
        String user,
        String host,
        String db,
        String command,
        int timeSeconds,
        String state,
        String currentSql
) {

    public static SessionResponseDTO from(SessionSample s) {
        return new SessionResponseDTO(
                s.getCapturedAt(),
                s.getConnectionId(),
                s.getUser(),
                s.getHost(),
                s.getDb(),
                s.getCommand(),
                s.getTimeSeconds(),
                s.getState(),
                s.getCurrentSql()
        );
    }
}

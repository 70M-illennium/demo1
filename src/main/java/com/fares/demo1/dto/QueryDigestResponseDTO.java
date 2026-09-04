package com.fares.demo1.dto;

import com.fares.demo1.model.QueryDigestSample;

import java.time.Instant;

/**
 * API view of one query digest from the latest cycle. {@code rowsExaminedPerSent} is
 * the efficiency signal - a high value means the query reads far more than it returns
 * (missing index, bad join).
 */
public record QueryDigestResponseDTO(
        Instant capturedAt,
        String schemaName,
        String digest,
        String digestText,
        long execCount,
        double totalLatencyMs,
        double avgLatencyMs,
        long rowsExamined,
        long rowsSent,
        double rowsExaminedPerSent,
        long fullScans,
        long tmpDiskTables
) {

    public static QueryDigestResponseDTO from(QueryDigestSample s) {
        double ratio = s.getRowsSent() > 0
                ? Math.round((double) s.getRowsExamined() / s.getRowsSent() * 100.0) / 100.0
                : 0.0;
        return new QueryDigestResponseDTO(
                s.getCapturedAt(),
                s.getSchemaName(),
                s.getDigest(),
                s.getDigestText(),
                s.getExecCount(),
                Math.round(s.getTotalLatencyMs() * 100.0) / 100.0,
                Math.round(s.getAvgLatencyMs() * 1000.0) / 1000.0,
                s.getRowsExamined(),
                s.getRowsSent(),
                ratio,
                s.getFullScans(),
                s.getTmpDiskTables()
        );
    }
}

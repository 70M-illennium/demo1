package com.fares.demo1.dto;

import com.fares.demo1.model.TableSizeSample;

import java.time.Instant;

/**
 * API view of one table's size from the latest cycle. {@code fragmentationPct} is
 * {@code dataFree / (data + index)} - a high value means an {@code OPTIMIZE TABLE}
 * would reclaim space.
 */
public record TableSizeResponseDTO(
        Instant capturedAt,
        String schemaName,
        String tableName,
        String engine,
        long rowsEstimate,
        double totalMb,
        double dataMb,
        double indexMb,
        double dataFreeMb,
        double fragmentationPct
) {

    private static final double MB = 1024.0 * 1024.0;

    public static TableSizeResponseDTO from(TableSizeSample s) {
        long total = s.getDataBytes() + s.getIndexBytes();
        double frag = total > 0
                ? Math.round((double) s.getDataFreeBytes() / total * 10000.0) / 100.0
                : 0.0;
        return new TableSizeResponseDTO(
                s.getCapturedAt(),
                s.getSchemaName(),
                s.getTableName(),
                s.getEngine(),
                s.getRowsEstimate(),
                round2(total / MB),
                round2(s.getDataBytes() / MB),
                round2(s.getIndexBytes() / MB),
                round2(s.getDataFreeBytes() / MB),
                frag
        );
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

package com.fares.demo1.dto;

import com.fares.demo1.model.SecurityFinding;
import com.fares.demo1.model.Severity;

import java.time.Instant;

/** API view of one security-posture finding from the latest check cycle. */
public record SecurityFindingResponseDTO(
        Instant capturedAt,
        String category,
        Severity severity,
        String detail
) {

    public static SecurityFindingResponseDTO from(SecurityFinding f) {
        return new SecurityFindingResponseDTO(
                f.getCapturedAt(), f.getCategory(), f.getSeverity(), f.getDetail());
    }
}

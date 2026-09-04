package com.fares.demo1.dto;

/**
 * Partial update for {@code HealthCheckProperties} - every field is optional (null =
 * leave unchanged), so a client only sends the ones it wants to move. Field names and
 * meanings mirror {@code HealthCheckProperties} exactly; see that class's javadoc for
 * what each one controls.
 */
public record UpdateThresholdsRequest(
        Double diskWarnPercent,
        Double diskCritPercent,
        Long longTransactionSeconds,
        Double slowQueryRatePerMin,
        Double tmpDiskTableRatePerMin,
        Double bufferPoolHitRatioWarnPercent,
        Double bufferPoolHitRatioCritPercent,
        Double connectionWarnPercent,
        Double connectionCritPercent,
        Double latencySigmaK,
        Long collectionGapSeconds,
        Integer baselineWindow,
        Integer openAfter,
        Integer resolveAfter
) {
}

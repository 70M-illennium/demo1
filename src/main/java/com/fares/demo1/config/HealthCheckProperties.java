package com.fares.demo1.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Tunables for {@code HealthCheckService}, bound from {@code monitor.health.*} in
 * {@code application.properties}. Every field has an inline default, so the properties
 * file only needs an entry for values you want to change.
 */
@Component
@ConfigurationProperties("monitor.health")
@Getter
@Setter
public class HealthCheckProperties {

    /** Host filesystem usage (%) at which DISK_SPACE_LOW opens as WARNING. */
    private double diskWarnPercent = 90.0;

    /** Host filesystem usage (%) at which DISK_SPACE_LOW escalates to CRITICAL. */
    private double diskCritPercent = 95.0;

    /** Oldest open transaction age (seconds) that opens LONG_RUNNING_TRANSACTION. */
    private long longTransactionSeconds = 300;

    /** Slow-query accrual rate (per minute) that opens SLOW_QUERY_RATE_HIGH. */
    private double slowQueryRatePerMin = 10.0;

    /** Internal-disk temp-table creation rate (per minute) that opens TMP_DISK_TABLE_RATE_HIGH. */
    private double tmpDiskTableRatePerMin = 5.0;

    /** Buffer-pool hit ratio (%) over the last interval that opens BUFFER_POOL_HIT_RATIO_LOW as WARNING. */
    private double bufferPoolHitRatioWarnPercent = 95.0;

    /** Buffer-pool hit ratio (%) over the last interval that escalates to CRITICAL. */
    private double bufferPoolHitRatioCritPercent = 90.0;

    /** threads_connected / max_connections (%) that opens CONNECTION_SATURATION as WARNING. */
    private double connectionWarnPercent = 85.0;

    /** threads_connected / max_connections (%) that escalates CONNECTION_SATURATION to CRITICAL. */
    private double connectionCritPercent = 95.0;

    /** Standard deviations above the baseline mean that count as a CONNECT_LATENCY_ANOMALY. */
    private double latencySigmaK = 4.0;

    /** A gap between consecutive snapshots longer than this (seconds) is a COLLECTION_GAP. */
    private long collectionGapSeconds = 180;

    /** Number of snapshots used to compute the latency baseline. */
    private int baselineWindow = 20;

    /** Consecutive breaching snapshots before an event opens. */
    private int openAfter = 2;

    /** Consecutive clear snapshots before an event resolves. */
    private int resolveAfter = 3;

    /** How many snapshots the checker pulls each cycle. */
    public int fetchCount() {
        return baselineWindow + resolveAfter;
    }
}

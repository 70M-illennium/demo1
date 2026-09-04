package com.fares.demo1.dto;

/**
 * Byte -> human-unit conversions for API responses. Snapshots are stored in raw bytes
 * (the monitoring norm); this converts to MB / GB for display only, rounded to 2 decimals.
 */
final class Units {

    private static final double MB = 1024.0 * 1024.0;
    private static final double GB = MB * 1024.0;

    private Units() {
    }

    static double toMb(double bytes) {
        return round2(bytes / MB);
    }

    static double toGb(double bytes) {
        return round2(bytes / GB);
    }

    /** Round any value to 2 decimals (percentages, load averages), not just byte conversions. */
    static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

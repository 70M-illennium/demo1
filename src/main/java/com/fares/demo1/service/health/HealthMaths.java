package com.fares.demo1.service.health;

import com.fares.demo1.model.DatabaseSnapshotEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToLongFunction;

/**
 * Small numeric helpers shared by the rules: turning lifetime counters into
 * per-interval rates, and basic stats for the deviation checks.
 */
public final class HealthMaths {

    private HealthMaths() {
    }

    /**
     * Per-interval breach flags for a lifetime counter: for each adjacent snapshot pair
     * (newest first), is the counter climbing at {@code limitPerMin} or faster?
     *
     * @return the flags plus the most recent interval's rate, or {@code null} if there
     *         are fewer than two snapshots
     */
    public static Rate counterRate(List<DatabaseSnapshotEntity> db,
                                   ToLongFunction<DatabaseSnapshotEntity> counter,
                                   double limitPerMin) {
        if (db.size() < 2) {
            return null;
        }
        List<Boolean> breaches = new ArrayList<>();
        double latest = 0.0;
        for (int i = 0; i < db.size() - 1; i++) {
            DatabaseSnapshotEntity newer = db.get(i);
            DatabaseSnapshotEntity older = db.get(i + 1);
            double perMin = ratePerMinute(counter.applyAsLong(older), counter.applyAsLong(newer),
                    older.getTimestamp(), newer.getTimestamp());
            if (i == 0) {
                latest = perMin;
            }
            breaches.add(perMin >= limitPerMin);
        }
        return new Rate(breaches, latest);
    }

    public record Rate(List<Boolean> breaches, double latest) {
    }

    /** Increase per minute between two counter readings; 0 on a counter reset or bad timestamps. */
    public static double ratePerMinute(long oldValue, long newValue, Instant oldTime, Instant newTime) {
        long deltaValue = newValue - oldValue;
        long deltaSeconds = Duration.between(oldTime, newTime).getSeconds();
        if (deltaValue < 0 || deltaSeconds <= 0) {
            return 0.0;
        }
        return deltaValue / (deltaSeconds / 60.0);
    }

    public static double mean(double[] values) {
        if (values.length == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    public static double stddev(double[] values, double mean) {
        if (values.length == 0) {
            return 0.0;
        }
        double sumSq = 0.0;
        for (double v : values) {
            sumSq += (v - mean) * (v - mean);
        }
        return Math.sqrt(sumSq / values.length);
    }
}

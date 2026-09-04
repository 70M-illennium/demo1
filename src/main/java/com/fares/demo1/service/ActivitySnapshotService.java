package com.fares.demo1.service;

import com.fares.demo1.model.ActivitySnapshotEntity;
import com.fares.demo1.repo.ActivitySnapshotRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ActivitySnapshotService {

    private static final Duration LATEST_CACHE_TTL = Duration.ofSeconds(60);   // matches the capture() cadence

    private final ActivitySnapshotRepo activitySnapshotRepo;
    private final SnapshotWriteBuffer writeBuffer;
    private final MetricCache metricCache;

    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

    // ---------- reads for the API ----------

    /** The most recent activity snapshot, or empty if none has been collected yet. */
    @Transactional(readOnly = true)
    public Optional<ActivitySnapshotEntity> latestSnapshot() {
        return metricCache.getOrLoad("activity.latest", LATEST_CACHE_TTL,
                activitySnapshotRepo::findFirstByOrderByTimestampDesc);
    }

    /** The most recent {@code limit} activity snapshots, newest first. */
    @Transactional(readOnly = true)
    public List<ActivitySnapshotEntity> recentSnapshots(int limit) {
        return activitySnapshotRepo.findByOrderByTimestampDesc(PageRequest.of(0, limit));
    }

    // ---------- collection ----------

    @Scheduled(fixedRate = 60_000, initialDelay = 10_000)   // every 60s, wait 10s at startup
    public void capture() {
        try {
            ActivitySnapshotEntity snapshot = new ActivitySnapshotEntity();
            snapshot.setTimestamp(Instant.now());

            MemoryUsage heap = memoryBean.getHeapMemoryUsage();
            snapshot.setJvmHeapUsedBytes(heap.getUsed());
            snapshot.setJvmHeapMaxBytes(heap.getMax());   // -1 if the JVM has no defined max

            // httpRequestsTotal / httpRequests5xx / httpRequestDurationP95Ms / loginFailures
            // need Actuator + Micrometer (http) and Spring Security (login) - left at 0.

            log.info("Activity snapshot: heapUsed={}MB heapMax={}MB",
                    heap.getUsed() / 1024 / 1024,
                    heap.getMax() / 1024 / 1024);
            writeBuffer.save(() -> activitySnapshotRepo.save(snapshot));
        } catch (Exception ex) {
            log.error("Activity metrics collection failed", ex);   // don't let one failure kill the scheduler
        }
    }
}

package com.fares.demo1.service;

import com.fares.demo1.model.HostSystemSnapshotEntity;
import com.fares.demo1.repo.HostSystemSnapshotRepo;
import com.sun.management.OperatingSystemMXBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class HostSystemSnapshotService {

    private static final Duration LATEST_CACHE_TTL = Duration.ofSeconds(60);   // matches the capture() cadence

    private final HostSystemSnapshotRepo hostSystemSnapshotRepo;
    private final SnapshotWriteBuffer writeBuffer;
    private final MetricCache metricCache;

    private final OperatingSystemMXBean osBean =
            (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    // ---------- reads for the API ----------

    /**
     * The most recent host snapshot, or empty if none has been collected yet. Goes
     * through {@link MetricCache} under the key {@code "host.latest"} - see {@code
     * DatabaseSnapshotService.latestSnapshot()} for why this isn't {@code @Cacheable}.
     */
    @Transactional(readOnly = true)
    public Optional<HostSystemSnapshotEntity> latestSnapshot() {
        return metricCache.getOrLoad("host.latest", LATEST_CACHE_TTL,
                hostSystemSnapshotRepo::findFirstByOrderByTimestampDesc);
    }

    /** The most recent {@code limit} host snapshots, newest first. */
    @Transactional(readOnly = true)
    public List<HostSystemSnapshotEntity> recentSnapshots(int limit) {
        return hostSystemSnapshotRepo.findByOrderByTimestampDesc(PageRequest.of(0, limit));
    }

    // ---------- collection ----------

    @Scheduled(fixedRate = 60_000, initialDelay = 10_000)   // every 60s, wait 10s at startup
    public void capture() {
        try {
            HostSystemSnapshotEntity snapshot = new HostSystemSnapshotEntity();
            snapshot.setTimestamp(Instant.now());

            // CPU: getCpuLoad() is 0..1, or negative before the first sampling window elapses
            double cpuLoad = osBean.getCpuLoad();
            snapshot.setCpuUsagePercent(cpuLoad < 0 ? 0.0 : cpuLoad * 100.0);

            // Memory: physical RAM used vs total
            long totalMemory = osBean.getTotalMemorySize();
            long freeMemory = osBean.getFreeMemorySize();
            snapshot.setMemoryUsagePercent(totalMemory == 0 ? 0.0
                    : (double) (totalMemory - freeMemory) / totalMemory * 100.0);

            // Load average (1 minute); -1 on platforms that do not report it (e.g. Windows)
            double load = osBean.getSystemLoadAverage();
            snapshot.setLoadAverage1m(load < 0 ? 0.0 : load);

            // Filesystem the app is running on
            File root = new File("/");
            long totalDisk = root.getTotalSpace();
            long usableDisk = root.getUsableSpace();
            snapshot.setFilesystemFreeBytes(usableDisk);
            snapshot.setDiskUsagePercent(totalDisk == 0 ? 0.0
                    : (double) (totalDisk - usableDisk) / totalDisk * 100.0);

            // swapUsedBytes needs OSHI or /proc parsing - left at 0 until that decision.

            log.info("Host snapshot: cpu={}% mem={}% disk={}% load={}",
                    Math.round(snapshot.getCpuUsagePercent()),
                    Math.round(snapshot.getMemoryUsagePercent()),
                    Math.round(snapshot.getDiskUsagePercent()),
                    snapshot.getLoadAverage1m());
            writeBuffer.save(() -> hostSystemSnapshotRepo.save(snapshot));
        } catch (Exception ex) {
            log.error("Host metrics collection failed", ex);   // don't let one failure kill the scheduler
        }
    }
}

package com.fares.demo1.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(indexes = @Index(name = "idx_host_snapshot_timestamp", columnList = "timestamp"))
@Getter
@Setter
public class HostSystemSnapshotEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)


    private long id;
    private Instant timestamp;


    // critical metric //double for % long for counters
    private double cpuUsagePercent;
    private long swapUsedBytes;
    private double diskUsagePercent;
    private long filesystemFreeBytes;
    // critical metric//
    private double loadAverage1m;
    private double memoryUsagePercent;

}

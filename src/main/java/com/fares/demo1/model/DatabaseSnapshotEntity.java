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
@Table(indexes = @Index(name = "idx_db_snapshot_timestamp", columnList = "timestamp"))
@Getter
@Setter
public class DatabaseSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)   // id is assigned by the database, never set from code
    //all//
    private long id;
    private Instant timestamp;

    //metrics critical//
    private boolean reachable;
    private int threadsRunning;
    private long slowQueries;
    private int innodbRowLockCurrentWaits;

    //metrics high//
    private long connectLatencyMs;
    private long uptimeSeconds;
    private int threadsConnected;
    private int maxUsedConnections;
    private long questions;
    private long createdTmpDiskTables;
    private long innodbBufferPoolReadRequests;
    private long innodbBufferPoolReads;
    private long innodbDeadlocks;
    private long oldestTransactionAgeSeconds;
    private double databaseSizeBytes;
}

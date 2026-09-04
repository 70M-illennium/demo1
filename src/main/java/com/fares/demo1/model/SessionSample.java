package com.fares.demo1.model;

import jakarta.persistence.Column;
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

/**
 * One row per non-idle connection on the target at collection time, from
 * {@code information_schema.PROCESSLIST}. This is the "what is running right now" view -
 * long-running statements and lock waits show up here. The monitor's own connection and
 * background daemons are filtered out.
 */
@Entity
@Table(indexes = @Index(name = "idx_session_sample_captured_at", columnList = "capturedAt"))
@Getter
@Setter
public class SessionSample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    private Instant capturedAt;

    private long connectionId;
    private String user;
    private String host;
    private String db;

    /** Query, Execute, Change user, ... */
    private String command;

    /** Seconds the connection has been in its current state. */
    private int timeSeconds;

    /** "Sending data", "Waiting for table metadata lock", "updating", ... */
    private String state;

    /** The statement currently running, truncated. */
    @Column(length = 512)
    private String currentSql;
}

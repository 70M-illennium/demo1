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
@Table(indexes = @Index(name = "idx_activity_snapshot_timestamp", columnList = "timestamp"))
@Setter
@Getter
public class ActivitySnapshotEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private long id;
    private Instant timestamp;

    // jvm - collected now
    private long jvmHeapUsedBytes;
    private long jvmHeapMaxBytes;

    // http + login - need Spring Boot Actuator/Micrometer (http.server.requests) and
    // Spring Security (login). Fields present for structure; not collected yet.
    private long httpRequestsTotal;
    private long httpRequests5xx;
    private long httpRequestDurationP95Ms;
    private long loginFailures;
}

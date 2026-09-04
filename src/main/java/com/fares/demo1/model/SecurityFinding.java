package com.fares.demo1.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * One thing the security-posture checks flagged on the target in a given cycle - a weak
 * global setting, or an account with no password / a wildcard host. A cycle that finds
 * nothing writes no rows; the absence of recent findings is the "clean" signal.
 */
@Entity
@Table(indexes = @Index(name = "idx_security_finding_captured_at", columnList = "capturedAt"))
@Getter
@Setter
public class SecurityFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    private Instant capturedAt;

    /** WEAK_CONFIG, ACCOUNT_NO_PASSWORD, ACCOUNT_WILDCARD_HOST, ... */
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(32)")
    private Severity severity;

    private String detail;
}

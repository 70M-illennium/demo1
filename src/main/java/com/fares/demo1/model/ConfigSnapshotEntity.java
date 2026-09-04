package com.fares.demo1.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * The header for one captured set of the target's GLOBAL VARIABLES. A new header (with
 * a fresh set of {@link ConfigValueSample} rows) is written only when a tracked value
 * changes - so the row count is the number of config changes, not one per minute. The
 * individual values live in {@code ConfigValueSample}, so tracking another variable is
 * a list change, not a schema change.
 */
@Entity
@Getter
@Setter
public class ConfigSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private long id;

    /** When this configuration was first observed. */
    private Instant timestamp;
}

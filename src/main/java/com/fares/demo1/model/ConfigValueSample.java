package com.fares.demo1.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * One tracked GLOBAL VARIABLE and its value, belonging to a {@link ConfigSnapshotEntity}.
 */
@Entity
@Getter
@Setter
public class ConfigValueSample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    @ManyToOne(optional = false)
    private ConfigSnapshotEntity snapshot;

    private String name;
    private String value;
}

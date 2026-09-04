package com.fares.demo1.repo;

import com.fares.demo1.model.ConfigSnapshotEntity;
import com.fares.demo1.model.ConfigValueSample;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConfigValueSampleRepo extends JpaRepository<ConfigValueSample, Long> {

    List<ConfigValueSample> findBySnapshotOrderByName(ConfigSnapshotEntity snapshot);
}

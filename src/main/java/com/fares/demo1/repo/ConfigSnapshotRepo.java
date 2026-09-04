package com.fares.demo1.repo;

import com.fares.demo1.model.ConfigSnapshotEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConfigSnapshotRepo extends JpaRepository<ConfigSnapshotEntity, Long> {

    Optional<ConfigSnapshotEntity> findFirstByOrderByTimestampDesc();

    // newest first; caller passes Pageable to cap how many rows come back
    List<ConfigSnapshotEntity> findByOrderByTimestampDesc(Pageable pageable);

}

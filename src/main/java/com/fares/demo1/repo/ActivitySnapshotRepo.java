package com.fares.demo1.repo;

import com.fares.demo1.model.ActivitySnapshotEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ActivitySnapshotRepo extends JpaRepository<ActivitySnapshotEntity, Long > {

    Optional<ActivitySnapshotEntity> findFirstByOrderByTimestampDesc();

    List<ActivitySnapshotEntity> findByOrderByTimestampDesc(Pageable pageable);

    /** Bulk-delete snapshots older than the cutoff. Returns the row count. */
    @Modifying
    @Query("delete from ActivitySnapshotEntity e where e.timestamp < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);

}

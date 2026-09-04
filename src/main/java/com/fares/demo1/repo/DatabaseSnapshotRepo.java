package com.fares.demo1.repo;

import com.fares.demo1.model.DatabaseSnapshotEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DatabaseSnapshotRepo extends JpaRepository<DatabaseSnapshotEntity,Long> {

    Optional<DatabaseSnapshotEntity> findFirstByOrderByTimestampDesc();

    // newest first; caller passes Pageable to cap how many rows come back
    List<DatabaseSnapshotEntity> findByOrderByTimestampDesc(Pageable pageable);

    /** Bulk-delete snapshots older than the cutoff. Returns the row count. */
    @Modifying
    @Query("delete from DatabaseSnapshotEntity e where e.timestamp < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);

}

package com.fares.demo1.repo;

import com.fares.demo1.model.SecurityFinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SecurityFindingRepo extends JpaRepository<SecurityFinding, Long> {

    /** All findings from the most recent check cycle (the API re-sorts by severity). */
    @Query("select f from SecurityFinding f where f.capturedAt = "
            + "(select max(x.capturedAt) from SecurityFinding x) order by f.category")
    List<SecurityFinding> findLatestCycle();

    @Modifying
    @Query("delete from SecurityFinding f where f.capturedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}

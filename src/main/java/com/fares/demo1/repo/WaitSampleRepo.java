package com.fares.demo1.repo;

import com.fares.demo1.model.WaitSample;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface WaitSampleRepo extends JpaRepository<WaitSample, Long> {

    /** All wait events from the most recent collection cycle, most time first. */
    @Query("select s from WaitSample s where s.capturedAt = "
            + "(select max(x.capturedAt) from WaitSample x) order by s.totalWaitMs desc")
    List<WaitSample> findLatestCycle();

    @Modifying
    @Query("delete from WaitSample s where s.capturedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}

package com.fares.demo1.repo;

import com.fares.demo1.model.SessionSample;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SessionSampleRepo extends JpaRepository<SessionSample, Long> {

    /** All sessions from the most recent collection cycle, longest-running first. */
    @Query("select s from SessionSample s where s.capturedAt = "
            + "(select max(x.capturedAt) from SessionSample x) order by s.timeSeconds desc")
    List<SessionSample> findLatestCycle();

    @Modifying
    @Query("delete from SessionSample s where s.capturedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}

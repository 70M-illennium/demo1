package com.fares.demo1.repo;

import com.fares.demo1.model.QueryDigestSample;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface QueryDigestSampleRepo extends JpaRepository<QueryDigestSample, Long> {

    /** All digests from the most recent collection cycle, slowest first. */
    @Query("select s from QueryDigestSample s where s.capturedAt = "
            + "(select max(x.capturedAt) from QueryDigestSample x) order by s.totalLatencyMs desc")
    List<QueryDigestSample> findLatestCycle();

    @Modifying
    @Query("delete from QueryDigestSample s where s.capturedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}

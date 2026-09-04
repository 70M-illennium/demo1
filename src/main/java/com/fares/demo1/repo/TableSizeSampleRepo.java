package com.fares.demo1.repo;

import com.fares.demo1.model.TableSizeSample;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface TableSizeSampleRepo extends JpaRepository<TableSizeSample, Long> {

    /** All tables from the most recent collection cycle, largest first. */
    @Query("select s from TableSizeSample s where s.capturedAt = "
            + "(select max(x.capturedAt) from TableSizeSample x) "
            + "order by s.dataBytes + s.indexBytes desc")
    List<TableSizeSample> findLatestCycle();

    @Modifying
    @Query("delete from TableSizeSample s where s.capturedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}

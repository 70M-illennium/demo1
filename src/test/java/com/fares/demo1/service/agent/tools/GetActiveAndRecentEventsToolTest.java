package com.fares.demo1.service.agent.tools;

import com.fares.demo1.model.EventType;
import com.fares.demo1.model.MonitorEventEntity;
import com.fares.demo1.model.Severity;
import com.fares.demo1.repo.MonitorEventRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetActiveAndRecentEventsToolTest {

    @Mock
    private MonitorEventRepo monitorEventRepo;

    @InjectMocks
    private GetActiveAndRecentEventsTool tool;

    private static final AtomicLong NEXT_ID = new AtomicLong(1);

    private static MonitorEventEntity event(EventType type, Severity severity) {
        MonitorEventEntity e = new MonitorEventEntity();
        // id has no public setter (JPA assigns it on save) - set it via reflection, the
        // same way ReflectionTestUtils is meant to be used for entities under test.
        ReflectionTestUtils.setField(e, "id", NEXT_ID.getAndIncrement());
        e.setType(type);
        e.setSeverity(severity);
        e.setOccurredAt(Instant.now());
        e.setLastSeenAt(Instant.now());
        return e;
    }

    @Test
    void namesAndSchemaAreSane() {
        assertThat(tool.name()).isEqualTo("get_active_and_recent_events");
    }

    @Test
    void executeReturnsActiveSortedBySeverityAndRecentBoundedByLimit() {
        MonitorEventEntity warn = event(EventType.DISK_SPACE_LOW, Severity.WARNING);
        MonitorEventEntity critical = event(EventType.DB_UNREACHABLE, Severity.CRITICAL);
        when(monitorEventRepo.findByResolvedAtIsNullOrderByOccurredAtDesc())
                .thenReturn(List.of(warn, critical));   // deliberately unsorted input
        when(monitorEventRepo.findByOrderByOccurredAtDesc(PageRequest.of(0, 5)))
                .thenReturn(List.of(critical));

        Object result = tool.execute(Map.of("limit", 5));

        assertThat(result).isInstanceOf(GetActiveAndRecentEventsTool.EventsView.class);
        GetActiveAndRecentEventsTool.EventsView view = (GetActiveAndRecentEventsTool.EventsView) result;
        assertThat(view.active()).hasSize(2);
        assertThat(view.active().get(0).severity()).isEqualTo(Severity.CRITICAL);   // sorted, most severe first
        assertThat(view.recent()).hasSize(1);
    }
}

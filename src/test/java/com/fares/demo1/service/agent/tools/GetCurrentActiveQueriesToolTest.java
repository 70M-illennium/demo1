package com.fares.demo1.service.agent.tools;

import com.fares.demo1.dto.SessionResponseDTO;
import com.fares.demo1.model.SessionSample;
import com.fares.demo1.service.WorkloadSnapshotService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCurrentActiveQueriesToolTest {

    @Mock
    private WorkloadSnapshotService workloadSnapshotService;

    @InjectMocks
    private GetCurrentActiveQueriesTool tool;

    @Test
    void namesAndSchemaAreSane() {
        assertThat(tool.name()).isEqualTo("get_current_active_queries");
        assertThat(tool.description()).isNotBlank();
        assertThat(tool.inputSchema()).containsEntry("type", "object");
    }

    @Test
    void executeCallsTheLiveReadNotTheCachedSnapshot_andMapsToDTOs() {
        SessionSample session = new SessionSample();
        session.setConnectionId(42L);
        session.setCommand("Query");
        session.setCurrentSql("SELECT * FROM big_table");
        when(workloadSnapshotService.currentActiveQueries()).thenReturn(List.of(session));

        Object result = tool.execute(Map.of());

        verify(workloadSnapshotService).currentActiveQueries();
        assertThat(result).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<SessionResponseDTO> dtos = (List<SessionResponseDTO>) result;
        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).connectionId()).isEqualTo(42L);
        assertThat(dtos.get(0).currentSql()).isEqualTo("SELECT * FROM big_table");
    }

    @Test
    void executeWithNoActiveQueries_returnsAnEmptyList() {
        when(workloadSnapshotService.currentActiveQueries()).thenReturn(List.of());

        Object result = tool.execute(Map.of());

        assertThat((List<?>) result).isEmpty();
    }
}

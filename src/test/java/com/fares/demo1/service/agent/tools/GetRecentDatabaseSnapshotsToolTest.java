package com.fares.demo1.service.agent.tools;

import com.fares.demo1.dto.DatabaseSnapshotResponseDTO;
import com.fares.demo1.model.DatabaseSnapshotEntity;
import com.fares.demo1.service.DatabaseSnapshotService;
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
class GetRecentDatabaseSnapshotsToolTest {

    @Mock
    private DatabaseSnapshotService databaseSnapshotService;

    @InjectMocks
    private GetRecentDatabaseSnapshotsTool tool;

    @Test
    void namesAndSchemaAreSane() {
        assertThat(tool.name()).isEqualTo("get_recent_database_snapshots");
        assertThat(tool.description()).isNotBlank();
        assertThat(tool.inputSchema()).containsEntry("type", "object");
    }

    @Test
    void executeUsesTheProvidedLimitAndMapsToDTOs() {
        DatabaseSnapshotEntity entity = new DatabaseSnapshotEntity();
        entity.setReachable(true);
        when(databaseSnapshotService.recentSnapshots(3)).thenReturn(List.of(entity));

        Object result = tool.execute(Map.of("limit", 3));

        verify(databaseSnapshotService).recentSnapshots(3);
        assertThat(result).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<DatabaseSnapshotResponseDTO> dtos = (List<DatabaseSnapshotResponseDTO>) result;
        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).availability().reachable()).isTrue();
    }

    @Test
    void executeFallsBackToDefaultLimitWhenNotProvided() {
        when(databaseSnapshotService.recentSnapshots(5)).thenReturn(List.of());

        tool.execute(Map.of());

        verify(databaseSnapshotService).recentSnapshots(5);
    }
}

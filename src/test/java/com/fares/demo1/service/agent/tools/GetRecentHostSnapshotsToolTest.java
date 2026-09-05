package com.fares.demo1.service.agent.tools;

import com.fares.demo1.dto.HostSystemSnapshotResponseDTO;
import com.fares.demo1.model.HostSystemSnapshotEntity;
import com.fares.demo1.service.HostSystemSnapshotService;
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
class GetRecentHostSnapshotsToolTest {

    @Mock
    private HostSystemSnapshotService hostSystemSnapshotService;

    @InjectMocks
    private GetRecentHostSnapshotsTool tool;

    @Test
    void namesAndSchemaAreSane() {
        assertThat(tool.name()).isEqualTo("get_recent_host_snapshots");
        assertThat(tool.inputSchema()).containsEntry("type", "object");
    }

    @Test
    void executeUsesTheProvidedLimitAndMapsToDTOs() {
        HostSystemSnapshotEntity entity = new HostSystemSnapshotEntity();
        when(hostSystemSnapshotService.recentSnapshots(2)).thenReturn(List.of(entity));

        Object result = tool.execute(Map.of("limit", 2));

        verify(hostSystemSnapshotService).recentSnapshots(2);
        @SuppressWarnings("unchecked")
        List<HostSystemSnapshotResponseDTO> dtos = (List<HostSystemSnapshotResponseDTO>) result;
        assertThat(dtos).hasSize(1);
    }
}

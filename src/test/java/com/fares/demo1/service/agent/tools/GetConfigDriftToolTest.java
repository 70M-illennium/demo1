package com.fares.demo1.service.agent.tools;

import com.fares.demo1.dto.ConfigSnapshotResponseDTO;
import com.fares.demo1.model.ConfigSnapshotEntity;
import com.fares.demo1.service.ConfigSnapshotService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetConfigDriftToolTest {

    @Mock
    private ConfigSnapshotService configSnapshotService;

    @InjectMocks
    private GetConfigDriftTool tool;

    @Test
    void namesAndSchemaAreSane() {
        assertThat(tool.name()).isEqualTo("get_config_drift");
    }

    @Test
    void executeJoinsEachSnapshotWithItsValues() {
        ConfigSnapshotEntity snapshot = new ConfigSnapshotEntity();
        snapshot.setTimestamp(Instant.now());
        when(configSnapshotService.recentSnapshots(4)).thenReturn(List.of(snapshot));
        when(configSnapshotService.valuesOf(snapshot)).thenReturn(Map.of("max_connections", "200"));

        Object result = tool.execute(Map.of("limit", 4));

        verify(configSnapshotService).recentSnapshots(4);
        @SuppressWarnings("unchecked")
        List<ConfigSnapshotResponseDTO> dtos = (List<ConfigSnapshotResponseDTO>) result;
        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).values()).containsEntry("max_connections", "200");
    }
}

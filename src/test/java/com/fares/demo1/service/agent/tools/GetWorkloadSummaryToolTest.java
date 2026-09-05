package com.fares.demo1.service.agent.tools;

import com.fares.demo1.model.QueryDigestSample;
import com.fares.demo1.model.SessionSample;
import com.fares.demo1.model.TableSizeSample;
import com.fares.demo1.model.WaitSample;
import com.fares.demo1.service.WorkloadSnapshotService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetWorkloadSummaryToolTest {

    @Mock
    private WorkloadSnapshotService workloadSnapshotService;

    @InjectMocks
    private GetWorkloadSummaryTool tool;

    @Test
    void namesAndSchemaAreSane() {
        assertThat(tool.name()).isEqualTo("get_workload_summary");
        assertThat(tool.inputSchema()).containsEntry("type", "object");
    }

    @Test
    void executeCombinesAllFourWorkloadReads() {
        QueryDigestSample digest = new QueryDigestSample();
        digest.setCapturedAt(Instant.now());
        SessionSample session = new SessionSample();
        session.setCapturedAt(Instant.now());
        WaitSample wait = new WaitSample();
        wait.setCapturedAt(Instant.now());
        TableSizeSample table = new TableSizeSample();
        table.setCapturedAt(Instant.now());

        when(workloadSnapshotService.latestDigests()).thenReturn(List.of(digest));
        when(workloadSnapshotService.latestSessions()).thenReturn(List.of(session));
        when(workloadSnapshotService.latestWaits()).thenReturn(List.of(wait));
        when(workloadSnapshotService.latestTableSizes()).thenReturn(List.of(table));

        Object result = tool.execute(Map.of());

        assertThat(result).isInstanceOf(GetWorkloadSummaryTool.WorkloadView.class);
        GetWorkloadSummaryTool.WorkloadView view = (GetWorkloadSummaryTool.WorkloadView) result;
        assertThat(view.queries()).hasSize(1);
        assertThat(view.sessions()).hasSize(1);
        assertThat(view.waits()).hasSize(1);
        assertThat(view.tables()).hasSize(1);
    }
}

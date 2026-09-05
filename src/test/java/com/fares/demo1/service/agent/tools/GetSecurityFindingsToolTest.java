package com.fares.demo1.service.agent.tools;

import com.fares.demo1.dto.SecurityFindingResponseDTO;
import com.fares.demo1.model.SecurityFinding;
import com.fares.demo1.model.Severity;
import com.fares.demo1.service.SecurityCheckService;
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
class GetSecurityFindingsToolTest {

    @Mock
    private SecurityCheckService securityCheckService;

    @InjectMocks
    private GetSecurityFindingsTool tool;

    private static SecurityFinding finding(Severity severity) {
        SecurityFinding f = new SecurityFinding();
        f.setCapturedAt(Instant.now());
        f.setCategory("WEAK_CONFIG");
        f.setSeverity(severity);
        f.setDetail("test finding");
        return f;
    }

    @Test
    void namesAndSchemaAreSane() {
        assertThat(tool.name()).isEqualTo("get_security_findings");
        assertThat(tool.inputSchema()).containsEntry("type", "object");
    }

    @Test
    void executeSortsMostSevereFirst() {
        when(securityCheckService.latestFindings())
                .thenReturn(List.of(finding(Severity.WARNING), finding(Severity.CRITICAL)));

        Object result = tool.execute(Map.of());

        @SuppressWarnings("unchecked")
        List<SecurityFindingResponseDTO> dtos = (List<SecurityFindingResponseDTO>) result;
        assertThat(dtos).hasSize(2);
        assertThat(dtos.get(0).severity()).isEqualTo(Severity.CRITICAL);
    }
}

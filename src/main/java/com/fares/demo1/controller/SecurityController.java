package com.fares.demo1.controller;

import com.fares.demo1.dto.SecurityFindingResponseDTO;
import com.fares.demo1.model.SecurityFinding;
import com.fares.demo1.service.SecurityCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/**
 * The target's security posture as of the most recent check cycle.
 *
 * <pre>
 *   GET /api/security/findings   weak settings / risky accounts, most severe first
 * </pre>
 *
 * An empty list means the last check cycle found nothing.
 */
@RestController
@RequestMapping("/api/security")
@RequiredArgsConstructor
public class SecurityController {

    private final SecurityCheckService securityCheckService;

    @GetMapping("/findings")
    public List<SecurityFindingResponseDTO> findings() {
        return securityCheckService.latestFindings().stream()
                .sorted(Comparator.comparing(SecurityFinding::getSeverity).reversed())
                .map(SecurityFindingResponseDTO::from)
                .toList();
    }
}

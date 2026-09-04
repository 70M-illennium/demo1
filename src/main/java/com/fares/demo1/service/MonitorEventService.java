package com.fares.demo1.service;

import com.fares.demo1.repo.MonitorEventRepo;
import org.springframework.stereotype.Service;

/**
 * Records MonitorEventEntity rows (restarts, deadlocks, config drift, alert
 * transitions) and resolves them when the condition clears. Called by the other
 * services when they detect an event; it is not a scheduled collector itself.
 */
@Service
public class MonitorEventService {

    private final MonitorEventRepo monitorEventRepo;

    public MonitorEventService(MonitorEventRepo monitorEventRepo) {
        this.monitorEventRepo = monitorEventRepo;
    }
}

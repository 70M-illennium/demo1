package com.fares.demo1.service;

import com.fares.demo1.model.MonitorEventEntity;
import com.fares.demo1.model.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default {@link Notifier}: writes the transition to the application log - ERROR for a
 * CRITICAL open, WARN otherwise, INFO on resolve.
 */
@Component
public class LoggingNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotifier.class);

    @Override
    public void onOpened(MonitorEventEntity event) {
        if (event.getSeverity() == Severity.CRITICAL) {
            log.error("ALERT OPENED [{}] {} - {}", event.getSeverity(), event.getType(), event.getMessage());
        } else {
            log.warn("ALERT OPENED [{}] {} - {}", event.getSeverity(), event.getType(), event.getMessage());
        }
    }

    @Override
    public void onResolved(MonitorEventEntity event) {
        log.info("ALERT RESOLVED [{}] {} - {}", event.getSeverity(), event.getType(), event.getMessage());
    }
}

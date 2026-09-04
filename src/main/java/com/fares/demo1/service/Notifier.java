package com.fares.demo1.service;

import com.fares.demo1.model.MonitorEventEntity;

/**
 * Sends word when a {@link MonitorEventEntity} opens or resolves. The only
 * implementation for now is {@link LoggingNotifier}; a webhook / email notifier can be
 * added later without touching the checker.
 */
public interface Notifier {

    void onOpened(MonitorEventEntity event);

    void onResolved(MonitorEventEntity event);
}

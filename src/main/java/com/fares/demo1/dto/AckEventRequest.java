package com.fares.demo1.dto;

/**
 * Body for {@code PUT /api/events/{id}/ack}. Deliberately its own tiny type instead of
 * binding the request straight to {@code MonitorEventEntity} - a client can only ever
 * send the one field it's actually allowed to set.
 */
public record AckEventRequest(String note) {
}

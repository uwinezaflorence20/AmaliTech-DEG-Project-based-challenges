package com.critmon.pulsecheck.service;

import com.critmon.pulsecheck.model.CreateMonitorRequest;
import com.critmon.pulsecheck.model.Monitor;
import com.critmon.pulsecheck.model.MonitorStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Service
public class MonitorService {

    private static final Logger log = LoggerFactory.getLogger(MonitorService.class);

    private final ConcurrentHashMap<String, Monitor> monitors = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    private final ObjectMapper objectMapper = new ObjectMapper();

    // US1 — Register a monitor and start its countdown
    public Monitor createMonitor(CreateMonitorRequest request) {
        if (monitors.containsKey(request.getId())) {
            throw new IllegalArgumentException(
                    "Monitor '" + request.getId() + "' already exists. Delete it first or send a heartbeat.");
        }

        Monitor monitor = new Monitor(request.getId(), request.getTimeout(), request.getAlertEmail());
        monitors.put(monitor.getId(), monitor);
        scheduleAlert(monitor);

        log.info("Monitor created: id={} timeout={}s alert={}", monitor.getId(), monitor.getTimeout(), monitor.getAlertEmail());
        return monitor;
    }

    // US2 — Reset the countdown; also un-pauses a paused monitor (Bonus US)
    public Monitor heartbeat(String id) {
        Monitor monitor = getOrThrow(id);

        cancelTimer(monitor);
        monitor.setLastHeartbeat(Instant.now());
        monitor.setStatus(MonitorStatus.ACTIVE);
        scheduleAlert(monitor);

        log.info("Heartbeat: id={} timer reset to {}s", id, monitor.getTimeout());
        return monitor;
    }

    // Bonus US — Pause monitoring; timer stops and no alert will fire
    public Monitor pause(String id) {
        Monitor monitor = getOrThrow(id);

        if (monitor.getStatus() == MonitorStatus.DOWN) {
            throw new IllegalStateException("Cannot pause a DOWN monitor. Re-register it instead.");
        }
        if (monitor.getStatus() == MonitorStatus.PAUSED) {
            return monitor; // idempotent
        }

        cancelTimer(monitor);
        monitor.setStatus(MonitorStatus.PAUSED);
        monitor.setExpiresAt(null);

        log.info("Monitor paused: id={}", id);
        return monitor;
    }

    // Developer's Choice — inspect the live status and time remaining
    public Monitor getMonitor(String id) {
        return getOrThrow(id);
    }

    // Developer's Choice — list all registered monitors
    public Collection<Monitor> getAllMonitors() {
        return monitors.values();
    }

    // Developer's Choice — deregister a monitor cleanly
    public void deleteMonitor(String id) {
        Monitor monitor = getOrThrow(id);
        cancelTimer(monitor);
        monitors.remove(id);
        log.info("Monitor deleted: id={}", id);
    }

    private void scheduleAlert(Monitor monitor) {
        ScheduledFuture<?> future = scheduler.schedule(
                () -> fireAlert(monitor),
                monitor.getTimeout(),
                TimeUnit.SECONDS
        );
        monitor.setScheduledFuture(future);
        monitor.setExpiresAt(Instant.now().plusSeconds(monitor.getTimeout()));
    }

    private void cancelTimer(Monitor monitor) {
        ScheduledFuture<?> future = monitor.getScheduledFuture();
        if (future != null && !future.isDone()) {
            future.cancel(false);
        }
    }

    // US3 — Fire the alert when the countdown reaches zero
    private void fireAlert(Monitor monitor) {
        monitor.setStatus(MonitorStatus.DOWN);
        monitor.setExpiresAt(null);

        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("ALERT", "Device " + monitor.getId() + " is down!");
        alert.put("time", Instant.now().toString());
        alert.put("deviceId", monitor.getId());
        alert.put("alertEmail", monitor.getAlertEmail());

        try {
            log.error(objectMapper.writeValueAsString(alert));
        } catch (Exception e) {
            log.error("ALERT: Device {} is down!", monitor.getId());
        }
    }

    private Monitor getOrThrow(String id) {
        Monitor monitor = monitors.get(id);
        if (monitor == null) {
            throw new NoSuchElementException("Monitor not found: " + id);
        }
        return monitor;
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}

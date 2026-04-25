package com.critmon.pulsecheck.controller;

import com.critmon.pulsecheck.model.CreateMonitorRequest;
import com.critmon.pulsecheck.model.Monitor;
import com.critmon.pulsecheck.service.MonitorService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/monitors")
public class MonitorController {

    private final MonitorService monitorService;

    public MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    // US1 — Register a new monitor
    @PostMapping
    public ResponseEntity<Map<String, Object>> createMonitor(@Valid @RequestBody CreateMonitorRequest request) {
        Monitor monitor = monitorService.createMonitor(request);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Monitor registered. Countdown started.");
        body.put("monitorId", monitor.getId());
        body.put("timeout", monitor.getTimeout());
        body.put("alertEmail", monitor.getAlertEmail());
        body.put("status", monitor.getStatus());
        return ResponseEntity.status(201).body(body);
    }

    // US2 — Send a heartbeat to reset the timer (also un-pauses the monitor)
    @PostMapping("/{id}/heartbeat")
    public ResponseEntity<Map<String, Object>> heartbeat(@PathVariable String id) {
        Monitor monitor = monitorService.heartbeat(id);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Heartbeat received. Timer reset.");
        body.put("monitorId", monitor.getId());
        body.put("status", monitor.getStatus());
        body.put("lastHeartbeat", monitor.getLastHeartbeat().toString());
        body.put("secondsRemaining", monitor.getSecondsRemaining());
        return ResponseEntity.ok(body);
    }

    // Bonus US — Pause monitoring to suppress false alarms during maintenance
    @PostMapping("/{id}/pause")
    public ResponseEntity<Map<String, Object>> pause(@PathVariable String id) {
        Monitor monitor = monitorService.pause(id);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Monitor paused. No alerts will fire until a heartbeat is received.");
        body.put("monitorId", monitor.getId());
        body.put("status", monitor.getStatus());
        return ResponseEntity.ok(body);
    }

    // Developer's Choice — get live status and time remaining for a single monitor
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getMonitor(@PathVariable String id) {
        Monitor monitor = monitorService.getMonitor(id);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", monitor.getId());
        body.put("status", monitor.getStatus());
        body.put("timeout", monitor.getTimeout());
        body.put("alertEmail", monitor.getAlertEmail());
        body.put("secondsRemaining", monitor.getSecondsRemaining());
        body.put("lastHeartbeat", monitor.getLastHeartbeat() != null ? monitor.getLastHeartbeat().toString() : null);
        body.put("expiresAt", monitor.getExpiresAt() != null ? monitor.getExpiresAt().toString() : null);
        body.put("createdAt", monitor.getCreatedAt().toString());
        return ResponseEntity.ok(body);
    }

    // Developer's Choice — list all registered monitors
    @GetMapping
    public ResponseEntity<Collection<Monitor>> getAllMonitors() {
        return ResponseEntity.ok(monitorService.getAllMonitors());
    }

    // Developer's Choice — deregister a monitor
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteMonitor(@PathVariable String id) {
        monitorService.deleteMonitor(id);
        return ResponseEntity.ok(Map.of(
                "message", "Monitor deregistered successfully.",
                "monitorId", id
        ));
    }
}

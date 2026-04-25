package com.critmon.pulsecheck.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ScheduledFuture;

public class Monitor {

    private String id;
    private int timeout;
    private String alertEmail;
    private MonitorStatus status;
    private Instant createdAt;
    private Instant lastHeartbeat;
    private Instant expiresAt;

    @JsonIgnore
    private ScheduledFuture<?> scheduledFuture;

    public Monitor(String id, int timeout, String alertEmail) {
        this.id = id;
        this.timeout = timeout;
        this.alertEmail = alertEmail;
        this.status = MonitorStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.lastHeartbeat = Instant.now();
    }

    public long getSecondsRemaining() {
        if (status != MonitorStatus.ACTIVE || expiresAt == null) return 0;
        long remaining = Instant.now().until(expiresAt, ChronoUnit.SECONDS);
        return Math.max(0, remaining);
    }

    public String getId() { return id; }
    public int getTimeout() { return timeout; }
    public String getAlertEmail() { return alertEmail; }

    public MonitorStatus getStatus() { return status; }
    public void setStatus(MonitorStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(Instant lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    @JsonIgnore
    public ScheduledFuture<?> getScheduledFuture() { return scheduledFuture; }
    public void setScheduledFuture(ScheduledFuture<?> scheduledFuture) { this.scheduledFuture = scheduledFuture; }
}

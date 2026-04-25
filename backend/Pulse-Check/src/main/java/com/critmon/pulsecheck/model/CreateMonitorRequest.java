package com.critmon.pulsecheck.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateMonitorRequest {

    @NotBlank(message = "Device ID is required")
    private String id;

    @NotNull(message = "Timeout is required")
    @Min(value = 1, message = "Timeout must be at least 1 second")
    private Integer timeout;

    @NotBlank(message = "Alert email is required")
    @Email(message = "Alert email must be a valid email address")
    private String alertEmail;

    public CreateMonitorRequest() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Integer getTimeout() { return timeout; }
    public void setTimeout(Integer timeout) { this.timeout = timeout; }

    public String getAlertEmail() { return alertEmail; }
    public void setAlertEmail(String alertEmail) { this.alertEmail = alertEmail; }
}

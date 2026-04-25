package com.critmon.pulsecheck;

import com.critmon.pulsecheck.model.CreateMonitorRequest;
import com.critmon.pulsecheck.model.Monitor;
import com.critmon.pulsecheck.model.MonitorStatus;
import com.critmon.pulsecheck.service.MonitorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PulseCheckApplicationTests {

    @Autowired
    private MonitorService monitorService;

    @Test
    void contextLoads() {
    }

    @Test
    void createMonitorSetsStatusToActive() {
        Monitor monitor = monitorService.createMonitor(request("test-device-1", 60, "admin@critmon.com"));
        assertThat(monitor.getStatus()).isEqualTo(MonitorStatus.ACTIVE);
        monitorService.deleteMonitor("test-device-1");
    }

    @Test
    void duplicateRegistrationThrows() {
        monitorService.createMonitor(request("test-device-2", 60, "admin@critmon.com"));
        assertThatThrownBy(() -> monitorService.createMonitor(request("test-device-2", 30, "other@critmon.com")))
                .isInstanceOf(IllegalArgumentException.class);
        monitorService.deleteMonitor("test-device-2");
    }

    @Test
    void heartbeatResetsTimerAndStatusIsActive() {
        monitorService.createMonitor(request("test-device-3", 60, "admin@critmon.com"));
        monitorService.pause("test-device-3");
        Monitor resumed = monitorService.heartbeat("test-device-3");
        assertThat(resumed.getStatus()).isEqualTo(MonitorStatus.ACTIVE);
        monitorService.deleteMonitor("test-device-3");
    }

    @Test
    void pauseSetStatusToPaused() {
        monitorService.createMonitor(request("test-device-4", 60, "admin@critmon.com"));
        Monitor paused = monitorService.pause("test-device-4");
        assertThat(paused.getStatus()).isEqualTo(MonitorStatus.PAUSED);
        monitorService.deleteMonitor("test-device-4");
    }

    @Test
    void heartbeatOnUnknownIdThrows() {
        assertThatThrownBy(() -> monitorService.heartbeat("non-existent-device"))
                .isInstanceOf(NoSuchElementException.class);
    }

    private CreateMonitorRequest request(String id, int timeout, String email) {
        CreateMonitorRequest req = new CreateMonitorRequest();
        req.setId(id);
        req.setTimeout(timeout);
        req.setAlertEmail(email);
        return req;
    }
}

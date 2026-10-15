package com.selfhealing.monitoring_service.controller;

import com.selfhealing.monitoring_service.logging.RecoveryEvent;
import com.selfhealing.monitoring_service.logging.RecoveryEventLogger;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/recovery-events")
public class RecoveryEventController {

    private final RecoveryEventLogger recoveryEventLogger;

    public RecoveryEventController(
            RecoveryEventLogger recoveryEventLogger) {

        this.recoveryEventLogger =
                recoveryEventLogger;
    }

    @GetMapping
    public List<RecoveryEvent> getEvents() {

        return recoveryEventLogger.getEvents();
    }
}
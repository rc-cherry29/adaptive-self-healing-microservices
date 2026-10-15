package com.selfhealing.monitoring_service.logging;

import com.selfhealing.monitoring_service.repository.RecoveryEventRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RecoveryEventLogger {

    private final RecoveryEventRepository repository;

    public RecoveryEventLogger(
            RecoveryEventRepository repository) {

        this.repository = repository;
    }

    public void logEvent(RecoveryEvent event) {
        repository.save(event);
    }

    public List<RecoveryEvent> getEvents() {
        return repository.findAll();
    }
}
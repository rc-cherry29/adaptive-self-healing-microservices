package com.selfhealing.product_service.health;

import com.selfhealing.product_service.controller.FaultInjectionController;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class FaultHealthIndicator implements HealthIndicator {

    private final FaultInjectionController faultInjectionController;

    public FaultHealthIndicator(
            FaultInjectionController faultInjectionController) {
        this.faultInjectionController = faultInjectionController;
    }

    @Override
    public Health health() {

        if (faultInjectionController.isFailureEnabled()) {
            return Health.down()
                    .withDetail("reason", "Simulated Product Service Failure")
                    .build();
        }

        return Health.up().build();
    }
}
package com.selfhealing.monitoring_service.baseline;

import org.springframework.stereotype.Component;

@Component
public class BaselineDecisionEngine {

    public String decideRecoveryAction(
            String failureType,
            int failureCount) {

        // Healthy service
        if ("HEALTHY".equals(failureType)) {
            return "NO_ACTION";
        }

        // Service completely unavailable
        if ("SERVICE_DOWN".equals(failureType)) {

            // Simple fixed rule:
            // First two failures -> retry
            if (failureCount <= 2) {
                return "RETRY";
            }

            // After repeated failures -> restart
            return "RESTART";
        }

        // Slow service
        if ("SLOW_RESPONSE".equals(failureType)) {
            return "RETRY";
        }

        // Unknown failure
        return "RETRY";
    }
}
package com.selfhealing.monitoring_service.classifier;

import org.springframework.stereotype.Component;

@Component
public class FailureClassifier {

    public String classify(boolean reachable, long responseTime) {

        if (!reachable) {
            return "SERVICE_DOWN";
        }

        if (responseTime > 2000) {
            return "SLOW_RESPONSE";
        }

        return "HEALTHY";
    }
}
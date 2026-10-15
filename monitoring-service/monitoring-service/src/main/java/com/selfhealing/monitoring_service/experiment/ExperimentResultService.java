
package com.selfhealing.monitoring_service.experiment;

import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class ExperimentResultService {

    private final ExperimentResultRepository experimentResultRepository;

    public ExperimentResultService(
            ExperimentResultRepository experimentResultRepository) {

        this.experimentResultRepository = experimentResultRepository;
    }

    // Existing method: keeps old calls working
    public void saveExperimentResult(
            String decisionMode,
            String serviceName,
            String failureType,
            String recoveryAction,
            String verificationResult,
            int failureCount,
            long failureDetectionLatencyMs,
            long recoveryTimeMs,
            long downtimeMs) {

        saveExperimentResult(
                decisionMode,
                serviceName,
                failureType,
                recoveryAction,
                verificationResult,
                failureCount,
                failureDetectionLatencyMs,
                recoveryTimeMs,
                downtimeMs,
                null,
                null
        );
    }

    // New method: supports both additional measurements
    public void saveExperimentResult(
            String decisionMode,
            String serviceName,
            String failureType,
            String recoveryAction,
            String verificationResult,
            int failureCount,
            long failureDetectionLatencyMs,
            long recoveryTimeMs,
            long downtimeMs,
            Long gatewayRecoveryTimeMs,
            Long primaryDowntimeMs) {

        ExperimentResult result = new ExperimentResult();

        result.setDecisionMode(decisionMode);
        result.setServiceName(serviceName);
        result.setFailureType(failureType);
        result.setRecoveryAction(recoveryAction);
        result.setVerificationResult(verificationResult);
        result.setFailureCount(failureCount);
        result.setFailureDetectionLatencyMs(failureDetectionLatencyMs);
        result.setRecoveryTimeMs(recoveryTimeMs);
        result.setDowntimeMs(downtimeMs);

        result.setGatewayRecoveryTimeMs(gatewayRecoveryTimeMs);
        result.setPrimaryDowntimeMs(primaryDowntimeMs);

        result.setTimestamp(LocalDateTime.now());

        experimentResultRepository.save(result);

        System.out.println(
                ">>> EXPERIMENT RESULT SAVED"
                        + " | Mode: " + decisionMode
                        + " | Service: " + serviceName
                        + " | Failure: " + failureType
                        + " | Action: " + recoveryAction
                        + " | Verification: " + verificationResult
                        + " | Failure Count: " + failureCount
                        + " | Detection Latency: " + failureDetectionLatencyMs + " ms"
                        + " | Recovery Time: " + recoveryTimeMs + " ms"
                        + " | Downtime: " + downtimeMs + " ms"
                        + " | Gateway Recovery: " + gatewayRecoveryTimeMs + " ms"
                        + " | Primary Downtime: " + primaryDowntimeMs + " ms"
        );
    }
}

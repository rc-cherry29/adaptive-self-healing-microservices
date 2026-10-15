
package com.selfhealing.monitoring_service.experiment;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "experiment_results")
public class ExperimentResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String decisionMode;
    private String serviceName;
    private String failureType;
    private String recoveryAction;
    private String verificationResult;

    private int failureCount;
    private long failureDetectionLatencyMs;
    private long recoveryTimeMs;
    private long downtimeMs;

    @Column(name = "gateway_recovery_time_ms")
    private Long gatewayRecoveryTimeMs;

    @Column(name = "primary_downtime_ms")
    private Long primaryDowntimeMs;

    private LocalDateTime timestamp;

    public ExperimentResult() {
    }

    public Long getId() {
        return id;
    }

    public String getDecisionMode() {
        return decisionMode;
    }

    public void setDecisionMode(String decisionMode) {
        this.decisionMode = decisionMode;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getFailureType() {
        return failureType;
    }

    public void setFailureType(String failureType) {
        this.failureType = failureType;
    }

    public String getRecoveryAction() {
        return recoveryAction;
    }

    public void setRecoveryAction(String recoveryAction) {
        this.recoveryAction = recoveryAction;
    }

    public String getVerificationResult() {
        return verificationResult;
    }

    public void setVerificationResult(String verificationResult) {
        this.verificationResult = verificationResult;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public long getFailureDetectionLatencyMs() {
        return failureDetectionLatencyMs;
    }

    public void setFailureDetectionLatencyMs(long failureDetectionLatencyMs) {
        this.failureDetectionLatencyMs = failureDetectionLatencyMs;
    }

    public long getRecoveryTimeMs() {
        return recoveryTimeMs;
    }

    public void setRecoveryTimeMs(long recoveryTimeMs) {
        this.recoveryTimeMs = recoveryTimeMs;
    }

    public long getDowntimeMs() {
        return downtimeMs;
    }

    public void setDowntimeMs(long downtimeMs) {
        this.downtimeMs = downtimeMs;
    }

    public Long getGatewayRecoveryTimeMs() {
        return gatewayRecoveryTimeMs;
    }

    public void setGatewayRecoveryTimeMs(Long gatewayRecoveryTimeMs) {
        this.gatewayRecoveryTimeMs = gatewayRecoveryTimeMs;
    }

    public Long getPrimaryDowntimeMs() {
        return primaryDowntimeMs;
    }

    public void setPrimaryDowntimeMs(Long primaryDowntimeMs) {
        this.primaryDowntimeMs = primaryDowntimeMs;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}

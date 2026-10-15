package com.selfhealing.monitoring_service.logging;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "recovery_events")
public class RecoveryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String serviceName;
    private String failureType;
    private int failureCount;
    private String nrdaAction;
    private String recoveryResult;
    private String verificationResult;

    private LocalDateTime timestamp;

    private long recoveryTimeMs;
    private long downtimeMs;
    private long failureDetectionLatencyMs;

    // Required by JPA
    public RecoveryEvent() {
    }

    public RecoveryEvent(
            String serviceName,
            String failureType,
            int failureCount,
            String nrdaAction,
            String recoveryResult,
            String verificationResult,
            long recoveryTimeMs,
            long downtimeMs,
            long failureDetectionLatencyMs) {

        this.serviceName = serviceName;
        this.failureType = failureType;
        this.failureCount = failureCount;
        this.nrdaAction = nrdaAction;
        this.recoveryResult = recoveryResult;
        this.verificationResult = verificationResult;

        this.recoveryTimeMs = recoveryTimeMs;
        this.downtimeMs = downtimeMs;
        this.failureDetectionLatencyMs =
                failureDetectionLatencyMs;

        this.timestamp = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getFailureType() {
        return failureType;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public String getNrdaAction() {
        return nrdaAction;
    }

    public String getRecoveryResult() {
        return recoveryResult;
    }

    public String getVerificationResult() {
        return verificationResult;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public long getRecoveryTimeMs() {
        return recoveryTimeMs;
    }

    public long getDowntimeMs() {
        return downtimeMs;
    }

    public long getFailureDetectionLatencyMs() {
        return failureDetectionLatencyMs;
    }
}
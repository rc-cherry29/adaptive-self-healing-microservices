package com.selfhealing.monitoring_service.model;

public class ServiceHealthRecord {

    private String serviceName;
    private String status;
    private int failureCount;
    private long responseTime;
    private String failureType;

    private Long failureDetectedAt;
    private long lastDowntimeMs;

    // Stores latency between fault injection
    // and first detection by Monitoring Service
    private long failureDetectionLatencyMs;

    public ServiceHealthRecord(String serviceName) {

        this.serviceName = serviceName;
        this.status = "UNKNOWN";
        this.failureCount = 0;
        this.responseTime = 0;
        this.failureType = "UNKNOWN";

        this.failureDetectedAt = null;
        this.lastDowntimeMs = 0;
        this.failureDetectionLatencyMs = 0;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getStatus() {
        return status;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public long getResponseTime() {
        return responseTime;
    }

    public String getFailureType() {
        return failureType;
    }

    public Long getFailureDetectedAt() {
        return failureDetectedAt;
    }

    public long getLastDowntimeMs() {
        return lastDowntimeMs;
    }

    public long getFailureDetectionLatencyMs() {
        return failureDetectionLatencyMs;
    }

    public void setFailureDetectionLatencyMs(
            long failureDetectionLatencyMs) {

        this.failureDetectionLatencyMs =
                failureDetectionLatencyMs;
    }

    public void markDown(String failureType) {

        this.status = "DOWN";
        this.failureCount++;
        this.responseTime = 0;
        this.failureType = failureType;

        // First detection of this continuous outage
        if (failureDetectedAt == null) {

            failureDetectedAt =
                    System.currentTimeMillis();
        }
    }

    public void markUp(
            long responseTime,
            String failureType) {

        this.status = "UP";
        this.responseTime = responseTime;
        this.failureType = failureType;

        if ("SLOW_RESPONSE".equals(failureType)) {

            // Track consecutive degraded monitoring cycles
            this.failureCount++;

            // Service remains reachable, so this is not downtime
            return;
        }

        if (failureDetectedAt != null) {

            lastDowntimeMs =
                    System.currentTimeMillis()
                            - failureDetectedAt;

            failureDetectedAt = null;
        }

        // Reset only after a healthy response
        this.failureCount = 0;

        this.failureDetectionLatencyMs = 0;
    }

    public long getCurrentDowntimeMs() {

        if (failureDetectedAt == null) {
            return 0;
        }

        return System.currentTimeMillis()
                - failureDetectedAt;
    }
    public boolean wasDown() {
        return failureDetectedAt != null;
    }
}
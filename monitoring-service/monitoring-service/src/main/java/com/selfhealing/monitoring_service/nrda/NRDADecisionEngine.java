
package com.selfhealing.monitoring_service.nrda;

import org.springframework.stereotype.Component;

@Component
public class NRDADecisionEngine {

    // ==========================================
    // NRDA WEIGHTS
    // Total = 1.00
    // ==========================================

    private static final double FAILURE_WEIGHT = 0.25;
    private static final double FREQUENCY_WEIGHT = 0.15;
    private static final double RESPONSE_TIME_WEIGHT = 0.10;
    private static final double CRITICALITY_WEIGHT = 0.15;
    private static final double DEPENDENCY_WEIGHT = 0.10;
    private static final double RECOVERY_RISK_WEIGHT = 0.10;
    private static final double CPU_WEIGHT = 0.075;
    private static final double MEMORY_WEIGHT = 0.075;

    // ==========================================
    // NRDA DECISION THRESHOLDS
    // ==========================================

    private static final double RECOVERY_THRESHOLD = 5.5;

    private static final int DOWN_ESCALATION_COUNT = 6;
    private static final int SLOW_ESCALATION_COUNT = 6;

    // ==========================================
    // MAIN NRDA DECISION METHOD
    // ==========================================

    public String decideRecoveryAction(
            String failureType,
            int failureCount,
            long responseTime,
            int serviceCriticality,
            boolean dependencyHealthy,
            double recoveryRisk,
            double cpuUsage,
            double memoryUtilization,
            boolean healthyReplicaAvailable) {

        // Healthy service requires no recovery.
        if ("HEALTHY".equals(failureType)) {
            return "NO_ACTION";
        }

        // ==========================================
        // 1. FAILURE SEVERITY SCORE
        // ==========================================

        double failureSeverity =
                calculateFailureSeverity(failureType);

        // ==========================================
        // 2. FAILURE FREQUENCY SCORE
        // ==========================================

        double failureFrequency =
                calculateFailureFrequency(failureCount);

        // ==========================================
        // 3. RESPONSE TIME SCORE
        // ==========================================

        double responseTimeScore =
                calculateResponseTimeScore(responseTime);

        // ==========================================
        // 4. SERVICE CRITICALITY SCORE
        // ==========================================

        double criticalityScore =
                serviceCriticality;

        // ==========================================
        // 5. DEPENDENCY RISK SCORE
        // ==========================================

        double dependencyScore =
                dependencyHealthy ? 0.0 : 10.0;

        // ==========================================
        // 6. RECOVERY HISTORY / RISK SCORE
        // ==========================================

        double recoveryRiskScore =
                recoveryRisk;

        // ==========================================
        // 7. CPU RESOURCE SCORE
        // ==========================================

        double cpuScore =
                calculateResourceScore(cpuUsage);

        // ==========================================
        // 8. MEMORY RESOURCE SCORE
        // ==========================================

        double memoryScore =
                calculateResourceScore(memoryUtilization);

        // ==========================================
        // FAILURE-AWARE RESOURCE INFLUENCE
        // ==========================================

        double effectiveCpuScore = cpuScore;
        double effectiveMemoryScore = memoryScore;

        /*
         * When the service is completely DOWN,
         * CPU and memory utilization are not used
         * to influence the recovery decision.
         */

        if ("SERVICE_DOWN".equals(failureType)) {
            effectiveCpuScore = 0.0;
            effectiveMemoryScore = 0.0;
        }

        // ==========================================
        // RESOURCE SCORE LOGGING
        // ==========================================

        System.out.println(
                ">>> NRDA RESOURCE SCORE"
                        + " | CPU Usage: "
                        + String.format("%.2f", cpuUsage * 100)
                        + "%"
                        + " | CPU Score: "
                        + cpuScore
                        + " | Effective CPU Score: "
                        + effectiveCpuScore
                        + " | Memory Utilization: "
                        + String.format("%.2f", memoryUtilization * 100)
                        + "%"
                        + " | Memory Score: "
                        + memoryScore
                        + " | Effective Memory Score: "
                        + effectiveMemoryScore
        );

        // ==========================================
        // FINAL WEIGHTED NRDA SCORE
        // ==========================================

        double nrdaScore =
                (failureSeverity * FAILURE_WEIGHT)
                        + (failureFrequency * FREQUENCY_WEIGHT)
                        + (responseTimeScore * RESPONSE_TIME_WEIGHT)
                        + (criticalityScore * CRITICALITY_WEIGHT)
                        + (dependencyScore * DEPENDENCY_WEIGHT)
                        + (recoveryRiskScore * RECOVERY_RISK_WEIGHT)
                        + (effectiveCpuScore * CPU_WEIGHT)
                        + (effectiveMemoryScore * MEMORY_WEIGHT);

        // ==========================================
        // COMPLETE NRDA SCORE LOG
        // ==========================================

        System.out.println(
                ">>> NRDA SCORE"
                        + " | Failure Type: "
                        + failureType
                        + " | Severity: "
                        + failureSeverity
                        + " | Frequency: "
                        + failureFrequency
                        + " | Response: "
                        + responseTimeScore
                        + " | Criticality: "
                        + criticalityScore
                        + " | Dependency: "
                        + dependencyScore
                        + " | Recovery Risk: "
                        + recoveryRiskScore
                        + " | CPU Score: "
                        + effectiveCpuScore
                        + " | Memory Score: "
                        + effectiveMemoryScore
                        + " | Final Score: "
                        + String.format("%.2f", nrdaScore)
        );

        // ==========================================
        // 1. REPLICA-AWARE REROUTE
        // ==========================================

        /*
         * During the first two consecutive failures,
         * prefer rerouting to an available healthy
         * replica instead of restarting immediately.
         */

        if ("SERVICE_DOWN".equals(failureType)
                && healthyReplicaAvailable
                && failureCount <= 2) {

            System.out.println(
                    ">>> NRDA REPLICA-AWARE DECISION"
                            + " | Healthy Replica Available: true"
                            + " | Failure Count: " + failureCount
                            + " | Action: REROUTE"
            );

            return "REROUTE";
        }

        // ==========================================
        // 2. PERSISTENT SERVICE DOWN ESCALATION
        // ==========================================

        /*
         * A persistent failure should not remain
         * in an endless RETRY or RESTART cycle.
         *
         * When a healthy replica exists, attempt
         * SWITCH_TO_BACKUP.
         *
         * The API Gateway independently checks
         * whether the designated backup on port
         * 8086 is healthy before activation.
         */

        if ("SERVICE_DOWN".equals(failureType)
                && failureCount >= DOWN_ESCALATION_COUNT) {

            if (healthyReplicaAvailable) {

                System.out.println(
                        ">>> NRDA BACKUP ESCALATION"
                                + " | Failure Type: SERVICE_DOWN"
                                + " | Failure Count: " + failureCount
                                + " | Healthy Replica Available: true"
                                + " | NRDA Score: "
                                + String.format("%.2f", nrdaScore)
                                + " | Action: SWITCH_TO_BACKUP"
                );

                return "SWITCH_TO_BACKUP";
            }

            System.out.println(
                    ">>> NRDA ESCALATION"
                            + " | Failure Type: SERVICE_DOWN"
                            + " | Failure Count: " + failureCount
                            + " | Healthy Replica Available: false"
                            + " | Action: RESTART"
            );

            return "RESTART";
        }

        // ==========================================
        // 3. DEPENDENCY-AWARE DECISION
        // ==========================================

        /*
         * If a dependency is unhealthy, restarting
         * or scaling the current service may not
         * resolve the underlying failure.
         */

        if (!dependencyHealthy
                && ("SERVICE_DOWN".equals(failureType)
                || "SLOW_RESPONSE".equals(failureType))) {

            System.out.println(
                    ">>> NRDA DEPENDENCY DECISION"
                            + " | Failure Type: " + failureType
                            + " | Dependency Healthy: false"
                            + " | Action: REROUTE"
            );

            return "REROUTE";
        }

        // ==========================================
        // 4. SERVICE DOWN DECISION
        // ==========================================

        if ("SERVICE_DOWN".equals(failureType)) {

            if (nrdaScore >= RECOVERY_THRESHOLD) {

                System.out.println(
                        ">>> NRDA SCORE-BASED DECISION"
                                + " | Failure Type: SERVICE_DOWN"
                                + " | Failure Count: " + failureCount
                                + " | NRDA Score: "
                                + String.format("%.2f", nrdaScore)
                                + " | Action: RESTART"
                );

                return "RESTART";
            }

            System.out.println(
                    ">>> NRDA SCORE-BASED DECISION"
                            + " | Failure Type: SERVICE_DOWN"
                            + " | Failure Count: " + failureCount
                            + " | Action: RETRY"
            );

            return "RETRY";
        }

        // ==========================================
        // 5. SLOW RESPONSE DECISION
        // ==========================================

        if ("SLOW_RESPONSE".equals(failureType)) {

            /*
             * Persistent slow responses trigger
             * SCALE_OUT after six consecutive
             * monitoring failures.
             */

            if (failureCount >= SLOW_ESCALATION_COUNT) {

                System.out.println(
                        ">>> NRDA ESCALATION"
                                + " | Failure Type: SLOW_RESPONSE"
                                + " | Consecutive Failures: "
                                + failureCount
                                + " | NRDA Score: "
                                + String.format("%.2f", nrdaScore)
                                + " | Action: SCALE_OUT"
                );

                return "SCALE_OUT";
            }

            /*
             * High NRDA scores can trigger scaling
             * before the escalation count is reached.
             */

            if (nrdaScore >= RECOVERY_THRESHOLD) {

                System.out.println(
                        ">>> NRDA SCORE-BASED DECISION"
                                + " | Failure Type: SLOW_RESPONSE"
                                + " | NRDA Score: "
                                + String.format("%.2f", nrdaScore)
                                + " | Action: SCALE_OUT"
                );

                return "SCALE_OUT";
            }

            return "RETRY";
        }

        // ==========================================
        // 6. UNKNOWN FAILURE
        // ==========================================

        return "RETRY";
    }

    // ==========================================
    // FAILURE SEVERITY
    // ==========================================

    private double calculateFailureSeverity(
            String failureType) {

        if ("SERVICE_DOWN".equals(failureType)) {
            return 10.0;
        }

        if ("SLOW_RESPONSE".equals(failureType)) {
            return 6.0;
        }

        return 2.0;
    }

    // ==========================================
    // FAILURE FREQUENCY
    // ==========================================

    private double calculateFailureFrequency(
            int failureCount) {

        if (failureCount <= 1) {
            return 2.0;
        }

        if (failureCount <= 3) {
            return 4.0;
        }

        if (failureCount <= 5) {
            return 6.0;
        }

        if (failureCount <= 7) {
            return 8.0;
        }

        return 10.0;
    }

    // ==========================================
    // RESPONSE TIME SCORE
    // ==========================================

    private double calculateResponseTimeScore(
            long responseTime) {

        if (responseTime <= 1000) {
            return 2.0;
        }

        if (responseTime <= 2000) {
            return 4.0;
        }

        if (responseTime <= 5000) {
            return 7.0;
        }

        return 10.0;
    }

    // ==========================================
    // CPU / MEMORY RESOURCE SCORE
    // ==========================================

    private double calculateResourceScore(
            double utilization) {

        if (utilization <= 0.40) {
            return 2.0;
        }

        if (utilization <= 0.60) {
            return 4.0;
        }

        if (utilization <= 0.80) {
            return 7.0;
        }

        return 10.0;
    }
}

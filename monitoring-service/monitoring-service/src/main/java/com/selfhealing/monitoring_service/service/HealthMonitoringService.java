
package com.selfhealing.monitoring_service.service;

import com.selfhealing.monitoring_service.baseline.BaselineDecisionEngine;
import com.selfhealing.monitoring_service.classifier.FailureClassifier;
import com.selfhealing.monitoring_service.experiment.ExperimentResultService;
import com.selfhealing.monitoring_service.logging.RecoveryEvent;
import com.selfhealing.monitoring_service.logging.RecoveryEventLogger;
import com.selfhealing.monitoring_service.model.ServiceHealthRecord;
import com.selfhealing.monitoring_service.nrda.NRDADecisionEngine;
import com.selfhealing.monitoring_service.recovery.RecoveryExecutor;
import com.selfhealing.monitoring_service.recovery.RecoveryVerifier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HealthMonitoringService {

    private static final String PRODUCT_SERVICE = "PRODUCT-SERVICE";
    private static final String ORDER_SERVICE = "ORDER-SERVICE";

    private static final String PRIMARY_URL =
            "http://localhost:8081";

    private static final String GATEWAY_BACKUP_URL =
            "http://localhost:8080/internal/backup";

    private static final String GATEWAY_PRODUCT_PROBE_URL =
            "http://localhost:8080/products";

    private static final long RESTART_COOLDOWN_MS = 30000;
    private static final long SCALE_OUT_COOLDOWN_MS = 60000;
    private static final int FAILBACK_HEALTHY_CHECKS = 3;

    private final DiscoveryClient discoveryClient;

    private final RestClient restClient;
    private final RestClient failbackClient;
    private final RestClient gatewayProbeClient;

    private final NRDADecisionEngine nrdaDecisionEngine;
    private final BaselineDecisionEngine baselineDecisionEngine;
    private final FailureClassifier failureClassifier;

    private final RecoveryExecutor recoveryExecutor;
    private final RecoveryVerifier recoveryVerifier;
    private final RecoveryEventLogger recoveryEventLogger;

    private final RecoveryHistoryService recoveryHistoryService;
    private final ResourceMetricsService resourceMetricsService;
    private final ExperimentResultService experimentResultService;

    @Value("${recovery.decision-mode:NRDA}")
    private String decisionMode;

    private final Map<String, ServiceHealthRecord> healthRecords =
            new HashMap<>();

    private final Map<String, Long> lastRestartTime =
            new HashMap<>();

    private final Map<String, String> lastRecoveryAction =
            new HashMap<>();

    private final Map<String, Long> lastRecoveryTime =
            new HashMap<>();

    private final Map<String, String> lastVerificationResult =
            new HashMap<>();

    private final Map<String, Long> failureDetectedAtMs =
            new HashMap<>();

    private final Map<String, Long> gatewayRecoveryTimeMs =
            new HashMap<>();

    private final Map<String, Boolean> gatewayProbeAttemptedSinceDetection =
            new HashMap<>();

    private final Map<String, Long> lastScaleOutAttemptTime =
            new HashMap<>();

    private final Map<String, Boolean> scaleOutMaxReached =
            new HashMap<>();

    // Track slow-response incidents separately.
    private final Map<String, Long> slowResponseStartedAtMs =
            new HashMap<>();

    private final Map<String, Integer> slowResponseFailureCount =
            new HashMap<>();

    private int consecutivePrimaryHealthyChecks = 0;

    // =============================================
    // CONSTRUCTOR
    // =============================================

    public HealthMonitoringService(
            DiscoveryClient discoveryClient,
            FailureClassifier failureClassifier,
            NRDADecisionEngine nrdaDecisionEngine,
            RecoveryExecutor recoveryExecutor,
            RecoveryVerifier recoveryVerifier,
            RecoveryEventLogger recoveryEventLogger,
            ResourceMetricsService resourceMetricsService,
            RecoveryHistoryService recoveryHistoryService,
            BaselineDecisionEngine baselineDecisionEngine,
            ExperimentResultService experimentResultService) {

        this.discoveryClient = discoveryClient;
        this.failureClassifier = failureClassifier;
        this.nrdaDecisionEngine = nrdaDecisionEngine;
        this.recoveryExecutor = recoveryExecutor;
        this.recoveryVerifier = recoveryVerifier;
        this.recoveryEventLogger = recoveryEventLogger;
        this.resourceMetricsService = resourceMetricsService;
        this.recoveryHistoryService = recoveryHistoryService;
        this.baselineDecisionEngine = baselineDecisionEngine;
        this.experimentResultService = experimentResultService;

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(1500);
        factory.setReadTimeout(5000);

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();

        SimpleClientHttpRequestFactory backupFactory =
                new SimpleClientHttpRequestFactory();

        backupFactory.setConnectTimeout(1500);
        backupFactory.setReadTimeout(1500);

        this.failbackClient = RestClient.builder()
                .requestFactory(backupFactory)
                .build();

        SimpleClientHttpRequestFactory gatewayFactory =
                new SimpleClientHttpRequestFactory();

        gatewayFactory.setConnectTimeout(1000);
        gatewayFactory.setReadTimeout(2500);

        this.gatewayProbeClient = RestClient.builder()
                .requestFactory(gatewayFactory)
                .build();

        healthRecords.put(
                PRODUCT_SERVICE,
                new ServiceHealthRecord(PRODUCT_SERVICE)
        );

        healthRecords.put(
                ORDER_SERVICE,
                new ServiceHealthRecord(ORDER_SERVICE)
        );
    }

    // =============================================
    // SCHEDULED MONITORING
    // =============================================

    @Scheduled(fixedRate = 5000)
    public void monitorServices() {
        checkService(PRODUCT_SERVICE);

        // Enable after Product Service experiments.
        // checkService(ORDER_SERVICE);
    }

    // =============================================
    // SERVICE HEALTH CHECK
    // =============================================

    private void checkService(String serviceName) {

        ServiceHealthRecord record =
                healthRecords.get(serviceName);

        if (record == null) {
            return;
        }

        double cpuUsage = 0.0;
        double memoryUtilization = 0.0;

        try {
            List<ServiceInstance> instances;

            try {
                instances = discoveryClient.getInstances(serviceName);

                if (instances == null) {
                    instances = Collections.emptyList();
                }

            } catch (Exception discoveryException) {

                System.out.println(
                        ">>> EUREKA DISCOVERY UNAVAILABLE"
                                + " | Service: " + serviceName
                                + " | Reason: "
                                + discoveryException.getMessage()
                );

                instances = Collections.emptyList();
            }

            if (instances.isEmpty()
                    && !PRODUCT_SERVICE.equalsIgnoreCase(serviceName)) {

                throw new IllegalStateException(
                        "No instances registered in Eureka for "
                                + serviceName
                );
            }

            // =====================================
            // SELECT MONITORING TARGET
            // =====================================

            ServiceInstance instance;

            if (PRODUCT_SERVICE.equalsIgnoreCase(serviceName)) {

                instance = instances.stream()
                        .filter(candidate -> candidate.getPort() == 8081)
                        .findFirst()
                        .orElse(null);

                if (instance == null) {

                    System.out.println(
                            ">>> PRIMARY EUREKA REGISTRATION MISSING"
                                    + " | Checking primary directly"
                    );

                    try {
                        restClient.get()
                                .uri(PRIMARY_URL + "/actuator/health")
                                .retrieve()
                                .toBodilessEntity();

                        restClient.get()
                                .uri(PRIMARY_URL + "/products")
                                .retrieve()
                                .toBodilessEntity();

                        instance = new DefaultServiceInstance(
                                "primary-direct",
                                serviceName,
                                "localhost",
                                8081,
                                false
                        );

                        System.out.println(
                                ">>> PRIMARY DIRECT HEALTH CHECK SUCCESS"
                        );

                    } catch (Exception directCheckException) {

                        throw new IllegalStateException(
                                "Primary missing from Eureka "
                                        + "and direct health check failed",
                                directCheckException
                        );
                    }
                }

            } else {
                instance = instances.get(0);
            }

            String baseUrl = instance.getUri().toString();
            String healthUrl = baseUrl + "/actuator/health";

            System.out.println(
                    ">>> MONITORING TARGET"
                            + " | Service: " + serviceName
                            + " | Instance: " + instance.getInstanceId()
                            + " | URL: " + baseUrl
            );

            // =====================================
            // RESOURCE METRICS
            // =====================================

            if (PRODUCT_SERVICE.equalsIgnoreCase(serviceName)) {

                cpuUsage =
                        resourceMetricsService.getCpuUsage(baseUrl);

                double memoryUsedMb =
                        resourceMetricsService.getMemoryUsedMb(baseUrl);

                memoryUtilization =
                        resourceMetricsService
                                .getMemoryUtilization(baseUrl);

                System.out.println(
                        ">>> RESOURCE METRICS"
                                + " | Service: " + serviceName
                                + " | CPU: "
                                + String.format("%.2f", cpuUsage * 100)
                                + "%"
                                + " | Memory Used: "
                                + String.format("%.2f", memoryUsedMb)
                                + " MB"
                                + " | Memory Utilization: "
                                + String.format(
                                "%.2f",
                                memoryUtilization * 100
                        )
                                + "%"
                );
            }

            // =====================================
            // ACTUATOR HEALTH CHECK
            // =====================================

            restClient.get()
                    .uri(healthUrl)
                    .retrieve()
                    .toBodilessEntity();

            // =====================================
            // RESPONSE TIME PROBE
            // =====================================

            String probeUrl =
                    PRODUCT_SERVICE.equalsIgnoreCase(serviceName)
                            ? baseUrl + "/products"
                            : healthUrl;

            long startTime = System.currentTimeMillis();

            restClient.get()
                    .uri(probeUrl)
                    .retrieve()
                    .toBodilessEntity();

            long responseTime =
                    System.currentTimeMillis() - startTime;

            String failureType =
                    failureClassifier.classify(
                            true,
                            responseTime
                    );

            // =====================================
            // SLOW-RESPONSE INCIDENT DETECTION
            // =====================================

            if ("SLOW_RESPONSE".equalsIgnoreCase(failureType)) {

                if (!slowResponseStartedAtMs.containsKey(serviceName)) {

                    slowResponseStartedAtMs.put(
                            serviceName,
                            System.currentTimeMillis()
                    );

                    slowResponseFailureCount.put(
                            serviceName,
                            0
                    );

                    System.out.println(
                            ">>> SLOW-RESPONSE INCIDENT STARTED"
                                    + " | Service: " + serviceName
                    );
                }

                slowResponseFailureCount.put(
                        serviceName,
                        slowResponseFailureCount.get(serviceName) + 1
                );
            }

            System.out.println(
                    ">>> RESPONSE TIME PROBE"
                            + " | Service: " + serviceName
                            + " | URL: " + probeUrl
                            + " | Response Time: "
                            + responseTime + " ms"
            );

            // =====================================
            // PRESERVE PREVIOUS FAILURE DETAILS
            // =====================================

            boolean recoveredFromFailure = record.wasDown();

            int finalFailureCount = record.getFailureCount();

            long finalFailureDetectionLatencyMs =
                    record.getFailureDetectionLatencyMs();

            String finalFailureType = record.getFailureType();

            // =====================================
            // UPDATE HEALTH RECORD
            // =====================================

            record.markUp(responseTime, failureType);

            // =====================================
            // RESET INCIDENT STATE ON HEALTH
            // =====================================

            if ("HEALTHY".equalsIgnoreCase(failureType)) {

                clearScaleOutIncidentState(serviceName);

                Long slowStartedAt =
                        slowResponseStartedAtMs.remove(serviceName);

                Integer slowChecks =
                        slowResponseFailureCount.remove(serviceName);

                if (slowStartedAt != null) {

                    long slowDurationMs =
                            System.currentTimeMillis() - slowStartedAt;

                    System.out.println(
                            ">>> SLOW-RESPONSE INCIDENT ENDED"
                                    + " | Service: " + serviceName
                                    + " | Duration: "
                                    + slowDurationMs + " ms"
                                    + " | Slow Checks: " + slowChecks
                                    + " | Recovery: NOT YET VERIFIED"
                    );
                }
            }

            if (recoveredFromFailure) {
                observeGatewayAvailability(serviceName);
            }

            // =====================================
            // AUTOMATIC FAILBACK
            // =====================================

            checkAutomaticFailback(serviceName, failureType);

            // =====================================
            // RECOVERY COMPLETED
            // =====================================

            if (recoveredFromFailure) {

                long finalDowntimeMs =
                        record.getLastDowntimeMs();

                Long finalPrimaryDowntimeMs =
                        finalDowntimeMs;

                Long finalGatewayRecoveryTimeMs =
                        gatewayRecoveryTimeMs.get(serviceName);

                System.out.println(
                        ">>> RECOVERY COMPLETED"
                                + " | Service: " + serviceName
                                + " | Final Downtime: "
                                + finalDowntimeMs + " ms"
                                + " | Failure Detection Latency: "
                                + finalFailureDetectionLatencyMs + " ms"
                );

                RecoveryEvent completedEvent =
                        new RecoveryEvent(
                                serviceName,
                                "RECOVERY_COMPLETED",
                                finalFailureCount,
                                "NO_ACTION",
                                "RECOVERY_COMPLETED",
                                "RECOVERY_SUCCESS",
                                0,
                                finalDowntimeMs,
                                finalFailureDetectionLatencyMs
                        );

                recoveryEventLogger.logEvent(completedEvent);

                // =================================
                // SAVE SERVICE-DOWN EXPERIMENT
                // =================================

                String successfulRecoveryAction =
                        lastRecoveryAction.get(serviceName);

                Long successfulRecoveryTimeMs =
                        lastRecoveryTime.get(serviceName);

                String successfulVerificationResult =
                        lastVerificationResult.get(serviceName);

                if (successfulRecoveryAction != null
                        && successfulRecoveryTimeMs != null
                        && successfulVerificationResult != null) {

                    experimentResultService.saveExperimentResult(
                            decisionMode,
                            serviceName,
                            finalFailureType,
                            successfulRecoveryAction,
                            successfulVerificationResult,
                            finalFailureCount,
                            finalFailureDetectionLatencyMs,
                            successfulRecoveryTimeMs,
                            finalDowntimeMs,
                            finalGatewayRecoveryTimeMs,
                            finalPrimaryDowntimeMs
                    );

                } else {

                    System.out.println(
                            ">>> EXPERIMENT RESULT NOT SAVED"
                                    + " | Service: " + serviceName
                                    + " | Reason: No verified recovery details"
                    );
                }

                // =================================
                // CLEAR COMPLETED INCIDENT
                // =================================

                lastRecoveryAction.remove(serviceName);
                lastRecoveryTime.remove(serviceName);
                lastVerificationResult.remove(serviceName);

                failureDetectedAtMs.remove(serviceName);
                gatewayRecoveryTimeMs.remove(serviceName);

                gatewayProbeAttemptedSinceDetection
                        .remove(serviceName);
            }

            printStatus(
                    record,
                    cpuUsage,
                    memoryUtilization
            );

        } catch (Exception e) {

            System.out.println(
                    ">>> MONITORING CHECK FAILED"
                            + " | Service: " + serviceName
                            + " | Reason: " + e.getMessage()
            );

            String failureType =
                    failureClassifier.classify(false, 0);

            boolean wasAlreadyDown = record.wasDown();

            record.markDown(failureType);

            if (!wasAlreadyDown) {
                startFailureIncident(serviceName);
            }

            checkAutomaticFailback(serviceName, failureType);

            calculateFailureDetectionLatency(
                    serviceName,
                    record
            );

            observeGatewayAvailability(serviceName);

            printStatus(
                    record,
                    cpuUsage,
                    memoryUtilization
            );
        }
    }

    // =============================================
    // SCALE-OUT INCIDENT STATE
    // =============================================

    private void clearScaleOutIncidentState(String serviceName) {

        boolean hadMaxLimit =
                Boolean.TRUE.equals(
                        scaleOutMaxReached.remove(serviceName)
                );

        boolean hadAttempt =
                lastScaleOutAttemptTime.remove(serviceName) != null;

        if (hadMaxLimit || hadAttempt) {

            System.out.println(
                    ">>> NRDA SCALE-OUT STATE RESET"
                            + " | Service: " + serviceName
                            + " | Reason: Service classified HEALTHY"
            );
        }
    }

    private boolean isScaleOutMaxReached(String serviceName) {

        return Boolean.TRUE.equals(
                scaleOutMaxReached.get(serviceName)
        );
    }

    private boolean isScaleOutCooldownActive(String serviceName) {

        Long lastAttempt =
                lastScaleOutAttemptTime.get(serviceName);

        if (lastAttempt == null) {
            return false;
        }

        long elapsed =
                System.currentTimeMillis() - lastAttempt;

        if (elapsed >= SCALE_OUT_COOLDOWN_MS) {
            return false;
        }

        System.out.println(
                ">>> NRDA SCALE-OUT COOLDOWN ACTIVE"
                        + " | Service: " + serviceName
                        + " | Elapsed: " + elapsed + " ms"
                        + " | Remaining: "
                        + (SCALE_OUT_COOLDOWN_MS - elapsed) + " ms"
        );

        return true;
    }

    // =============================================
    // GATEWAY AVAILABILITY MEASUREMENT
    // =============================================

    private void startFailureIncident(String serviceName) {

        failureDetectedAtMs.put(
                serviceName,
                System.currentTimeMillis()
        );

        gatewayRecoveryTimeMs.remove(serviceName);

        gatewayProbeAttemptedSinceDetection
                .remove(serviceName);

        System.out.println(
                ">>> FAILURE INCIDENT STARTED"
                        + " | Service: " + serviceName
                        + " | Gateway measurement: awaiting probe"
        );
    }

    private void observeGatewayAvailability(String serviceName) {

        if (!PRODUCT_SERVICE.equalsIgnoreCase(serviceName)) {
            return;
        }

        Long detectedAt =
                failureDetectedAtMs.get(serviceName);

        if (detectedAt == null
                || gatewayRecoveryTimeMs.containsKey(serviceName)) {
            return;
        }

        try {
            boolean available =
                    gatewayProbeClient.get()
                            .uri(GATEWAY_PRODUCT_PROBE_URL)
                            .retrieve()
                            .toBodilessEntity()
                            .getStatusCode()
                            .is2xxSuccessful();

            if (available) {

                long observedAt =
                        System.currentTimeMillis();

                long elapsedMs =
                        Math.max(0L, observedAt - detectedAt);

                boolean firstProbeAtDetection =
                        !gatewayProbeAttemptedSinceDetection
                                .containsKey(serviceName);

                long measuredMs =
                        firstProbeAtDetection ? 0L : elapsedMs;

                gatewayRecoveryTimeMs.put(
                        serviceName,
                        measuredMs
                );

                System.out.println(
                        ">>> GATEWAY AVAILABILITY CONFIRMED"
                                + " | Service: " + serviceName
                                + " | First observed 2xx: "
                                + measuredMs + " ms"
                                + (firstProbeAtDetection
                                ? " | Already available at detection"
                                : " | Recovered after initial failed probe")
                );
            }

        } catch (Exception e) {

            System.out.println(
                    ">>> GATEWAY PROBE NOT YET SUCCESSFUL"
                            + " | Service: " + serviceName
                            + " | Reason: " + e.getMessage()
            );

        } finally {

            gatewayProbeAttemptedSinceDetection.put(
                    serviceName,
                    true
            );
        }
    }

    // =============================================
    // FAILURE DETECTION LATENCY
    // =============================================

    private void calculateFailureDetectionLatency(
            String serviceName,
            ServiceHealthRecord record) {

        if (record.getFailureCount() != 1) {
            return;
        }

        long detectedAt = System.currentTimeMillis();
        long injectedAt = getInjectedAt(serviceName);

        if (injectedAt > 0) {

            long latency = detectedAt - injectedAt;

            record.setFailureDetectionLatencyMs(latency);

            System.out.println(
                    ">>> FAILURE DETECTION LATENCY"
                            + " | Service: " + serviceName
                            + " | Injected At: " + injectedAt
                            + " | Detected At: " + detectedAt
                            + " | Latency: " + latency + " ms"
            );

        } else {

            record.setFailureDetectionLatencyMs(-1);

            System.out.println(
                    ">>> FAILURE DETECTION LATENCY"
                            + " | Service: " + serviceName
                            + " | Latency: UNAVAILABLE"
            );
        }
    }

    // =============================================
    // NRDA DECISION AND RECOVERY EXECUTION
    // =============================================

    private void printStatus(
            ServiceHealthRecord record,
            double cpuUsage,
            double memoryUtilization) {

        String serviceName = record.getServiceName();

        int criticality =
                getServiceCriticality(serviceName);

        boolean dependencyHealthy =
                isDependencyHealthy(serviceName);

        double recoveryRisk =
                recoveryHistoryService.calculateRecoveryRisk(
                        serviceName,
                        "RESTART"
                );

        boolean healthyReplicaAvailable =
                isHealthyReplicaAvailable(serviceName);

        System.out.println(
                ">>> NRDA REPLICA AVAILABILITY"
                        + " | Service: " + serviceName
                        + " | Healthy Replica Available: "
                        + healthyReplicaAvailable
        );

        // =====================================
        // DECISION ENGINE
        // =====================================

        String recoveryAction;

        if ("BASELINE".equalsIgnoreCase(decisionMode)) {

            recoveryAction =
                    baselineDecisionEngine.decideRecoveryAction(
                            record.getFailureType(),
                            record.getFailureCount()
                    );

        } else {

            if (isVerifiedNrdaMitigationActive(serviceName, record)) {

                recoveryAction = "NO_ACTION";

                System.out.println(
                        ">>> NRDA INCIDENT ALREADY MITIGATED"
                                + " | Service: " + serviceName
                                + " | Verified Action: "
                                + lastRecoveryAction.get(serviceName)
                                + " | Gateway: HTTP 2xx"
                                + " | Monitoring primary for recovery"
                );

            } else {

                recoveryAction =
                        nrdaDecisionEngine.decideRecoveryAction(
                                record.getFailureType(),
                                record.getFailureCount(),
                                record.getResponseTime(),
                                criticality,
                                dependencyHealthy,
                                recoveryRisk,
                                cpuUsage,
                                memoryUtilization,
                                healthyReplicaAvailable
                        );
            }

            // =================================
            // SCALE-OUT DUPLICATE GUARD
            // =================================

            if ("SCALE_OUT".equals(recoveryAction)) {

                if (isScaleOutMaxReached(serviceName)) {

                    System.out.println(
                            ">>> NRDA SCALE-OUT SUPPRESSED"
                                    + " | Service: " + serviceName
                                    + " | Reason: Maximum replicas "
                                    + "already reached during this incident"
                    );

                    recoveryAction = "NO_ACTION";

                } else if (isScaleOutCooldownActive(serviceName)) {

                    System.out.println(
                            ">>> NRDA SCALE-OUT SUPPRESSED"
                                    + " | Service: " + serviceName
                                    + " | Reason: Scale-out cooldown"
                    );

                    recoveryAction = "NO_ACTION";
                }
            }
        }

        System.out.println(
                ">>> DECISION ENGINE"
                        + " | Mode: " + decisionMode
                        + " | Action: " + recoveryAction
        );

        // =====================================
        // RESTART COOLDOWN
        // =====================================

        boolean restartSuppressed = false;

        if ("RESTART".equals(recoveryAction)) {

            Long previousRestartTime =
                    lastRestartTime.get(serviceName);

            if (previousRestartTime != null) {

                long elapsed =
                        System.currentTimeMillis()
                                - previousRestartTime;

                if (elapsed < RESTART_COOLDOWN_MS) {

                    restartSuppressed = true;

                    System.out.println(
                            ">>> RESTART SUPPRESSED"
                                    + " | Service: " + serviceName
                                    + " | Reason: Cooldown active"
                                    + " | Elapsed: " + elapsed + " ms"
                    );
                }
            }
        }

        // =====================================
        // EXECUTE RECOVERY
        // =====================================

        long recoveryStartTime =
                System.currentTimeMillis();

        String recoveryResult;

        if (restartSuppressed) {

            recoveryResult = "RESTART_COOLDOWN_ACTIVE";

        } else if ("SWITCH_TO_BACKUP".equals(recoveryAction)
                && isBackupModeAlreadyActive()) {

            recoveryResult = "BACKUP_ALREADY_ACTIVE";

            System.out.println(
                    ">>> NRDA BACKUP ALREADY ACTIVE"
                            + " | Service: " + serviceName
            );

        } else {

            if ("SCALE_OUT".equals(recoveryAction)) {

                lastScaleOutAttemptTime.put(
                        serviceName,
                        System.currentTimeMillis()
                );
            }

            recoveryResult =
                    recoveryExecutor.execute(
                            serviceName,
                            recoveryAction
                    );

            if ("RESTART".equals(recoveryAction)
                    && "RESTART_EXECUTED".equals(recoveryResult)) {

                lastRestartTime.put(
                        serviceName,
                        System.currentTimeMillis()
                );
            }

            if ("SCALE_OUT".equals(recoveryAction)
                    && "SCALE_OUT_MAX_REPLICAS_REACHED"
                    .equals(recoveryResult)) {

                scaleOutMaxReached.put(
                        serviceName,
                        true
                );

                System.out.println(
                        ">>> NRDA SCALE-OUT LIMIT RECORDED"
                                + " | Service: " + serviceName
                                + " | Result: " + recoveryResult
                                + " | Further scale-out attempts "
                                + "suppressed until healthy"
                );
            }
        }

        // =====================================
        // VERIFY RECOVERY
        // =====================================

        String verificationResult = "NOT_REQUIRED";

        if (restartSuppressed) {

            verificationResult = "WAITING_FOR_RESTART";

        } else {

            switch (recoveryResult) {

                case "RESTART_EXECUTED":
                    verificationResult =
                            recoveryVerifier.verify(serviceName);
                    break;

                case "REROUTE_EXECUTED":
                    verificationResult =
                            recoveryVerifier.verifyReroute(serviceName);
                    break;

                case "BACKUP_SWITCH_EXECUTED":
                    verificationResult =
                            recoveryVerifier.verifyBackup(serviceName);
                    break;

                default:
                    // RETRY and SCALE_OUT are not automatically
                    // treated as verified permanent recoveries.
                    break;
            }
        }

        long recoveryTimeMs =
                System.currentTimeMillis() - recoveryStartTime;

        if (record.wasDown()) {
            observeGatewayAvailability(serviceName);
        }

        // =====================================
        // CALCULATE DOWNTIME
        // =====================================

        long downtimeMs;

        if ("UP".equals(record.getStatus())) {

            downtimeMs = record.getLastDowntimeMs();

        } else {

            downtimeMs = record.getCurrentDowntimeMs();
        }

        // =====================================
        // SAVE RECOVERY EVENT
        // =====================================

        boolean shouldLogEvent =
                !"NO_ACTION".equals(recoveryAction)
                        && !"BACKUP_ALREADY_ACTIVE"
                        .equals(recoveryResult);

        if (shouldLogEvent) {

            RecoveryEvent event =
                    new RecoveryEvent(
                            serviceName,
                            record.getFailureType(),
                            record.getFailureCount(),
                            recoveryAction,
                            recoveryResult,
                            verificationResult,
                            recoveryTimeMs,
                            downtimeMs,
                            record.getFailureDetectionLatencyMs()
                    );

            recoveryEventLogger.logEvent(event);

            // =====================================
            // SAVE SLOW-RESPONSE EXPERIMENT
            // =====================================

            if ("SLOW_RESPONSE".equalsIgnoreCase(
                    record.getFailureType())) {

                String slowVerificationResult = "NOT_VERIFIED";

                if ("RETRY_SUCCESS".equalsIgnoreCase(recoveryResult)) {

                    // The retry returned a normal response.
                    // This does not prove permanent fault recovery.
                    slowVerificationResult =
                            "RETRY_RESPONSE_NORMAL";

                } else if (
                        "RETRY_STILL_SLOW".equalsIgnoreCase(recoveryResult)
                                || "SCALE_OUT_MAX_REPLICAS_REACHED"
                                .equalsIgnoreCase(recoveryResult)) {

                    slowVerificationResult = "RECOVERY_FAILED";

                } else if (
                        "RECOVERY_SUCCESS".equalsIgnoreCase(
                                verificationResult)) {

                    slowVerificationResult = "RECOVERY_SUCCESS";
                }

                experimentResultService.saveExperimentResult(
                        decisionMode,
                        serviceName,
                        "SLOW_RESPONSE",
                        recoveryAction,
                        slowVerificationResult,
                        record.getFailureCount(),
                        record.getFailureDetectionLatencyMs(),
                        recoveryTimeMs,
                        0L,
                        null,
                        null
                );

                System.out.println(
                        ">>> SLOW-RESPONSE EXPERIMENT ATTEMPT SAVED"
                                + " | Service: " + serviceName
                                + " | Action: " + recoveryAction
                                + " | Outcome: " + recoveryResult
                                + " | Verification: "
                                + slowVerificationResult
                );
            }

            // =====================================
            // TRACK VERIFIED RECOVERY
            // =====================================

            if ("RECOVERY_SUCCESS".equals(verificationResult)) {

                lastRecoveryAction.put(
                        serviceName,
                        recoveryAction
                );

                lastRecoveryTime.put(
                        serviceName,
                        recoveryTimeMs
                );

                lastVerificationResult.put(
                        serviceName,
                        verificationResult
                );

                System.out.println(
                        ">>> NRDA RECOVERY VERIFIED AND RECORDED"
                                + " | Service: " + serviceName
                                + " | Action: " + recoveryAction
                                + " | Result: " + verificationResult
                );
            }
        }

        // =====================================
        // CONSOLE STATUS
        // =====================================

        System.out.println(
                LocalDateTime.now()
                        + " | " + serviceName
                        + " | " + record.getStatus()
                        + " | Response Time: "
                        + record.getResponseTime() + " ms"
                        + " | Failure Count: "
                        + record.getFailureCount()
                        + " | Classification: "
                        + record.getFailureType()
                        + " | Dependency Healthy: "
                        + dependencyHealthy
                        + " | Recovery Risk: "
                        + String.format("%.2f", recoveryRisk)
                        + " | CPU: "
                        + String.format("%.2f", cpuUsage * 100)
                        + "%"
                        + " | Memory Utilization: "
                        + String.format(
                        "%.2f",
                        memoryUtilization * 100
                )
                        + "%"
                        + " | Decision Mode: "
                        + decisionMode
                        + " | Recovery Action: "
                        + recoveryAction
                        + " | Recovery Result: "
                        + recoveryResult
                        + " | Verification: "
                        + verificationResult
                        + " | Recovery Time: "
                        + recoveryTimeMs + " ms"
                        + " | Downtime: "
                        + downtimeMs + " ms"
        );
    }

    // =============================================
    // PER-INCIDENT NRDA MITIGATION GUARD
    // =============================================

    private boolean isVerifiedNrdaMitigationActive(
            String serviceName,
            ServiceHealthRecord record) {

        if (!PRODUCT_SERVICE.equalsIgnoreCase(serviceName)
                || !record.wasDown()
                || !"RECOVERY_SUCCESS".equals(
                lastVerificationResult.get(serviceName))) {

            return false;
        }

        String verifiedAction =
                lastRecoveryAction.get(serviceName);

        if (!"REROUTE".equals(verifiedAction)
                && !"SWITCH_TO_BACKUP".equals(verifiedAction)) {

            return false;
        }

        try {
            return gatewayProbeClient.get()
                    .uri(GATEWAY_PRODUCT_PROBE_URL)
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()
                    .is2xxSuccessful();

        } catch (Exception e) {

            System.out.println(
                    ">>> NRDA MITIGATION RECHECK FAILED"
                            + " | Service: " + serviceName
                            + " | Previous Action: " + verifiedAction
                            + " | Reason: " + e.getMessage()
                            + " | Re-evaluating recovery"
            );

            return false;
        }
    }

    // =============================================
    // HEALTHY PRODUCT REPLICA DETECTION
    // =============================================

    private boolean isHealthyReplicaAvailable(String serviceName) {

        if (!PRODUCT_SERVICE.equalsIgnoreCase(serviceName)) {
            return false;
        }

        try {
            List<ServiceInstance> instances =
                    discoveryClient.getInstances(serviceName);

            if (instances == null) {
                return false;
            }

            for (ServiceInstance instance : instances) {

                int port = instance.getPort();

                if (port == 8081) {
                    continue;
                }

                String replicaUrl =
                        instance.getUri() + "/actuator/health";

                try {
                    restClient.get()
                            .uri(replicaUrl)
                            .retrieve()
                            .toBodilessEntity();

                    System.out.println(
                            ">>> NRDA HEALTHY REPLICA FOUND"
                                    + " | Instance: "
                                    + instance.getHost()
                                    + ":" + port
                    );

                    return true;

                } catch (Exception e) {

                    System.out.println(
                            ">>> NRDA REPLICA UNHEALTHY"
                                    + " | Instance: "
                                    + instance.getHost()
                                    + ":" + port
                    );
                }
            }

        } catch (Exception e) {

            System.out.println(
                    ">>> NRDA REPLICA CHECK FAILED"
                            + " | Reason: " + e.getMessage()
            );
        }

        return false;
    }

    // =============================================
    // CHECK EXISTING BACKUP MODE
    // =============================================

    private boolean isBackupModeAlreadyActive() {

        try {
            String status =
                    failbackClient.get()
                            .uri(GATEWAY_BACKUP_URL + "/status")
                            .retrieve()
                            .body(String.class);

            return "BACKUP_MODE_ACTIVE".equals(status);

        } catch (Exception e) {

            System.out.println(
                    ">>> NRDA BACKUP STATUS CHECK FAILED"
                            + " | Reason: " + e.getMessage()
            );

            return false;
        }
    }

    // =============================================
    // AUTOMATIC FAILBACK
    // =============================================

    private void checkAutomaticFailback(
            String serviceName,
            String failureType) {

        if (!PRODUCT_SERVICE.equalsIgnoreCase(serviceName)) {
            return;
        }

        if (!"NRDA".equalsIgnoreCase(decisionMode)) {

            consecutivePrimaryHealthyChecks = 0;
            return;
        }

        if (!"HEALTHY".equals(failureType)) {

            consecutivePrimaryHealthyChecks = 0;
            return;
        }

        consecutivePrimaryHealthyChecks++;

        if (consecutivePrimaryHealthyChecks
                < FAILBACK_HEALTHY_CHECKS) {

            System.out.println(
                    ">>> NRDA FAILBACK HEALTH CHECK"
                            + " | Primary: localhost:8081"
                            + " | Consecutive Healthy Checks: "
                            + consecutivePrimaryHealthyChecks
                            + "/" + FAILBACK_HEALTHY_CHECKS
            );

            return;
        }

        try {
            String status =
                    failbackClient.get()
                            .uri(GATEWAY_BACKUP_URL + "/status")
                            .retrieve()
                            .body(String.class);

            if ("BACKUP_MODE_INACTIVE".equals(status)) {

                consecutivePrimaryHealthyChecks = 0;
                return;
            }

            if (!"BACKUP_MODE_ACTIVE".equals(status)) {

                System.out.println(
                        ">>> NRDA FAILBACK WAITING"
                                + " | Unexpected Gateway Status: "
                                + status
                );

                return;
            }

            System.out.println(
                    ">>> NRDA AUTOMATIC FAILBACK STARTED"
                            + " | Primary: localhost:8081"
                            + " | Healthy Checks: "
                            + consecutivePrimaryHealthyChecks
            );

            String result =
                    failbackClient.post()
                            .uri(GATEWAY_BACKUP_URL + "/deactivate")
                            .retrieve()
                            .body(String.class);

            if (!"BACKUP_MODE_DEACTIVATED".equals(result)) {

                System.out.println(
                        ">>> NRDA AUTOMATIC FAILBACK FAILED"
                                + " | Response: " + result
                );

                return;
            }

            String finalStatus =
                    failbackClient.get()
                            .uri(GATEWAY_BACKUP_URL + "/status")
                            .retrieve()
                            .body(String.class);

            if ("BACKUP_MODE_INACTIVE".equals(finalStatus)) {

                System.out.println(
                        ">>> NRDA AUTOMATIC FAILBACK EXECUTED"
                                + " | Primary: localhost:8081"
                                + " | Gateway: NORMAL_MODE"
                );

                consecutivePrimaryHealthyChecks = 0;

            } else {

                System.out.println(
                        ">>> NRDA AUTOMATIC FAILBACK FAILED"
                                + " | Final Status: " + finalStatus
                );
            }

        } catch (Exception e) {

            System.out.println(
                    ">>> NRDA AUTOMATIC FAILBACK ERROR"
                            + " | Reason: " + e.getMessage()
            );
        }
    }

    // =============================================
    // SERVICE CRITICALITY
    // =============================================

    private int getServiceCriticality(String serviceName) {

        if (ORDER_SERVICE.equalsIgnoreCase(serviceName)) {
            return 9;
        }

        if (PRODUCT_SERVICE.equalsIgnoreCase(serviceName)) {
            return 7;
        }

        return 5;
    }

    // =============================================
    // DEPENDENCY HEALTH
    // =============================================

    private boolean isDependencyHealthy(String serviceName) {

        if (ORDER_SERVICE.equalsIgnoreCase(serviceName)) {

            ServiceHealthRecord productRecord =
                    healthRecords.get(PRODUCT_SERVICE);

            return productRecord != null
                    && "UP".equals(productRecord.getStatus());
        }

        return true;
    }

    // =============================================
    // CONTROLLED FAULT INJECTION TIMESTAMP
    // =============================================

    private long getInjectedAt(String serviceName) {

        if (!PRODUCT_SERVICE.equalsIgnoreCase(serviceName)) {
            return 0;
        }

        try {
            Long injectedAt =
                    restClient.get()
                            .uri(PRIMARY_URL + "/fault/injected-at")
                            .retrieve()
                            .body(Long.class);

            return injectedAt != null ? injectedAt : 0;

        } catch (Exception e) {

            System.out.println(
                    ">>> FAULT TIMESTAMP UNAVAILABLE"
                            + " | Service: " + serviceName
                            + " | Reason: " + e.getMessage()
            );

            return 0;
        }
    }

    // =============================================
    // HEALTH RECORDS
    // =============================================

    public Map<String, ServiceHealthRecord> getHealthRecords() {
        return healthRecords;
    }
}

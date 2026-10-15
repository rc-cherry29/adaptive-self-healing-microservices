
package com.selfhealing.monitoring_service.recovery;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class RecoveryVerifier {

    private final DiscoveryClient discoveryClient;
    private final RestClient restClient;

    private static final long RETRY_INTERVAL_MS = 1000;
    private static final long RECOVERY_TIMEOUT_MS = 60000;

    private static final String GATEWAY_URL =
            "http://localhost:8080";

    public RecoveryVerifier(DiscoveryClient discoveryClient) {

        this.discoveryClient = discoveryClient;

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(1500);
        factory.setReadTimeout(2000);

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    // =================================================
    // EXISTING RESTART VERIFICATION
    // =================================================

    public String verify(String serviceName) {

        long verificationStartTime =
                System.currentTimeMillis();

        int attempt = 0;

        while (System.currentTimeMillis()
                - verificationStartTime < RECOVERY_TIMEOUT_MS) {

            attempt++;

            try {

                if ("PRODUCT-SERVICE".equalsIgnoreCase(serviceName)) {

                    restClient.get()
                            .uri("http://localhost:8081/actuator/health")
                            .retrieve()
                            .toBodilessEntity();

                    logSuccess(
                            serviceName,
                            attempt,
                            verificationStartTime,
                            "DIRECT_HEALTH_CHECK"
                    );

                    return "RECOVERY_SUCCESS";
                }

                List<ServiceInstance> instances =
                        discoveryClient.getInstances(serviceName);

                if (!instances.isEmpty()) {

                    ServiceInstance instance =
                            instances.get(0);

                    restClient.get()
                            .uri(instance.getUri() + "/actuator/health")
                            .retrieve()
                            .toBodilessEntity();

                    logSuccess(
                            serviceName,
                            attempt,
                            verificationStartTime,
                            "EUREKA_HEALTH_CHECK"
                    );

                    return "RECOVERY_SUCCESS";
                }

            } catch (Exception e) {

                System.out.println(
                        ">>> RECOVERY VERIFICATION WAITING"
                                + " | Service: " + serviceName
                                + " | Attempt: " + attempt
                                + " | Error: " + e.getMessage()
                );
            }

            if (!waitBeforeRetry()) {
                return "RECOVERY_FAILED";
            }
        }

        System.out.println(
                ">>> RECOVERY VERIFICATION TIMEOUT"
                        + " | Service: " + serviceName
        );

        return "RECOVERY_FAILED";
    }

    // =================================================
    // REROUTE VERIFICATION
    // =================================================

    public String verifyReroute(String serviceName) {

        if (!"PRODUCT-SERVICE".equalsIgnoreCase(serviceName)) {
            return "RECOVERY_FAILED";
        }

        try {

            restClient.get()
                    .uri(GATEWAY_URL + "/products")
                    .retrieve()
                    .toBodilessEntity();

            System.out.println(
                    ">>> NRDA REROUTE VERIFICATION SUCCESS"
                            + " | Service: " + serviceName
                            + " | Gateway: HTTP 2xx"
            );

            return "RECOVERY_SUCCESS";

        } catch (Exception e) {

            System.out.println(
                    ">>> NRDA REROUTE VERIFICATION FAILED"
                            + " | Reason: " + e.getMessage()
            );

            return "RECOVERY_FAILED";
        }
    }

    // =================================================
    // SWITCH TO BACKUP VERIFICATION
    // =================================================

    public String verifyBackup(String serviceName) {

        if (!"PRODUCT-SERVICE".equalsIgnoreCase(serviceName)) {
            return "RECOVERY_FAILED";
        }

        try {

            String status = restClient.get()
                    .uri(GATEWAY_URL + "/internal/backup/status")
                    .retrieve()
                    .body(String.class);

            if (!"BACKUP_MODE_ACTIVE".equals(status)) {

                System.out.println(
                        ">>> NRDA BACKUP VERIFICATION FAILED"
                                + " | Status: " + status
                );

                return "RECOVERY_FAILED";
            }

            restClient.get()
                    .uri(GATEWAY_URL + "/products")
                    .retrieve()
                    .toBodilessEntity();

            System.out.println(
                    ">>> NRDA BACKUP VERIFICATION SUCCESS"
                            + " | Service: " + serviceName
                            + " | Backup Mode: ACTIVE"
                            + " | Gateway: HTTP 2xx"
            );

            return "RECOVERY_SUCCESS";

        } catch (Exception e) {

            System.out.println(
                    ">>> NRDA BACKUP VERIFICATION FAILED"
                            + " | Reason: " + e.getMessage()
            );

            return "RECOVERY_FAILED";
        }
    }

    // =================================================
    // COMMON HELPERS
    // =================================================

    private void logSuccess(
            String serviceName,
            int attempt,
            long verificationStartTime,
            String method) {

        long verificationTime =
                System.currentTimeMillis()
                        - verificationStartTime;

        System.out.println(
                ">>> RECOVERY VERIFIED"
                        + " | Service: " + serviceName
                        + " | Attempt: " + attempt
                        + " | Verification Time: "
                        + verificationTime + " ms"
                        + " | Method: " + method
        );
    }

    private boolean waitBeforeRetry() {

        try {

            Thread.sleep(RETRY_INTERVAL_MS);
            return true;

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();
            return false;
        }
    }
}

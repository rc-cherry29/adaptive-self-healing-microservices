
package com.selfhealing.monitoring_service.recovery;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class RecoveryExecutor {

    private final DiscoveryClient discoveryClient;
    private final RestClient restClient;

    private static final long SLOW_RESPONSE_THRESHOLD_MS = 2000;

    // Original Product Service used in controlled experiments.
    private static final String PRODUCT_SERVICE_URL =
            "http://localhost:8081";

    private static final String PRODUCT_CONTAINER =
            "product-service-container";

    // Dynamic scaling configuration.
    private static final int MAX_SCALE_REPLICAS = 3;

    private static final int FIRST_SCALE_PORT = 8084;

    private static final String SCALE_IMAGE =
            "product-service:updated";

    private static final int REPLICA_STARTUP_TIMEOUT_SECONDS = 45;

    public RecoveryExecutor(DiscoveryClient discoveryClient) {

        this.discoveryClient = discoveryClient;

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(2000);
        requestFactory.setReadTimeout(5000);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    // ---------------------------------------------------------
    // RECOVERY ACTION DISPATCHER
    // ---------------------------------------------------------

    public String execute(String serviceName, String recoveryAction) {

        switch (recoveryAction) {

            case "NO_ACTION":
                return "NO_RECOVERY_REQUIRED";

            case "RETRY":
                return retryService(serviceName);

            case "RESTART":
                return restartService(serviceName);

            case "SCALE_OUT":
                return scaleOutService(serviceName);

            case "REROUTE":
                return rerouteService(serviceName);

            case "SWITCH_TO_BACKUP":
                return switchToBackup(serviceName);

            default:
                return "UNKNOWN_ACTION";
        }
    }

    // ---------------------------------------------------------
    // RETRY RECOVERY
    // ---------------------------------------------------------

    private String retryService(String serviceName) {

        // Probe the original Product Service, not a healthy replica.
        if ("PRODUCT-SERVICE".equalsIgnoreCase(serviceName)) {

            return probeService(
                    serviceName,
                    PRODUCT_SERVICE_URL + "/products"
            );
        }

        List<ServiceInstance> instances =
                discoveryClient.getInstances(serviceName);

        if (instances.isEmpty()) {

            System.out.println(
                    ">>> RETRY FAILED | No Eureka instances for "
                            + serviceName
            );

            return "RETRY_FAILED";
        }

        for (ServiceInstance instance : instances) {

            String probeUrl =
                    instance.getUri().toString()
                            + "/actuator/health";

            String result = probeService(
                    serviceName,
                    probeUrl
            );

            if ("RETRY_SUCCESS".equals(result)) {
                return result;
            }
        }

        return "RETRY_FAILED";
    }

    private String rerouteService(String serviceName) {

        if (!"PRODUCT-SERVICE".equalsIgnoreCase(serviceName)) {
            return "REROUTE_NOT_CONFIGURED";
        }

        try {
            String result = restClient.post()
                    .uri("http://localhost:8080/internal/reroute/exclude"
                            + "?host=localhost&port=8081")
                    .retrieve()
                    .body(String.class);

            if ("REROUTE_EXCLUSION_REGISTERED".equals(result)) {

                System.out.println(
                        ">>> NRDA REROUTE EXECUTED"
                                + " | Excluded: localhost:8081"
                );

                return "REROUTE_EXECUTED";
            }

            return "REROUTE_FAILED";

        } catch (Exception e) {

            System.out.println(
                    ">>> NRDA REROUTE FAILED | " + e.getMessage()
            );

            return "REROUTE_FAILED";
        }
    }

    private String switchToBackup(String serviceName) {

        if (!"PRODUCT-SERVICE".equalsIgnoreCase(serviceName)) {
            return "BACKUP_NOT_CONFIGURED";
        }

        try {
            String result = restClient.post()
                    .uri("http://localhost:8080/internal/backup/activate")
                    .retrieve()
                    .body(String.class);

            if ("BACKUP_MODE_ACTIVATED".equals(result)) {

                System.out.println(
                        ">>> NRDA SWITCH_TO_BACKUP EXECUTED"
                                + " | Backup Instance: localhost:8086"
                );

                return "BACKUP_SWITCH_EXECUTED";
            }

            return "BACKUP_SWITCH_FAILED";

        } catch (Exception e) {

            System.out.println(
                    ">>> NRDA SWITCH_TO_BACKUP FAILED | "
                            + e.getMessage()
            );

            return "BACKUP_SWITCH_FAILED";
        }
    }

    private String probeService(
            String serviceName,
            String probeUrl
    ) {

        try {

            long startTime = System.currentTimeMillis();

            restClient.get()
                    .uri(probeUrl)
                    .retrieve()
                    .toBodilessEntity();

            long responseTime =
                    System.currentTimeMillis() - startTime;

            System.out.println(
                    ">>> RETRY VERIFICATION"
                            + " | Service: " + serviceName
                            + " | URL: " + probeUrl
                            + " | Response Time: "
                            + responseTime + " ms"
            );

            if (responseTime > SLOW_RESPONSE_THRESHOLD_MS) {

                System.out.println(
                        ">>> RETRY RESULT"
                                + " | Service: " + serviceName
                                + " | Status: STILL SLOW"
                );

                return "RETRY_STILL_SLOW";
            }

            System.out.println(
                    ">>> RETRY RESULT"
                            + " | Service: " + serviceName
                            + " | Status: RESPONSE TIME NORMAL"
            );

            return "RETRY_SUCCESS";

        } catch (Exception e) {

            System.out.println(
                    ">>> RETRY FAILED"
                            + " | Service: " + serviceName
                            + " | URL: " + probeUrl
                            + " | Reason: " + e.getMessage()
            );

            return "RETRY_FAILED";
        }
    }

    // ---------------------------------------------------------
    // RESTART RECOVERY
    // ---------------------------------------------------------

    private String restartService(String serviceName) {

        if (!"PRODUCT-SERVICE".equalsIgnoreCase(serviceName)) {
            return "RESTART_NOT_CONFIGURED";
        }

        try {

            ProcessBuilder processBuilder =
                    new ProcessBuilder(
                            "docker",
                            "restart",
                            PRODUCT_CONTAINER
                    );

            processBuilder.redirectErrorStream(true);

            processBuilder.redirectOutput(
                    ProcessBuilder.Redirect.INHERIT
            );

            Process process = processBuilder.start();

            boolean finished =
                    process.waitFor(30, TimeUnit.SECONDS);

            if (!finished) {

                process.destroyForcibly();

                return "RESTART_TIMEOUT";
            }

            if (process.exitValue() == 0) {

                System.out.println(
                        ">>> RESTART EXECUTED"
                                + " | Container: "
                                + PRODUCT_CONTAINER
                );

                return "RESTART_EXECUTED";
            }

            return "RESTART_FAILED";

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            return "RESTART_FAILED";

        } catch (Exception e) {

            System.out.println(
                    ">>> RESTART ERROR | " + e.getMessage()
            );

            return "RESTART_FAILED";
        }
    }

    // ---------------------------------------------------------
    // DYNAMIC SCALE-OUT RECOVERY
    // ---------------------------------------------------------

    private String scaleOutService(String serviceName) {

        if (!"PRODUCT-SERVICE".equalsIgnoreCase(serviceName)) {
            return "SCALE_OUT_NOT_CONFIGURED";
        }

        try {

            if (!isDockerAvailable()) {

                System.out.println(
                        ">>> SCALE OUT FAILED | Docker unavailable"
                );

                return "SCALE_OUT_FAILED";
            }

            // Check replica slots from 1 to MAX_SCALE_REPLICAS.
            for (int replicaNumber = 1;
                 replicaNumber <= MAX_SCALE_REPLICAS;
                 replicaNumber++) {

                String containerName =
                        getScaleContainerName(replicaNumber);

                int hostPort =
                        getScalePort(replicaNumber);

                // Existing containers are never overwritten.
                if (scaleContainerExists(containerName)) {

                    if (!isScaleContainerRunning(containerName)) {

                        System.out.println(
                                ">>> SCALE OUT | Existing replica stopped"
                                        + " | Container: " + containerName
                        );

                        return "SCALE_OUT_REPLICA_STOPPED";
                    }

                    if (!isScaleReplicaHealthy(hostPort)) {

                        System.out.println(
                                ">>> SCALE OUT | Existing replica unhealthy"
                                        + " | Container: " + containerName
                                        + " | Port: " + hostPort
                        );

                        return "SCALE_OUT_REPLICA_UNHEALTHY";
                    }

                    System.out.println(
                            ">>> SCALE OUT | Existing healthy replica"
                                    + " | Container: " + containerName
                                    + " | Port: " + hostPort
                    );

                    // This replica is healthy.
                    // Check whether another replica can be created.
                    continue;
                }

                // Found an available replica slot.
                // Create exactly one new replica per SCALE_OUT call.
                return createScaleReplica(
                        serviceName,
                        containerName,
                        hostPort
                );
            }

            // All configured replica slots are occupied and healthy.
            System.out.println(
                    ">>> SCALE OUT | Maximum replicas reached"
                            + " | Maximum: " + MAX_SCALE_REPLICAS
            );

            return "SCALE_OUT_MAX_REPLICAS_REACHED";

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            return "SCALE_OUT_FAILED";

        } catch (Exception e) {

            System.out.println(
                    ">>> SCALE OUT ERROR"
                            + " | Reason: " + e.getMessage()
            );

            return "SCALE_OUT_FAILED";
        }
    }

    // ---------------------------------------------------------
    // REPLICA CONFIGURATION HELPERS
    // ---------------------------------------------------------

    private String getScaleContainerName(int replicaNumber) {

        return "product-service-scale-" + replicaNumber;
    }

    private int getScalePort(int replicaNumber) {

        return FIRST_SCALE_PORT + replicaNumber - 1;
    }

    // ---------------------------------------------------------
    // DOCKER AVAILABILITY
    // ---------------------------------------------------------

    private boolean isDockerAvailable()
            throws Exception {

        ProcessBuilder builder = new ProcessBuilder(
                "docker",
                "info",
                "--format",
                "{{.ServerVersion}}"
        );

        builder.redirectOutput(
                ProcessBuilder.Redirect.DISCARD
        );

        builder.redirectError(
                ProcessBuilder.Redirect.DISCARD
        );

        Process process = builder.start();

        boolean finished =
                process.waitFor(10, TimeUnit.SECONDS);

        if (!finished) {

            process.destroyForcibly();

            return false;
        }

        return process.exitValue() == 0;
    }

    // ---------------------------------------------------------
    // CHECK WHETHER A REPLICA EXISTS
    // ---------------------------------------------------------

    private boolean scaleContainerExists(String containerName)
            throws Exception {

        ProcessBuilder builder = new ProcessBuilder(
                "docker",
                "container",
                "inspect",
                containerName
        );

        builder.redirectOutput(
                ProcessBuilder.Redirect.DISCARD
        );

        builder.redirectError(
                ProcessBuilder.Redirect.DISCARD
        );

        Process process = builder.start();

        boolean finished =
                process.waitFor(10, TimeUnit.SECONDS);

        if (!finished) {

            process.destroyForcibly();

            throw new IllegalStateException(
                    "Docker inspect timed out: " + containerName
            );
        }

        return process.exitValue() == 0;
    }

    // ---------------------------------------------------------
    // CHECK WHETHER A REPLICA IS RUNNING
    // ---------------------------------------------------------

    private boolean isScaleContainerRunning(String containerName)
            throws Exception {

        ProcessBuilder builder = new ProcessBuilder(
                "docker",
                "inspect",
                "--format",
                "{{.State.Running}}",
                containerName
        );

        builder.redirectErrorStream(true);

        Process process = builder.start();

        boolean finished =
                process.waitFor(10, TimeUnit.SECONDS);

        if (!finished) {

            process.destroyForcibly();

            throw new IllegalStateException(
                    "Docker status check timed out: " + containerName
            );
        }

        String output = new String(
                process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
        ).trim();

        if (process.exitValue() != 0) {

            throw new IllegalStateException(
                    "Docker status check failed: " + output
            );
        }

        return "true".equalsIgnoreCase(output);
    }

    // ---------------------------------------------------------
    // CHECK REPLICA HEALTH
    // ---------------------------------------------------------

    private boolean isScaleReplicaHealthy(int hostPort) {

        HttpURLConnection connection = null;

        try {

            String healthUrl =
                    "http://localhost:"
                            + hostPort
                            + "/actuator/health";

            connection = (HttpURLConnection)
                    URI.create(healthUrl)
                            .toURL()
                            .openConnection();

            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestMethod("GET");

            if (connection.getResponseCode() != 200) {
                return false;
            }

            String response;

            try (InputStream input =
                         connection.getInputStream()) {

                response = new String(
                        input.readAllBytes(),
                        StandardCharsets.UTF_8
                );
            }

            return response
                    .replaceAll("\\s+", "")
                    .contains("\"status\":\"UP\"");

        } catch (Exception e) {

            return false;

        } finally {

            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // ---------------------------------------------------------
    // CREATE A NEW DOCKER REPLICA
    // ---------------------------------------------------------

    private String createScaleReplica(
            String serviceName,
            String containerName,
            int hostPort
    ) throws Exception {

        String portMapping = hostPort + ":8081";

        String baseUrl =
                "http://localhost:" + hostPort;

        ProcessBuilder builder = new ProcessBuilder(
                "docker",
                "run",
                "-d",
                "--pull=never",

                "--name",
                containerName,

                "-p",
                portMapping,

                "--add-host=host.docker.internal:host-gateway",

                "-e",
                "EUREKA_CLIENT_SERVICEURL_DEFAULTZONE="
                        + "http://host.docker.internal:8761/eureka/",

                "-e",
                "EUREKA_INSTANCE_HOSTNAME=localhost",

                "-e",
                "EUREKA_INSTANCE_PREFER_IP_ADDRESS=false",

                "-e",
                "EUREKA_INSTANCE_INSTANCE_ID="
                        + containerName,

                "-e",
                "EUREKA_INSTANCE_NON_SECURE_PORT="
                        + hostPort,

                "-e",
                "EUREKA_INSTANCE_HEALTH_CHECK_URL="
                        + baseUrl + "/actuator/health",

                "-e",
                "EUREKA_INSTANCE_STATUS_PAGE_URL="
                        + baseUrl + "/actuator/info",

                "-e",
                "EUREKA_INSTANCE_HOME_PAGE_URL="
                        + baseUrl + "/",

                SCALE_IMAGE
        );

        builder.redirectErrorStream(true);

        builder.redirectOutput(
                ProcessBuilder.Redirect.INHERIT
        );

        System.out.println(
                ">>> SCALE OUT STARTED"
                        + " | Service: " + serviceName
                        + " | Container: " + containerName
                        + " | Host Port: " + hostPort
                        + " | Image: " + SCALE_IMAGE
        );

        Process process = builder.start();

        boolean finished =
                process.waitFor(30, TimeUnit.SECONDS);

        if (!finished) {

            process.destroyForcibly();

            System.out.println(
                    ">>> SCALE OUT TIMEOUT"
                            + " | Container: " + containerName
            );

            return "SCALE_OUT_TIMEOUT";
        }

        if (process.exitValue() != 0) {

            System.out.println(
                    ">>> SCALE OUT FAILED"
                            + " | Container: " + containerName
                            + " | Docker exit code: "
                            + process.exitValue()
            );

            return "SCALE_OUT_FAILED";
        }

        System.out.println(
                ">>> SCALE OUT | Container created"
                        + " | Container: " + containerName
                        + " | Waiting for health check"
        );

        // Docker run returning success does not mean
        // Spring Boot has finished starting.
        boolean healthy = waitForScaleReplicaHealth(hostPort);

        if (!healthy) {

            System.out.println(
                    ">>> SCALE OUT | New replica not healthy"
                            + " | Container: " + containerName
                            + " | Port: " + hostPort
            );

            return "SCALE_OUT_REPLICA_UNHEALTHY";
        }

        System.out.println(
                ">>> SCALE OUT EXECUTED"
                        + " | Container: " + containerName
                        + " | Host Port: " + hostPort
                        + " | Health: UP"
        );

        return "SCALE_OUT_EXECUTED";
    }

    // ---------------------------------------------------------
    // WAIT FOR NEW REPLICA TO BECOME HEALTHY
    // ---------------------------------------------------------

    private boolean waitForScaleReplicaHealth(int hostPort)
            throws InterruptedException {

        long deadline =
                System.currentTimeMillis()
                        + REPLICA_STARTUP_TIMEOUT_SECONDS * 1000L;

        while (System.currentTimeMillis() < deadline) {

            if (isScaleReplicaHealthy(hostPort)) {
                return true;
            }

            Thread.sleep(1500);
        }

        return false;
    }
}

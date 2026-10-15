package com.selfhealing.monitoring_service.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only dashboard API plus explicitly gated LOCAL demonstration fault controls.
 * Add spring-boot-starter-jdbc if your Monitoring Service does not already have it.
 * Uses the existing datasource and experiment_results table.
 */
@RestController
@RequestMapping("/api/dashboard")
public class NRDADashboardController {
    private final JdbcTemplate jdbc;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(700))
            .build();

    @Value("${recovery.decision-mode:NRDA}")
    private String decisionMode;

    @Value("${dashboard.fault-controls-enabled:false}")
    private boolean faultControlsEnabled;

    public NRDADashboardController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        String sql = """
            SELECT COUNT(*) AS total_experiments,
                   COALESCE(SUM(CASE WHEN decision_mode = 'NRDA' THEN 1 ELSE 0 END), 0) AS nrda_experiments,
                   COALESCE(SUM(CASE WHEN verification_result IN
                       ('RECOVERY_SUCCESS', 'RETRY_RESPONSE_NORMAL')
                       THEN 1 ELSE 0 END), 0) AS verified_successes,
                   COALESCE(SUM(CASE WHEN verification_result = 'RECOVERY_FAILED'
                       THEN 1 ELSE 0 END), 0) AS failed_attempts
            FROM experiment_results
            """;
        return jdbc.queryForObject(sql, (rs, rowNum) -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("decisionMode", decisionMode);
            result.put("totalExperiments", rs.getLong("total_experiments"));
            result.put("nrdaExperiments", rs.getLong("nrda_experiments"));
            result.put("verifiedSuccesses", rs.getLong("verified_successes"));
            result.put("failedAttempts", rs.getLong("failed_attempts"));
            return result;
        });
    }

    @GetMapping("/experiments")
    public List<Map<String, Object>> experiments(@RequestParam(defaultValue = "100") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        String sql = """
            SELECT id, decision_mode, service_name, failure_type,
                   recovery_action, verification_result,
                   recovery_time_ms, downtime_ms,
                   gateway_recovery_time_ms, primary_downtime_ms
            FROM experiment_results
            ORDER BY id DESC
            LIMIT ?
            """;
        return jdbc.query(sql, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getLong("id"));
            row.put("decisionMode", rs.getString("decision_mode"));
            row.put("serviceName", rs.getString("service_name"));
            row.put("failureType", rs.getString("failure_type"));
            row.put("recoveryAction", rs.getString("recovery_action"));
            row.put("verificationResult", rs.getString("verification_result"));
            row.put("recoveryTimeMs", nullableNumber(rs.getObject("recovery_time_ms")));
            row.put("downtimeMs", nullableNumber(rs.getObject("downtime_ms")));
            row.put("gatewayRecoveryTimeMs", nullableNumber(rs.getObject("gateway_recovery_time_ms")));
            row.put("primaryDowntimeMs", nullableNumber(rs.getObject("primary_downtime_ms")));
            return row;
        }, safeLimit);
    }

    @GetMapping("/analytics")
    public List<Map<String, Object>> analytics() {
        String sql = """
            SELECT decision_mode, failure_type, recovery_action,
                   COUNT(*) AS experiments,
                   AVG(recovery_time_ms) AS avg_recovery_ms,
                   AVG(downtime_ms) AS avg_downtime_ms,
                   COUNT(gateway_recovery_time_ms) AS gateway_measured,
                   AVG(gateway_recovery_time_ms) AS avg_gateway_ms
            FROM experiment_results
            GROUP BY decision_mode, failure_type, recovery_action
            ORDER BY failure_type, decision_mode, recovery_action
            """;
        return jdbc.query(sql, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("decisionMode", rs.getString("decision_mode"));
            row.put("failureType", rs.getString("failure_type"));
            row.put("recoveryAction", rs.getString("recovery_action"));
            row.put("experiments", rs.getLong("experiments"));
            row.put("avgRecoveryMs", nullableNumber(rs.getObject("avg_recovery_ms")));
            row.put("avgDowntimeMs", nullableNumber(rs.getObject("avg_downtime_ms")));
            row.put("gatewayMeasured", rs.getLong("gateway_measured"));
            row.put("avgGatewayMs", nullableNumber(rs.getObject("avg_gateway_ms")));
            return row;
        });
    }

    @GetMapping("/services")
    public List<Map<String, Object>> services() {
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(probe("Product Service", "http://localhost:8081"));
        result.add(probe("Order Service", "http://localhost:8082"));
        result.add(probe("Eureka Registry", "http://localhost:8761"));
        result.add(probe("API Gateway", "http://localhost:8080"));
        return result;
    }

    private Map<String, Object> probe(String name, String baseUrl) {
        long started = System.nanoTime();
        String status = "DOWN";
        try {
            HttpResponse<String> health = get(baseUrl + "/actuator/health");
            if (health.statusCode() == 404) {
                // Actuator is optional. A response to / proves HTTP reachability, not health.
                get(baseUrl + "/");
                status = "REACHABLE";
            } else if (health.body().matches("(?s).*\\\"status\\\"\\s*:\\s*\\\"UP\\\".*")) {
                status = "UP";
            } else if (health.body().matches("(?s).*\\\"status\\\"\\s*:\\s*\\\"DOWN\\\".*")) {
                status = "DOWN";
            } else {
                status = "REACHABLE";
            }
        } catch (HttpTimeoutException ex) {
            status = "TIMEOUT";
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            status = "TIMEOUT";
        } catch (IOException ex) {
            status = "DOWN";
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("url", baseUrl);
        row.put("status", status);
        row.put("probeMs", (System.nanoTime() - started) / 1_000_000);
        return row;
    }

    private HttpResponse<String> get(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(1400))
                .GET().build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    @PostMapping("/faults/slow/{state}")
    public Map<String, String> slowFault(@PathVariable String state, HttpServletRequest request) {
        if (!faultControlsEnabled) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Fault controls disabled. Set dashboard.fault-controls-enabled=true for local demos.");
        }
        String remote = request.getRemoteAddr();
        if (!("127.0.0.1".equals(remote) || "::1".equals(remote)
                || "0:0:0:0:0:0:0:1".equals(remote))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Fault controls are restricted to requests from localhost.");
        }
        if (!("enable".equals(state) || "disable".equals(state))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "State must be enable or disable");
        }
        String url = "http://localhost:8081/fault/slow/" + state;
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(4))
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        try {
            HttpResponse<String> response = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Product Service returned HTTP " + response.statusCode());
            }
            return Map.of("message", "Product Service slow-response fault "
                    + ("enable".equals(state) ? "enabled." : "disabled."));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Fault request interrupted", ex);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Cannot reach Product Service on localhost:8081", ex);
        }
    }

    private static Number nullableNumber(Object value) {
        return value == null ? null : (Number) value;
    }
}

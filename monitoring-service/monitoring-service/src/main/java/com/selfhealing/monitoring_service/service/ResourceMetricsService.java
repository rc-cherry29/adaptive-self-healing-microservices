package com.selfhealing.monitoring_service.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class ResourceMetricsService {

    private final RestClient restClient;

    public ResourceMetricsService() {
        this.restClient = RestClient.create();
    }

    public double getCpuUsage(String baseUrl) {

        try {

            Map response =
                    restClient.get()
                            .uri(baseUrl
                                    + "/actuator/metrics/process.cpu.usage")
                            .retrieve()
                            .body(Map.class);

            List<Map<String, Object>> measurements =
                    (List<Map<String, Object>>)
                            response.get("measurements");

            if (measurements == null
                    || measurements.isEmpty()) {
                return 0.0;
            }

            Number value =
                    (Number) measurements
                            .get(0)
                            .get("value");

            return value.doubleValue();

        } catch (Exception e) {

            System.out.println(
                    ">>> CPU METRIC ERROR | "
                            + e.getMessage()
            );

            return 0.0;
        }
    }


    public double getMemoryUsedMb(String baseUrl) {

        try {

            Map response =
                    restClient.get()
                            .uri(baseUrl
                                    + "/actuator/metrics/jvm.memory.used")
                            .retrieve()
                            .body(Map.class);

            List<Map<String, Object>> measurements =
                    (List<Map<String, Object>>)
                            response.get("measurements");

            if (measurements == null
                    || measurements.isEmpty()) {
                return 0.0;
            }

            Number value =
                    (Number) measurements
                            .get(0)
                            .get("value");

            double bytes =
                    value.doubleValue();

            return bytes
                    / (1024.0 * 1024.0);

        } catch (Exception e) {

            System.out.println(
                    ">>> MEMORY METRIC ERROR | "
                            + e.getMessage()
            );

            return 0.0;
        }
    }

    public double getMemoryUtilization(String baseUrl) {

        try {

            Map usedResponse =
                    restClient.get()
                            .uri(baseUrl
                                    + "/actuator/metrics/jvm.memory.used?tag=area:heap")
                            .retrieve()
                            .body(Map.class);

            Map maxResponse =
                    restClient.get()
                            .uri(baseUrl
                                    + "/actuator/metrics/jvm.memory.max?tag=area:heap")
                            .retrieve()
                            .body(Map.class);

            List<Map<String, Object>> usedMeasurements =
                    (List<Map<String, Object>>)
                            usedResponse.get("measurements");

            List<Map<String, Object>> maxMeasurements =
                    (List<Map<String, Object>>)
                            maxResponse.get("measurements");

            if (usedMeasurements == null
                    || usedMeasurements.isEmpty()
                    || maxMeasurements == null
                    || maxMeasurements.isEmpty()) {

                return 0.0;
            }

            Number usedValue =
                    (Number) usedMeasurements
                            .get(0)
                            .get("value");

            Number maxValue =
                    (Number) maxMeasurements
                            .get(0)
                            .get("value");

            double usedBytes =
                    usedValue.doubleValue();

            double maxBytes =
                    maxValue.doubleValue();

            if (maxBytes <= 0) {
                return 0.0;
            }

            return usedBytes / maxBytes;

        } catch (Exception e) {

            System.out.println(
                    ">>> MEMORY UTILIZATION ERROR | "
                            + e.getMessage()
            );

            return 0.0;
        }
    }
}
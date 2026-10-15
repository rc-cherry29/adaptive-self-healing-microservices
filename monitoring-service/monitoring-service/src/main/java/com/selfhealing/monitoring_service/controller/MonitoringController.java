package com.selfhealing.monitoring_service.controller;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/monitor")
public class MonitoringController {

    private final DiscoveryClient discoveryClient;
    private final RestClient restClient;

    public MonitoringController(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
        this.restClient = RestClient.create();
    }

    @GetMapping("/health")
    public Map<String, String> checkServices() {

        Map<String, String> status = new LinkedHashMap<>();

        status.put(
                "PRODUCT-SERVICE",
                checkService("PRODUCT-SERVICE")
        );

        status.put(
                "ORDER-SERVICE",
                checkService("ORDER-SERVICE")
        );

        return status;
    }

    private String checkService(String serviceName) {

        try {

            List<ServiceInstance> instances =
                    discoveryClient.getInstances(serviceName);

            if (instances.isEmpty()) {
                return "DOWN";
            }

            ServiceInstance instance = instances.get(0);

            String healthUrl =
                    instance.getUri() + "/actuator/health";

            restClient
                    .get()
                    .uri(healthUrl)
                    .retrieve()
                    .toBodilessEntity();

            return "UP";

        } catch (Exception e) {

            return "DOWN";
        }
    }
}
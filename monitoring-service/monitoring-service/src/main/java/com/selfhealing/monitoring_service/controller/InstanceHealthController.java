
package com.selfhealing.monitoring_service.controller;
import org.springframework.web.bind.annotation.RestController;
import com.selfhealing.monitoring_service.classifier.FailureClassifier;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class InstanceHealthController {

    private final DiscoveryClient discoveryClient;
    private final FailureClassifier failureClassifier;
    private final RestClient restClient;

    public InstanceHealthController(
            DiscoveryClient discoveryClient,
            FailureClassifier failureClassifier) {

        this.discoveryClient = discoveryClient;
        this.failureClassifier = failureClassifier;
        this.restClient = RestClient.create();
    }

    @GetMapping("/monitor/instances")
    public Map<String, String> getInstanceHealth() {

        Map<String, String> results = new LinkedHashMap<>();

        List<ServiceInstance> instances =
                discoveryClient.getInstances("PRODUCT-SERVICE");

        for (ServiceInstance instance : instances) {

            String instanceUrl = instance.getUri().toString();
            String instanceKey = instance.getHost()
                    + ":" + instance.getPort();

            try {

                long start = System.currentTimeMillis();

                restClient.get()
                        .uri(instanceUrl + "/products")
                        .retrieve()
                        .toBodilessEntity();

                long responseTime =
                        System.currentTimeMillis() - start;

                String status =
                        failureClassifier.classify(
                                true, responseTime
                        );

                results.put(instanceKey, status);

            } catch (Exception e) {

                results.put(
                        instanceKey,
                        failureClassifier.classify(false, 0)
                );
            }
        }

        return results;
    }
}

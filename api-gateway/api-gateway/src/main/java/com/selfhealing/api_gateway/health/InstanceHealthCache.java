
package com.selfhealing.api_gateway.health;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.core.ParameterizedTypeReference;

import java.util.Map;

@Configuration
@EnableScheduling
public class InstanceHealthCache {

    private final RestClient restClient;

    private volatile Map<String, String> statuses = Map.of();
    private volatile long lastUpdated = 0;

    public InstanceHealthCache() {

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(2000);
        factory.setReadTimeout(10000);

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    @Scheduled(initialDelay = 1000, fixedDelay = 5000)
    public void refreshHealthStatuses() {

        try {

            Map<String, String> response = restClient.get()
                    .uri("http://localhost:8083/monitor/instances")
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, String>>() {});

            if (response != null) {

                statuses = Map.copyOf(response);
                lastUpdated = System.currentTimeMillis();

                System.out.println(
                        ">>> GATEWAY HEALTH CACHE UPDATED: "
                                + statuses
                );
            }

        } catch (Exception e) {

            System.out.println(
                    ">>> GATEWAY HEALTH CACHE ERROR: "
                            + e.getMessage()
            );
        }
    }

    public String getInstanceStatus(String host, int port) {

        // Do not trust outdated monitoring information.
        if (System.currentTimeMillis() - lastUpdated > 15000) {
            return "UNKNOWN";
        }

        return statuses.getOrDefault(
                host + ":" + port,
                "UNKNOWN"
        );
    }
}

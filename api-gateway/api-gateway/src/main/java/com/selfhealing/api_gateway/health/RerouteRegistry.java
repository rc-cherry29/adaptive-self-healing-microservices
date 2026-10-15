package com.selfhealing.api_gateway.health;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RerouteRegistry {

    private static final long EXCLUSION_DURATION_MS = 60_000;

    private final Map<String, Long> excludedInstances =
            new ConcurrentHashMap<>();

    public void excludeInstance(String host, int port) {

        String key = host + ":" + port;

        excludedInstances.put(
                key,
                System.currentTimeMillis() + EXCLUSION_DURATION_MS
        );

        System.out.println(
                ">>> NRDA REROUTE | Excluded instance: " + key
        );
    }

    public boolean isExcluded(String host, int port) {

        String key = host + ":" + port;

        Long expiry = excludedInstances.get(key);

        if (expiry == null) {
            return false;
        }

        if (System.currentTimeMillis() >= expiry) {
            excludedInstances.remove(key, expiry);
            return false;
        }

        return true;
    }
}
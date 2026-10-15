package com.selfhealing.api_gateway.health;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/reroute")
public class RerouteController {

    private final RerouteRegistry rerouteRegistry;

    public RerouteController(RerouteRegistry rerouteRegistry) {
        this.rerouteRegistry = rerouteRegistry;
    }

    @PostMapping("/exclude")
    public ResponseEntity<String> excludeInstance(
            @RequestParam String host,
            @RequestParam int port) {

        if (!"localhost".equalsIgnoreCase(host)
                || port != 8081) {

            return ResponseEntity.badRequest()
                    .body("REROUTE_INVALID_INSTANCE");
        }

        rerouteRegistry.excludeInstance(host, port);

        return ResponseEntity.ok("REROUTE_EXCLUSION_REGISTERED");
    }
}
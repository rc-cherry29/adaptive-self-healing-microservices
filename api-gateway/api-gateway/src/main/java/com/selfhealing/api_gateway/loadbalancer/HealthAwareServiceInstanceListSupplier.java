package com.selfhealing.api_gateway.loadbalancer;

import com.selfhealing.api_gateway.health.InstanceHealthCache;
import com.selfhealing.api_gateway.health.RerouteRegistry;
import com.selfhealing.api_gateway.health.BackupRegistry;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.loadbalancer.core.DelegatingServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;

import reactor.core.publisher.Flux;

import java.util.List;

public class HealthAwareServiceInstanceListSupplier
        extends DelegatingServiceInstanceListSupplier {

    private final InstanceHealthCache healthCache;
    private final RerouteRegistry rerouteRegistry;
    private final BackupRegistry backupRegistry;

    public HealthAwareServiceInstanceListSupplier(
            ServiceInstanceListSupplier delegate,
            InstanceHealthCache healthCache,
            RerouteRegistry rerouteRegistry,
            BackupRegistry backupRegistry) {

        super(delegate);

        this.healthCache = healthCache;
        this.rerouteRegistry = rerouteRegistry;
        this.backupRegistry = backupRegistry;
    }

    @Override
    public Flux<List<ServiceInstance>> get() {
        return delegate.get().map(this::filterInstances);
    }

    @Override
    public Flux<List<ServiceInstance>> get(Request request) {
        return delegate.get(request).map(this::filterInstances);
    }

    private List<ServiceInstance> filterInstances(
            List<ServiceInstance> instances) {

        // STEP 1: NRDA SWITCH_TO_BACKUP
        // When backup mode is enabled, use only the healthy backup.

        if (backupRegistry.isBackupModeEnabled()) {

            List<ServiceInstance> healthyBackup = instances.stream()
                    .filter(instance ->
                            backupRegistry.isBackupInstance(
                                    instance.getHost(),
                                    instance.getPort()
                            )
                    )
                    .filter(instance ->
                            !rerouteRegistry.isExcluded(
                                    instance.getHost(),
                                    instance.getPort()
                            )
                    )
                    .filter(instance ->
                            "HEALTHY".equals(
                                    healthCache.getInstanceStatus(
                                            instance.getHost(),
                                            instance.getPort()
                                    )
                            )
                    )
                    .toList();

            if (healthyBackup.isEmpty()) {

                System.out.println(
                        ">>> NRDA BACKUP UNAVAILABLE"
                                + " | No healthy backup instance"
                );

            } else {

                System.out.println(
                        ">>> NRDA BACKUP ROUTING ACTIVE"
                                + " | Instance: localhost:8086"
                );
            }

            return healthyBackup;
        }

        // STEP 2: NRDA REROUTE
        // Exclude instances temporarily blocked by NRDA.

        List<ServiceInstance> eligible = instances.stream()
                .filter(instance ->
                        !rerouteRegistry.isExcluded(
                                instance.getHost(),
                                instance.getPort()
                        )
                )
                .toList();

        // STEP 3: Prefer healthy instances.

        List<ServiceInstance> healthy = eligible.stream()
                .filter(instance ->
                        "HEALTHY".equals(
                                healthCache.getInstanceStatus(
                                        instance.getHost(),
                                        instance.getPort()
                                )
                        )
                )
                .toList();

        if (!healthy.isEmpty()) {

            System.out.println(
                    ">>> HEALTHY INSTANCES AVAILABLE:"
            );

            healthy.forEach(instance ->
                    System.out.println(
                            ">>> " + instance.getHost()
                                    + ":" + instance.getPort()
                    )
            );

            return healthy;
        }

        // STEP 4: Fall back to UNKNOWN instances.
        // Never include explicitly excluded instances.

        List<ServiceInstance> unknown = eligible.stream()
                .filter(instance ->
                        "UNKNOWN".equals(
                                healthCache.getInstanceStatus(
                                        instance.getHost(),
                                        instance.getPort()
                                )
                        )
                )
                .toList();

        if (!unknown.isEmpty()) {
            return unknown;
        }

        // STEP 5: No eligible instances available.

        System.out.println(
                ">>> NO ELIGIBLE PRODUCT SERVICE INSTANCES"
        );

        return List.of();
    }
}
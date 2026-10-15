package com.selfhealing.api_gateway.loadbalancer;

import com.selfhealing.api_gateway.health.BackupRegistry;
import com.selfhealing.api_gateway.health.InstanceHealthCache;
import com.selfhealing.api_gateway.health.RerouteRegistry;

import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ConfigurableApplicationContext;

@Configuration
public class ProductLoadBalancerConfiguration {

    @Bean
    public ServiceInstanceListSupplier serviceInstanceListSupplier(
            ConfigurableApplicationContext context,
            InstanceHealthCache healthCache,
            RerouteRegistry rerouteRegistry,
            BackupRegistry backupRegistry) {

        ServiceInstanceListSupplier delegate =
                ServiceInstanceListSupplier.builder()
                        .withBlockingDiscoveryClient()
                        .build(context);

        return new HealthAwareServiceInstanceListSupplier(
                delegate,
                healthCache,
                rerouteRegistry,
                backupRegistry
        );
    }
}
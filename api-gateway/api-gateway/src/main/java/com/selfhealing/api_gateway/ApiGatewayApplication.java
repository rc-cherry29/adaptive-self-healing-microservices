package com.selfhealing.api_gateway;

import com.selfhealing.api_gateway.loadbalancer.ProductLoadBalancerConfiguration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(
		excludeFilters = @ComponentScan.Filter(
				type = FilterType.ASSIGNABLE_TYPE,
				classes = ProductLoadBalancerConfiguration.class
		)
)
@LoadBalancerClient(
		name = "PRODUCT-SERVICE",
		configuration = ProductLoadBalancerConfiguration.class
)
public class ApiGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiGatewayApplication.class, args);
	}
}
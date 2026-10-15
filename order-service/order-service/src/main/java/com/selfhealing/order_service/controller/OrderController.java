package com.selfhealing.order_service.controller;

import com.selfhealing.order_service.client.ProductClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final ProductClient productClient;

    public OrderController(ProductClient productClient) {
        this.productClient = productClient;
    }

    @GetMapping
    public String getOrders() {
        String productResponse = productClient.getProducts();

        return "Order Service is running | Response from Product Service: "
                + productResponse;
    }
}
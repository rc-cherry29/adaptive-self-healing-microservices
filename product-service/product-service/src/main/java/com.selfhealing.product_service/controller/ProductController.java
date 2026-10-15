package com.selfhealing.product_service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final FaultInjectionController faultInjectionController;

    public ProductController(
            FaultInjectionController faultInjectionController) {

        this.faultInjectionController = faultInjectionController;
    }

    @GetMapping
    public String getProducts() {

        // ==========================================
        // SERVICE DOWN FAULT
        // ==========================================

        if (faultInjectionController.isFailureEnabled()) {

            throw new RuntimeException(
                    "Simulated Product Service Failure"
            );
        }


        // ==========================================
        // SLOW RESPONSE FAULT
        // ==========================================

        if (faultInjectionController.isSlowResponseEnabled()) {

            try {

                long delay =
                        faultInjectionController
                                .getSlowResponseDelayMs();

                System.out.println(
                        ">>> SLOW RESPONSE FAULT ACTIVE"
                                + " | Delay: "
                                + delay
                                + " ms"
                );

                Thread.sleep(delay);

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();

                throw new RuntimeException(
                        "Slow response simulation interrupted",
                        e
                );
            }
        }


        // ==========================================
        // NORMAL RESPONSE
        // ==========================================

        return "Product Service is running";
    }
}
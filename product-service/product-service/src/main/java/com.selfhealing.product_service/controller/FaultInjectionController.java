package com.selfhealing.product_service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fault")
public class FaultInjectionController {

    // ==========================================
    // SERVICE DOWN FAULT
    // ==========================================



    // Simulated delay = 3 seconds
    private long slowResponseDelayMs = 3000;


    // ==========================================
    // FAULT INJECTION TIMESTAMP
    // ==========================================

    private volatile boolean failureEnabled = false;
    private volatile boolean slowResponseEnabled = false;
    private volatile long injectedAt = 0L;


    // ==========================================
    // SERVICE DOWN - ENABLE
    // ==========================================

    @PostMapping("/enable")
    public String enableFailure() {

        long timestamp = System.currentTimeMillis();

        slowResponseEnabled = false;
        injectedAt = timestamp;
        failureEnabled = true;

        return "Failure enabled at: " + injectedAt;
    }


    // ==========================================
    // SERVICE DOWN - DISABLE
    // ==========================================

    @PostMapping("/disable")
    public String disableFailure() {

        failureEnabled = false;

        injectedAt = 0L;

        return "Failure disabled";
    }


    // ==========================================
    // SLOW RESPONSE - ENABLE
    // ==========================================

    @PostMapping("/slow/enable")
    public String enableSlowResponse() {

        slowResponseEnabled = true;

        // Prevent two fault types running together
        failureEnabled = false;

        injectedAt = System.currentTimeMillis();

        return "Slow response enabled at: "
                + injectedAt
                + " | Delay: "
                + slowResponseDelayMs
                + " ms";
    }


    // ==========================================
    // SLOW RESPONSE - DISABLE
    // ==========================================

    @PostMapping("/slow/disable")
    public String disableSlowResponse() {

        slowResponseEnabled = false;
        injectedAt = 0L;

        return "Slow response disabled";
    }


    // ==========================================
    // GENERAL FAULT STATUS
    // ==========================================

    @GetMapping("/status")
    public String getStatus() {

        return "failureEnabled="
                + failureEnabled

                + ", slowResponseEnabled="
                + slowResponseEnabled

                + ", slowResponseDelayMs="
                + slowResponseDelayMs

                + ", injectedAt="
                + injectedAt;
    }


    // ==========================================
    // INJECTION TIMESTAMP
    // ==========================================

    @GetMapping("/injected-at")
    public long getInjectedAtTimestamp() {

        return injectedAt;
    }


    // ==========================================
    // GETTERS USED BY PRODUCT SERVICE
    // ==========================================

    public boolean isFailureEnabled() {

        return failureEnabled;
    }


    public boolean isSlowResponseEnabled() {

        return slowResponseEnabled;
    }


    public long getSlowResponseDelayMs() {

        return slowResponseDelayMs;
    }


    public long getInjectedAt() {

        return injectedAt;
    }
}
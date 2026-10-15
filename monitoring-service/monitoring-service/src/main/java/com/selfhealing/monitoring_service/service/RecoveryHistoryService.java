package com.selfhealing.monitoring_service.service;

import com.selfhealing.monitoring_service.logging.RecoveryEvent;
import com.selfhealing.monitoring_service.repository.RecoveryEventRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RecoveryHistoryService {

    private final RecoveryEventRepository recoveryEventRepository;

    public RecoveryHistoryService(
            RecoveryEventRepository recoveryEventRepository) {

        this.recoveryEventRepository =
                recoveryEventRepository;
    }


    // =================================================
    // RECOVERY RISK
    // =================================================

    public double calculateRecoveryRisk(
            String serviceName,
            String recoveryAction) {

        List<RecoveryEvent> previousAttempts =
                recoveryEventRepository
                        .findTop10ByServiceNameAndNrdaActionOrderByTimestampDesc(
                                serviceName,
                                recoveryAction
                        );


        // No historical information yet.
        // Use neutral risk instead of assuming
        // success or failure.

        if (previousAttempts.isEmpty()) {

            System.out.println(
                    ">>> RECOVERY HISTORY"
                            + " | Service: "
                            + serviceName
                            + " | Action: "
                            + recoveryAction
                            + " | Previous Attempts: 0"
                            + " | Recovery Risk: 5.00"
            );

            return 5.0;
        }


        int successfulRecoveries = 0;


        for (RecoveryEvent event : previousAttempts) {

            if ("RECOVERY_SUCCESS".equals(
                    event.getVerificationResult())) {

                successfulRecoveries++;
            }
        }


        double successRate =
                (double) successfulRecoveries
                        / previousAttempts.size();


        // Higher success rate = lower recovery risk.
        // Example:
        // 100% success -> risk 0
        // 70% success  -> risk 3
        // 50% success  -> risk 5
        // 0% success   -> risk 10.

        double recoveryRisk =
                (1.0 - successRate) * 10.0;


        System.out.println(
                ">>> RECOVERY HISTORY"
                        + " | Service: "
                        + serviceName
                        + " | Action: "
                        + recoveryAction
                        + " | Previous Attempts: "
                        + previousAttempts.size()
                        + " | Successful: "
                        + successfulRecoveries
                        + " | Success Rate: "
                        + String.format(
                        "%.2f",
                        successRate * 100
                )
                        + "%"
                        + " | Recovery Risk: "
                        + String.format(
                        "%.2f",
                        recoveryRisk
                )
        );


        return recoveryRisk;
    }
}
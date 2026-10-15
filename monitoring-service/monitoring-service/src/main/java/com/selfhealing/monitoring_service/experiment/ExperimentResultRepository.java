package com.selfhealing.monitoring_service.experiment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExperimentResultRepository
        extends JpaRepository<ExperimentResult, Long> {

    List<ExperimentResult>
    findByDecisionModeOrderByTimestampDesc(String decisionMode);
}
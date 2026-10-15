package com.selfhealing.monitoring_service.repository;

import com.selfhealing.monitoring_service.logging.RecoveryEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecoveryEventRepository
        extends JpaRepository<RecoveryEvent, Long> {

    List<RecoveryEvent>
    findTop10ByServiceNameAndNrdaActionOrderByTimestampDesc(
            String serviceName,
            String nrdaAction
    );
}
package com.selfhealing.api_gateway.health;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/backup")
public class BackupController {

    private final BackupRegistry backupRegistry;
    private final InstanceHealthCache healthCache;

    public BackupController(
            BackupRegistry backupRegistry,
            InstanceHealthCache healthCache) {

        this.backupRegistry = backupRegistry;
        this.healthCache = healthCache;
    }

    @PostMapping("/activate")
    public ResponseEntity<String> activateBackup() {

        String status = healthCache.getInstanceStatus(
                "localhost", 8086
        );

        if (!"HEALTHY".equals(status)) {
            return ResponseEntity.status(503)
                    .body("BACKUP_UNAVAILABLE");
        }

        backupRegistry.enableBackupMode();

        return ResponseEntity.ok("BACKUP_MODE_ACTIVATED");
    }

    @PostMapping("/deactivate")
    public ResponseEntity<String> deactivateBackup() {

        backupRegistry.disableBackupMode();

        return ResponseEntity.ok("BACKUP_MODE_DEACTIVATED");
    }

    @GetMapping("/status")
    public ResponseEntity<String> getBackupStatus() {

        return ResponseEntity.ok(
                backupRegistry.isBackupModeEnabled()
                        ? "BACKUP_MODE_ACTIVE"
                        : "BACKUP_MODE_INACTIVE"
        );
    }
}
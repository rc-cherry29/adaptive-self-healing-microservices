package com.selfhealing.api_gateway.health;

import org.springframework.stereotype.Component;

@Component
public class BackupRegistry {

    private volatile boolean backupModeEnabled = false;

    private static final String BACKUP_HOST = "localhost";
    private static final int BACKUP_PORT = 8086;

    public void enableBackupMode() {
        backupModeEnabled = true;

        System.out.println(
                ">>> NRDA BACKUP MODE ENABLED"
                        + " | Backup: "
                        + BACKUP_HOST + ":" + BACKUP_PORT
        );
    }

    public void disableBackupMode() {
        backupModeEnabled = false;

        System.out.println(
                ">>> NRDA BACKUP MODE DISABLED"
        );
    }

    public boolean isBackupModeEnabled() {
        return backupModeEnabled;
    }

    public boolean isBackupInstance(String host, int port) {
        return BACKUP_HOST.equalsIgnoreCase(host)
                && BACKUP_PORT == port;
    }
}
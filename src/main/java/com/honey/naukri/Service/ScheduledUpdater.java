package com.honey.naukri.Service;

import com.honey.naukri.Repo.HistoryRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledUpdater {
    private final NaukriUpdater updater;
    private final HistoryRepository history;

    public ScheduledUpdater(NaukriUpdater updater, HistoryRepository history) {
        this.updater = updater;
        this.history = history;
    }

    @Scheduled(cron = "${naukri.update-cron}", zone = "${naukri.timezone}")
    public void scheduledRun() {
        System.out.println("[SCHEDULER] Naukri scheduled update started at " + System.currentTimeMillis());
        runOnce();
    }

    public void runOnce() {
        System.out.println("[UPDATE] Starting profile update...");
        long id = history.start();
        try {
            updater.update();
            history.finish(id, "SUCCESS", "Profile/resume content re-saved.");
            System.out.println("[UPDATE] ✓ Update completed successfully");
        } catch (Exception e) {
            System.out.println("[UPDATE] ✗ Update failed: " + e.getMessage());
            history.finish(id, "FAILED", e.getMessage());
        }
    }
}

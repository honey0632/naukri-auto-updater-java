package com.honey.naukri.Controller;

import com.honey.naukri.Repo.HistoryRepository;
import com.honey.naukri.Service.ScheduledUpdater;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class UpdateController {
    private final ScheduledUpdater updater;
    private final HistoryRepository history;

    public UpdateController(ScheduledUpdater updater, HistoryRepository history) {
        this.updater = updater;
        this.history = history;
    }

    @PostMapping("/update")
    public Map<String,String> updateNow() {
        updater.runOnce();
        return Map.of("status", "started");
    }

    @GetMapping("/history")
    public List<Map<String,Object>> history() {
        return history.recent(50);
    }
}

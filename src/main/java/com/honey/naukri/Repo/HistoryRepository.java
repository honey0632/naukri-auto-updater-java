package com.honey.naukri.Repo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Repository
public class HistoryRepository {
    private final JdbcTemplate jdbc;

    public HistoryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public long start() {
        jdbc.update("INSERT INTO update_history(started_at,status) VALUES(?,?)",
                Instant.now().toString(), "RUNNING");
        return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
    }

    public void finish(long id, String status, String message) {
        jdbc.update("UPDATE update_history SET finished_at=?, status=?, message=? WHERE id=?",
                Instant.now().toString(), status, message, id);
    }

    public List<Map<String,Object>> recent(int limit) {
        return jdbc.queryForList(
                "SELECT * FROM update_history ORDER BY id DESC LIMIT ?", limit);
    }
}

package com.digitallife.repository;

import com.digitallife.domain.InnerThoughtLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class InnerThoughtLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public InnerThoughtLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long insert(String triggerType, String sensorySummary, String innerThought, boolean didSpeak) {
        String now = Instant.now().toString();
        return jdbcTemplate.queryForObject("""
                INSERT INTO inner_thought_log (trigger_type, sensory_summary, inner_thought, did_speak, created_at)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class,
                triggerType,
                sensorySummary,
                innerThought,
                didSpeak ? 1 : 0,
                now
        );
    }

    public void markDidSpeak(Long thoughtLogId) {
        int updated = jdbcTemplate.update("""
                UPDATE inner_thought_log SET did_speak = 1 WHERE id = ? AND did_speak = 0
                """, thoughtLogId);
        if (updated != 1) {
            throw new IllegalStateException("思考记录不存在或已完成发言");
        }
    }

    public Optional<InnerThoughtLog> findLatest() {
        List<InnerThoughtLog> list = jdbcTemplate.query("""
                SELECT id, trigger_type, sensory_summary, inner_thought, did_speak, created_at
                FROM inner_thought_log
                ORDER BY id DESC
                LIMIT 1
                """,
                (rs, rowNum) -> new InnerThoughtLog(
                        rs.getLong("id"),
                        rs.getString("trigger_type"),
                        rs.getString("sensory_summary"),
                        rs.getString("inner_thought"),
                        rs.getInt("did_speak") == 1,
                        rs.getString("created_at")
                )
        );
        return list.stream().findFirst();
    }
}

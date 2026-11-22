package com.digitallife.repository;

import com.digitallife.domain.LongTermMemory;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class LongTermMemoryRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<LongTermMemory> ROW_MAPPER = (rs, rowNum) -> {
        long srcId = rs.getLong("source_thought_id");
        Long nullableSrcId = rs.wasNull() ? null : srcId;
        return new LongTermMemory(
                rs.getLong("id"),
                rs.getString("memory_type"),
                rs.getString("fact_summary"),
                rs.getString("embedding_json"),
                rs.getInt("importance"),
                nullableSrcId,
                rs.getString("last_recalled_at"),
                rs.getString("created_at")
        );
    };

    public LongTermMemoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void ensureEmbeddingModelColumn() {
        List<String> columns = jdbcTemplate.query("PRAGMA table_info(long_term_memory)",
                (result, row) -> result.getString("name"));
        if (!columns.contains("embedding_model")) {
            jdbcTemplate.execute("ALTER TABLE long_term_memory ADD COLUMN embedding_model TEXT");
        }
    }

    public Optional<Long> insert(String memoryType, String factSummary, String embeddingJson,
                                 String embeddingModel, int importance, Long sourceThoughtId) {
        String now = Instant.now().toString();
        return jdbcTemplate.query("""
                INSERT INTO long_term_memory
                    (memory_type, fact_summary, embedding_json, embedding_model, importance, source_thought_id, created_at)
                SELECT ?, ?, ?, ?, ?, ?, ?
                WHERE NOT EXISTS (SELECT 1 FROM long_term_memory WHERE memory_type = ? AND fact_summary = ?)
                RETURNING id
                """, (result, row) -> result.getLong("id"),
                memoryType,
                factSummary,
                embeddingJson,
                embeddingModel,
                importance,
                sourceThoughtId,
                now,
                memoryType,
                factSummary
        ).stream().findFirst();
    }

    public List<LongTermMemory> findAllCandidates(String embeddingModel) {
        return jdbcTemplate.query("""
                SELECT id, memory_type, fact_summary, embedding_json, importance, source_thought_id, last_recalled_at, created_at
                FROM long_term_memory
                WHERE embedding_model = ?
                ORDER BY id DESC
                """, ROW_MAPPER, embeddingModel);
    }

    public boolean containsFact(String type, String summary) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM long_term_memory WHERE memory_type = ? AND fact_summary = ?)
                """, Integer.class, type, summary);
        return count != null && count != 0;
    }

    public List<LongTermMemory> findStaleVectors(String embeddingModel, int limit) {
        return jdbcTemplate.query("""
                SELECT id, memory_type, fact_summary, embedding_json, importance, source_thought_id, last_recalled_at, created_at
                FROM long_term_memory
                WHERE embedding_model IS NULL OR embedding_model != ?
                ORDER BY id
                LIMIT ?
                """, ROW_MAPPER, embeddingModel, limit);
    }

    public void putVector(Long id, String embeddingJson, String embeddingModel) {
        jdbcTemplate.update("UPDATE long_term_memory SET embedding_json = ?, embedding_model = ? WHERE id = ?",
                embeddingJson, embeddingModel, id);
    }

    public void updateLastRecalledAt(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String now = Instant.now().toString();
        for (Long id : ids) {
            jdbcTemplate.update("UPDATE long_term_memory SET last_recalled_at = ? WHERE id = ?", now, id);
        }
    }
}

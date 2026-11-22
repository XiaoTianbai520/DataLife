package com.digitallife.repository;

import com.digitallife.domain.ChatMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class ChatMessageRepository {

    private final JdbcTemplate jdbcTemplate;
    private final InnerThoughtLogRepository thoughtLogRepository;

    private static final RowMapper<ChatMessage> ROW_MAPPER = (rs, rowNum) -> {
        long thoughtId = rs.getLong("thought_log_id");
        Long nullableThoughtId = rs.wasNull() ? null : thoughtId;
        return new ChatMessage(
                rs.getLong("id"),
                nullableThoughtId,
                rs.getString("role"),
                rs.getString("content"),
                rs.getString("created_at")
        );
    };

    public ChatMessageRepository(JdbcTemplate jdbcTemplate, InnerThoughtLogRepository thoughtLogRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.thoughtLogRepository = thoughtLogRepository;
    }

    public ChatMessage insert(Long thoughtLogId, String role, String content) {
        String now = Instant.now().toString();
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO chat_message (thought_log_id, role, content, created_at)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """, Long.class,
                thoughtLogId,
                role,
                content,
                now
        );
        return new ChatMessage(id, thoughtLogId, role, content, now);
    }

    @Transactional
    public ChatMessage insertAssistantReply(String content, Long thoughtLogId) {
        thoughtLogRepository.markDidSpeak(thoughtLogId);
        return insert(thoughtLogId, "ASSISTANT", content);
    }

    public Optional<ChatMessage> findById(Long id) {
        return jdbcTemplate.query("""
                SELECT id, thought_log_id, role, content, created_at
                FROM chat_message WHERE id = ?
                """, ROW_MAPPER, id).stream().findFirst();
    }

    /**
     * 向更早消息翻页，每页按 ID 升序返回。
     */
    public List<ChatMessage> findHistory(Long beforeId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<ChatMessage> descList;
        if (beforeId != null && beforeId > 0) {
            descList = jdbcTemplate.query("""
                    SELECT id, thought_log_id, role, content, created_at
                    FROM chat_message
                    WHERE id < ?
                    ORDER BY id DESC
                    LIMIT ?
                    """, ROW_MAPPER, beforeId, safeLimit);
        } else {
            descList = jdbcTemplate.query("""
                    SELECT id, thought_log_id, role, content, created_at
                    FROM chat_message
                    ORDER BY id DESC
                    LIMIT ?
                    """, ROW_MAPPER, safeLimit);
        }
        Collections.reverse(descList);
        return descList;
    }

    /**
     * 获取最近 N 条可见消息，用于启动时恢复短期滑动窗口。
     */
    public List<ChatMessage> findRecentMessages(int count) {
        return findHistory(null, count);
    }
}

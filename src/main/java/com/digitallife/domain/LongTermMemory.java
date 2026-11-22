package com.digitallife.domain;

/**
 * 长期记忆向量实体（对应表 long_term_memory）。
 */
public record LongTermMemory(
        Long id,
        String memoryType,
        String factSummary,
        String embeddingJson,
        int importance,
        Long sourceThoughtId,
        String lastRecalledAt,
        String createdAt
) {
}

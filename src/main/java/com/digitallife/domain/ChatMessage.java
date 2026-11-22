package com.digitallife.domain;

/**
 * 可见对话历史实体（对应表 chat_message）。
 */
public record ChatMessage(
        Long id,
        Long thoughtLogId,
        String role,
        String content,
        String createdAt
) {
}

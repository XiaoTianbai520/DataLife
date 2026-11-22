package com.digitallife.domain;

/**
 * 内心独白与唤醒日志实体（对应表 inner_thought_log）。
 * 严禁存储任何原始截图字节或图片路径。
 */
public record InnerThoughtLog(
        Long id,
        String triggerType,
        String sensorySummary,
        String innerThought,
        boolean didSpeak,
        String createdAt
) {
}

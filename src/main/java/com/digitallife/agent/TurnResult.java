package com.digitallife.agent;

/**
 * 数字生命单轮唤醒推理结果。
 */
public record TurnResult(
        Long thoughtLogId,
        String innerThought,
        boolean didSpeak,
        String spokenContent
) {
}

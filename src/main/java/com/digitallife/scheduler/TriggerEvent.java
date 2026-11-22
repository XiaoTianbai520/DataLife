package com.digitallife.scheduler;

import java.time.Instant;

/**
 * 调度触发事件：支持在 PriorityBlockingQueue 中按优先级（数值越小优先级越高）+ 创建时间排序。
 */
public record TriggerEvent(
        EventType type,
        int priority,
        String userInput,
        Long userMessageId,
        Instant createdAt
) implements Comparable<TriggerEvent> {

    public static final int PRIORITY_USER_MESSAGE = 1;
    public static final int PRIORITY_HEARTBEAT = 10;

    public static TriggerEvent ofUserMessage(String userInput, Long userMessageId) {
        return new TriggerEvent(EventType.USER_MESSAGE, PRIORITY_USER_MESSAGE, userInput, userMessageId, Instant.now());
    }

    public static TriggerEvent ofHeartbeat() {
        return new TriggerEvent(EventType.HEARTBEAT, PRIORITY_HEARTBEAT, null, null, Instant.now());
    }

    @Override
    public int compareTo(TriggerEvent other) {
        int cmp = Integer.compare(this.priority, other.priority);
        if (cmp != 0) {
            return cmp;
        }
        return this.createdAt.compareTo(other.createdAt);
    }
}

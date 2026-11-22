package com.digitallife.scheduler;

import java.time.Instant;
import java.util.UUID;

/**
 * 调度触发事件，主动消息按保存顺序处理，定时观察使用较低优先级。
 */
public record TriggerEvent(
        EventType type,
        int priority,
        String userInput,
        Long userMessageId,
        Instant createdAt,
        String turnId
) implements Comparable<TriggerEvent> {

    public static final int PRIORITY_USER_MESSAGE = 1;
    public static final int PRIORITY_HEARTBEAT = 10;

    public static TriggerEvent ofUserMessage(String userInput, Long userMessageId) {
        return new TriggerEvent(EventType.USER_MESSAGE, PRIORITY_USER_MESSAGE, userInput, userMessageId,
                Instant.now(), UUID.randomUUID().toString());
    }

    public static TriggerEvent ofHeartbeat() {
        return new TriggerEvent(EventType.HEARTBEAT, PRIORITY_HEARTBEAT, null, null,
                Instant.now(), UUID.randomUUID().toString());
    }

    @Override
    public int compareTo(TriggerEvent other) {
        int cmp = Integer.compare(this.priority, other.priority);
        if (cmp != 0) {
            return cmp;
        }
        if (type == EventType.USER_MESSAGE) {
            return Long.compare(userMessageId, other.userMessageId);
        }
        return this.createdAt.compareTo(other.createdAt);
    }
}

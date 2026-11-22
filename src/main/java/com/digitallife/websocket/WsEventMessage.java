package com.digitallife.websocket;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * WebSocket 实时推送统一消息结构（对应 api.md 第三节）。
 * eventType: "SPEAK_MESSAGE" | "STATUS_ACTION" | "SYSTEM_NOTICE"
 */
public record WsEventMessage<T>(
        String eventType,
        String timestamp,
        T payload
) {
    public static <T> WsEventMessage<T> of(String eventType, T payload) {
        return new WsEventMessage<>(
                eventType,
                OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                payload
        );
    }
}

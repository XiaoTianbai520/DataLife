package com.digitallife.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 实时事件推送服务（ws://127.0.0.1:8080/ws/events）：
 * 支持推送 SPEAK_MESSAGE（角色开口发言）、STATUS_ACTION（轻状态动作反馈）、SYSTEM_NOTICE（系统通知）。
 */
@Service
public class EventPushService extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(EventPushService.class);

    public static final String EVENT_SPEAK_MESSAGE = "SPEAK_MESSAGE";
    public static final String EVENT_STATUS_ACTION = "STATUS_ACTION";
    public static final String EVENT_SYSTEM_NOTICE = "SYSTEM_NOTICE";

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper;

    public EventPushService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("桌面前端已建立 WebSocket 连接: sessionId={}, 当前活跃连接数={}", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("桌面前端已断开 WebSocket 连接: sessionId={}, 当前活跃连接数={}", session.getId(), sessions.size());
    }

    public void pushSpeakMessage(Long messageId, String triggerType, String content) {
        Map<String, Object> payload = Map.of(
                "messageId", messageId,
                "triggerType", triggerType,
                "content", content
        );
        broadcast(WsEventMessage.of(EVENT_SPEAK_MESSAGE, payload));
    }

    public void pushStatusAction(String action, String hintText) {
        Map<String, Object> payload = Map.of(
                "action", action,
                "hintText", hintText
        );
        broadcast(WsEventMessage.of(EVENT_STATUS_ACTION, payload));
    }

    public void pushSystemNotice(String level, String code, String message) {
        Map<String, Object> payload = Map.of(
                "level", level,
                "code", code,
                "message", message
        );
        broadcast(WsEventMessage.of(EVENT_SYSTEM_NOTICE, payload));
    }

    public void broadcast(WsEventMessage<?> eventMessage) {
        if (sessions.isEmpty()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(eventMessage);
            TextMessage textMessage = new TextMessage(json);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        synchronized (session) {
                            session.sendMessage(textMessage);
                        }
                    } catch (IOException e) {
                        log.warn("推送 WebSocket 消息失败 (sessionId={}): {}", session.getId(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("序列化 WebSocket 事件失败: {}", e.getMessage());
        }
    }
}

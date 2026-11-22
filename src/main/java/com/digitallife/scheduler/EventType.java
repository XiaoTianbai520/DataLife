package com.digitallife.scheduler;

/**
 * 唤醒事件类型枚举。
 */
public enum EventType {
    /** 被动触发：主人主动发送消息（高优先级 Priority=1） */
    USER_MESSAGE,

    /** 主动心跳：定时观察唤醒（低优先级 Priority=10） */
    HEARTBEAT
}

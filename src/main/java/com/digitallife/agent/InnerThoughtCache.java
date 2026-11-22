package com.digitallife.agent;

import com.digitallife.repository.InnerThoughtLogRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 上一轮内心独白缓存（InnerThoughtCache）：
 * 让角色在连续多次定时观察之间保持情绪流与思绪的前后连贯，启动时从 SQLite 自动恢复最新一条记录。
 */
@Component
public class InnerThoughtCache {

    private static final Logger log = LoggerFactory.getLogger(InnerThoughtCache.class);

    private final InnerThoughtLogRepository thoughtLogRepository;
    private volatile String lastThought = "（刚刚苏醒，正在悄悄适应主人的桌面环境……）";
    private volatile Instant updatedAt = Instant.now();

    public InnerThoughtCache(InnerThoughtLogRepository thoughtLogRepository) {
        this.thoughtLogRepository = thoughtLogRepository;
    }

    @PostConstruct
    public void restoreFromDatabase() {
        thoughtLogRepository.findLatest().ifPresent(latest -> {
            this.lastThought = latest.innerThought();
            this.updatedAt = Instant.now();
            log.info("InnerThoughtCache 已从 SQLite 恢复上一轮内心独白");
        });
    }

    public String getLastThought() {
        return lastThought;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(String thought) {
        if (thought != null && !thought.isBlank()) {
            this.lastThought = thought.trim();
            this.updatedAt = Instant.now();
        }
    }
}

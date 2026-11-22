package com.digitallife.scheduler;

import com.digitallife.repository.BotConfigRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 主动心跳定时调度器（HeartbeatScheduler）：
 * 按设定间隔（每 N 分钟）自动提交一次 HEARTBEAT 观察事件；
 * 当主人主动发言时支持 resetTimer() 重置计时，避免刚聊完天立即撞上定时心跳。
 */
@Component
public class HeartbeatScheduler {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatScheduler.class);

    private final EventDispatcher eventDispatcher;
    private final BotConfigRepository configRepository;
    private final ScheduledThreadPoolExecutor executor;

    private volatile int intervalMinutes;
    private ScheduledFuture<?> currentTask;
    private long scheduleVersion;

    public HeartbeatScheduler(EventDispatcher eventDispatcher, BotConfigRepository configRepository) {
        this.eventDispatcher = eventDispatcher;
        this.configRepository = configRepository;
        this.executor = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "digital-life-heartbeat-scheduler");
            t.setDaemon(true);
            return t;
        });
        this.executor.setRemoveOnCancelPolicy(true);
    }

    @PostConstruct
    public synchronized void start() {
        this.intervalMinutes = Math.max(1, configRepository.getInt(BotConfigRepository.KEY_HEARTBEAT_INTERVAL, 5));
        scheduleNext();
        log.info("HeartbeatScheduler 已启动，当前心跳间隔: {} 分钟", intervalMinutes);
    }

    public synchronized void resetTimer() {
        eventDispatcher.discardQueuedHeartbeats();
        scheduleNext();
        log.debug("主人主动交互，已重置心跳计时器 ({} 分钟后触发)", intervalMinutes);
    }

    public synchronized void submitUserMessage(TriggerEvent event) {
        eventDispatcher.submit(event);
        resetTimer();
    }

    public synchronized void updateInterval(int minutes) {
        this.intervalMinutes = Math.max(1, minutes);
        eventDispatcher.discardQueuedHeartbeats();
        scheduleNext();
        log.info("HeartbeatScheduler 心跳间隔已热更新为: {} 分钟", this.intervalMinutes);
    }

    private void scheduleNext() {
        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(false);
        }
        long delaySeconds = (long) intervalMinutes * 60L;
        long version = ++scheduleVersion;
        currentTask = executor.scheduleAtFixedRate(
                () -> enqueueHeartbeat(version),
                delaySeconds,
                delaySeconds,
                TimeUnit.SECONDS
        );
    }

    private synchronized void enqueueHeartbeat(long version) {
        if (version == scheduleVersion) {
            eventDispatcher.submit(TriggerEvent.ofHeartbeat());
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}

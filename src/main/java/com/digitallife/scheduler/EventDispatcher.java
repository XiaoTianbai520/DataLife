package com.digitallife.scheduler;

import com.digitallife.agent.DigitalLifeAgent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单线程优先级事件分发器（EventDispatcher）：
 * 保证数字生命大脑同一时刻只处理一个思考回合，且主人主动发言（Priority=1）能抢占排队中的定时心跳（Priority=10）。
 */
@Component
public class EventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EventDispatcher.class);

    private final PriorityBlockingQueue<TriggerEvent> queue = new PriorityBlockingQueue<>();
    private final DigitalLifeAgent agent;
    private final ExecutorService singleThreadExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public EventDispatcher(DigitalLifeAgent agent) {
        this.agent = agent;
        this.singleThreadExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "digital-life-event-dispatcher");
            t.setDaemon(true);
            return t;
        });
    }

    @PostConstruct
    public void startLoop() {
        if (running.compareAndSet(false, true)) {
            singleThreadExecutor.submit(this::consumeLoop);
            log.info("EventDispatcher 单线程优先级队列已启动");
        }
    }

    public void submit(TriggerEvent event) {
        if (event.type() == EventType.HEARTBEAT) {
            // 若队列中已存在尚未消费的心跳事件，则跳过重复堆积
            boolean alreadyHasHeartbeat = queue.stream().anyMatch(e -> e.type() == EventType.HEARTBEAT);
            if (alreadyHasHeartbeat) {
                log.debug("队列中已有待处理 HEARTBEAT 事件，忽略本次重复入队");
                return;
            }
        }
        queue.offer(event);
        log.info("事件入队: type={}, priority={}, queueSize={}", event.type(), event.priority(), queue.size());
    }

    public void discardQueuedHeartbeats() {
        queue.removeIf(event -> event.type() == EventType.HEARTBEAT);
    }

    private void consumeLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                TriggerEvent event = queue.take();
                agent.handleEvent(event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("EventDispatcher 处理事件异常: {}", e.getMessage(), e);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        singleThreadExecutor.shutdownNow();
    }
}

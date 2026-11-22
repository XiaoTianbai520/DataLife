package com.digitallife.scheduler;

import com.digitallife.agent.DigitalLifeAgent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.PriorityQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.digitallife.scheduler.TurnProgress.Stage.*;

/**
 * 单线程优先级事件分发器（EventDispatcher）：
 * 保证数字生命大脑同一时刻只处理一个思考回合，且主人主动发言（Priority=1）能抢占排队中的定时心跳（Priority=10）。
 */
@Component
public class EventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EventDispatcher.class);

    private final PriorityQueue<TriggerEvent> queue = new PriorityQueue<>();
    private final DigitalLifeAgent agent;
    private final TurnProgress progress;
    private final ExecutorService singleThreadExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private boolean processing;

    public EventDispatcher(DigitalLifeAgent agent, TurnProgress progress) {
        this.agent = agent;
        this.progress = progress;
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

    public synchronized void submit(TriggerEvent event) {
        if (event.type() == EventType.HEARTBEAT) {
            if (processing || !queue.isEmpty()) {
                return;
            }
        } else {
            discardQueuedHeartbeats();
            progress.update(event, QUEUED);
        }
        queue.offer(event);
        notifyAll();
        log.info("事件入队: type={}, priority={}, queueSize={}", event.type(), event.priority(), queue.size());
    }

    public synchronized void discardQueuedHeartbeats() {
        queue.removeIf(event -> event.type() == EventType.HEARTBEAT);
    }

    private synchronized TriggerEvent takeNext() throws InterruptedException {
        while (queue.isEmpty()) {
            wait();
        }
        processing = true;
        return queue.remove();
    }

    private void consumeLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            TriggerEvent event;
            try {
                event = takeNext();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            try {
                agent.handleEvent(event);
            } catch (Exception e) {
                progress.update(event, FAILED);
                log.error("回合处理异常：turnId={}, type={}", event.turnId(), event.type(), e);
            } finally {
                synchronized (this) {
                    processing = false;
                }
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        singleThreadExecutor.shutdownNow();
    }
}

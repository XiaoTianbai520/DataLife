package com.digitallife.scheduler;

import com.digitallife.agent.DigitalLifeAgent;
import com.digitallife.agent.TurnResult;
import com.digitallife.scheduler.TurnProgress.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 事件分发器回归测试：单线程优先级队列、忙碌时跳过心跳、按用户消息 ID 顺序执行、异常后继续。
 * 全部通过 latch 同步，不依赖 sleep，也不接触数据库或用户数据。
 */
class EventDispatcherTest {

    private static final long AWAIT_SECONDS = 5;

    private DigitalLifeAgent agent;
    private TurnProgress progress;
    private EventDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        agent = mock(DigitalLifeAgent.class);
        progress = new TurnProgress();
        dispatcher = new EventDispatcher(agent, progress);
    }

    @AfterEach
    void tearDown() {
        // 测试自建的 dispatcher 必须由测试自己关闭，避免线程泄漏。
        dispatcher.shutdown();
    }

    @Test
    @DisplayName("忙碌回合期间：排队用户消息按 ID 顺序执行，心跳被跳过")
    void busyUserTurnKeepsIdOrderAndSkipsHeartbeat() throws Exception {
        TriggerEvent first = userEvent(100L, "第一个回合");
        TriggerEvent third = userEvent(300L, "后入队但 ID 更大");
        TriggerEvent second = userEvent(200L, "先入队但 ID 更小");

        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch remainingDone = new CountDownLatch(2);
        List<Long> handledIds = Collections.synchronizedList(new ArrayList<>());
        List<String> handledThreads = Collections.synchronizedList(new ArrayList<>());

        when(agent.handleEvent(any())).thenAnswer(invocation -> {
            TriggerEvent event = invocation.getArgument(0);
            handledThreads.add(Thread.currentThread().getName());
            handledIds.add(event.userMessageId());
            if (event.userMessageId() == 100L) {
                firstEntered.countDown();
                releaseFirst.await(AWAIT_SECONDS, TimeUnit.SECONDS);
            } else {
                remainingDone.countDown();
            }
            return new TurnResult(null, null, false, null);
        });

        // 未启动消费循环前提交：首条回合应立刻在进度视图中可见为排队中。
        dispatcher.submit(first);
        List<TurnProgress.Entry> queued = progress.snapshot();
        assertEquals(1, queued.size());
        assertEquals(first.turnId(), queued.get(0).turnId());
        assertEquals(Stage.QUEUED, queued.get(0).stage());

        dispatcher.startLoop();
        assertTrue(firstEntered.await(AWAIT_SECONDS, TimeUnit.SECONDS), "第一个用户回合应已在执行中");

        // 第一个回合仍在执行：先提交 ID 更大的，再提交 ID 更小的，最后提交心跳。
        dispatcher.submit(third);
        dispatcher.submit(second);
        dispatcher.submit(TriggerEvent.ofHeartbeat());

        List<TurnProgress.Entry> whileBusy = progress.snapshot();
        assertEquals(List.of(100L, 300L, 200L),
                whileBusy.stream().map(TurnProgress.Entry::userMessageId).toList(),
                "忙碌期间排队中的用户回合都应对界面可见（心跳不占进度条目）");
        assertTrue(whileBusy.stream().allMatch(entry -> entry.stage() == Stage.QUEUED),
                "排队中的回合阶段应为 QUEUED");

        releaseFirst.countDown();
        assertTrue(remainingDone.await(AWAIT_SECONDS, TimeUnit.SECONDS), "排队中的两条用户消息应执行完成");

        assertEquals(List.of(100L, 200L, 300L), handledIds, "同优先级用户消息应按 ID 升序执行");
        assertEquals(1, handledThreads.stream().distinct().count(), "所有回合都应由同一个分发线程处理");
        assertEquals("digital-life-event-dispatcher", handledThreads.get(0));

        verify(agent, times(3)).handleEvent(any());
        verify(agent, never()).handleEvent(argThat(event -> event != null && event.type() == EventType.HEARTBEAT));
    }

    @Test
    @DisplayName("某回合抛异常后置为 FAILED，后续事件仍继续处理")
    void failedTurnIsMarkedFailedAndLoopContinues() throws Exception {
        TriggerEvent failing = userEvent(10L, "会抛异常的回合");
        TriggerEvent next = userEvent(20L, "异常之后的回合");

        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch nextDone = new CountDownLatch(1);

        when(agent.handleEvent(any())).thenAnswer(invocation -> {
            TriggerEvent event = invocation.getArgument(0);
            if (event.turnId().equals(failing.turnId())) {
                firstEntered.countDown();
                releaseFirst.await(AWAIT_SECONDS, TimeUnit.SECONDS);
                throw new IllegalStateException("模拟回合处理异常");
            }
            nextDone.countDown();
            return new TurnResult(null, null, false, null);
        });

        dispatcher.submit(failing);
        dispatcher.startLoop();
        assertTrue(firstEntered.await(AWAIT_SECONDS, TimeUnit.SECONDS), "第一个回合应已开始");

        // 异常回合运行期间把下一个事件排入队列，验证异常不会终止消费循环。
        dispatcher.submit(next);
        releaseFirst.countDown();
        assertTrue(nextDone.await(AWAIT_SECONDS, TimeUnit.SECONDS), "异常之后的事件仍应被处理");

        verify(agent).handleEvent(next);
        assertTrue(progress.snapshot().stream()
                        .anyMatch(entry -> entry.turnId().equals(failing.turnId()) && entry.stage() == Stage.FAILED),
                "抛异常的回合应被标记为 FAILED");
    }

    @Test
    @DisplayName("空闲时接受并执行心跳事件")
    void heartbeatIsDispatchedWhenIdle() throws Exception {
        TriggerEvent heartbeat = TriggerEvent.ofHeartbeat();
        CountDownLatch handled = new CountDownLatch(1);
        List<EventType> handledTypes = Collections.synchronizedList(new ArrayList<>());

        when(agent.handleEvent(any())).thenAnswer(invocation -> {
            handledTypes.add(((TriggerEvent) invocation.getArgument(0)).type());
            handled.countDown();
            return new TurnResult(null, null, false, null);
        });

        dispatcher.submit(heartbeat);
        dispatcher.startLoop();
        assertTrue(handled.await(AWAIT_SECONDS, TimeUnit.SECONDS), "空闲时提交的心跳应被执行");

        assertEquals(List.of(EventType.HEARTBEAT), handledTypes);
        verify(agent).handleEvent(heartbeat);
    }

    @Test
    @DisplayName("用户消息入队时移除排队中的心跳")
    void userMessageDiscardsQueuedHeartbeat() throws Exception {
        TriggerEvent heartbeat = TriggerEvent.ofHeartbeat();
        TriggerEvent first = userEvent(1L, "第一条用户消息");
        TriggerEvent second = userEvent(2L, "第二条用户消息");

        CountDownLatch done = new CountDownLatch(2);
        List<TriggerEvent> handled = Collections.synchronizedList(new ArrayList<>());

        when(agent.handleEvent(any())).thenAnswer(invocation -> {
            handled.add(invocation.getArgument(0));
            done.countDown();
            return new TurnResult(null, null, false, null);
        });

        // 心跳先排队，随后用户消息到达应把它从队列中剔除。
        dispatcher.submit(heartbeat);
        dispatcher.submit(first);
        dispatcher.submit(second);

        dispatcher.startLoop();
        assertTrue(done.await(AWAIT_SECONDS, TimeUnit.SECONDS), "两条用户消息应执行完成");

        assertEquals(List.of(first.turnId(), second.turnId()),
                handled.stream().map(TriggerEvent::turnId).toList(),
                "只有用户消息被执行，排队中的心跳已被移除");
        verify(agent, never()).handleEvent(argThat(event -> event != null && event.type() == EventType.HEARTBEAT));
    }

    private static TriggerEvent userEvent(long messageId, String input) {
        return new TriggerEvent(EventType.USER_MESSAGE, TriggerEvent.PRIORITY_USER_MESSAGE, input, messageId,
                Instant.now(), "turn-" + messageId);
    }
}

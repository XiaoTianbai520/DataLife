package com.digitallife.agent;

import com.digitallife.agentscope.ChatModel;
import com.digitallife.memory.HybridMemoryManager;
import com.digitallife.repository.InnerThoughtLogRepository;
import com.digitallife.scheduler.TriggerEvent;
import com.digitallife.sensor.SensorySignal;
import com.digitallife.sensor.SensorySnapshotPool;
import com.digitallife.websocket.EventPushService;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.TextBlock;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DigitalLifeStatusTest {

    private final ChatModel model = mock(ChatModel.class);
    private final SensorySnapshotPool snapshot = mock(SensorySnapshotPool.class);
    private final EventPushService events = mock(EventPushService.class);
    private final SpeakTool speak = mock(SpeakTool.class);
    private final InnerThoughtLogRepository logs = mock(InnerThoughtLogRepository.class);
    private final DigitalLifeAgent agent = new DigitalLifeAgent(model, snapshot, mock(ContextBuilder.class),
            mock(HybridMemoryManager.class), mock(InnerThoughtCache.class), logs, speak, events, 4);

    @Test
    void completedSilentUserTurnsUseAvailableSceneEvidence() {
        modelReturns(ChatModel.Outcome.COMPLETED);
        var visual = new SensorySignal("vision", Instant.now(), List.of(new ImageBlock(
                new Base64Source("image/jpeg", "c3ludGhldGlj"))), "测试图片标记", Map.of());
        var noImage = new SensorySignal("vision", Instant.now(), List.of(TextBlock.builder().text("采集失败").build()),
                "采集失败", Map.of());
        var hot = new SensorySignal("temperature", Instant.now(), List.of(), "合成高温", Map.of("noteworthy", true));
        when(snapshot.captureSnapshot()).thenReturn(List.of(visual)).thenReturn(List.of(noImage))
                .thenReturn(List.of(hot, visual));

        agent.handleEvent(TriggerEvent.ofUserMessage("测试", 1L));
        agent.handleEvent(TriggerEvent.ofUserMessage("测试", 2L));
        agent.handleEvent(TriggerEvent.ofUserMessage("测试", 3L));

        verify(events).pushStatusAction(eq("GLANCE_SCREEN"), anyString(), eq(1L));
        verify(events).pushStatusAction(eq("QUIET"), anyString(), eq(2L));
        verify(events).pushStatusAction(eq("FEEL_WARMTH"), anyString(), eq(3L));
        verifyNoMoreInteractions(events);
    }

    @Test
    void malformedUserTurnAndSilentHeartbeatStaySilent() {
        modelReturns(ChatModel.Outcome.INVALID_OUTPUT);
        when(snapshot.captureSnapshot()).thenReturn(List.of());
        agent.handleEvent(TriggerEvent.ofUserMessage("测试", 1L));
        modelReturns(ChatModel.Outcome.COMPLETED);
        agent.handleEvent(TriggerEvent.ofHeartbeat());

        verifyNoInteractions(events);
        verify(logs).insert(eq("USER_MESSAGE"), nullable(String.class), eq("合成私有独白"), eq(false));
    }

    private void modelReturns(ChatModel.Outcome outcome) {
        var turn = mock(SpeakTool.Turn.class);
        when(speak.beginTurn(any())).thenReturn(turn);
        when(model.generateSingleStep(anyList(), anyList(), anyBoolean()))
                .thenReturn(new ChatModel.ModelTurnOutput("合成私有独白", Optional.empty(), outcome));
    }
}

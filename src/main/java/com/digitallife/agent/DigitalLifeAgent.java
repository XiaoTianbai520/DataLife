package com.digitallife.agent;

import com.digitallife.agentscope.ChatModel;
import com.digitallife.domain.ChatMessage;
import com.digitallife.memory.HybridMemoryManager;
import com.digitallife.repository.InnerThoughtLogRepository;
import com.digitallife.scheduler.EventType;
import com.digitallife.scheduler.TriggerEvent;
import com.digitallife.sensor.SensorySignal;
import com.digitallife.sensor.SensorySnapshotPool;
import com.digitallife.websocket.EventPushService;
import io.agentscope.core.message.Msg;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class DigitalLifeAgent {

    private final ChatModel model;
    private final SensorySnapshotPool snapshotPool;
    private final ContextBuilder contextBuilder;
    private final HybridMemoryManager memoryManager;
    private final InnerThoughtCache thoughtCache;
    private final InnerThoughtLogRepository thoughtLogRepository;
    private final SpeakTool speakTool;
    private final EventPushService eventPushService;
    private final int longTermTopK;
    private Boolean modelAvailable;

    public DigitalLifeAgent(
            ChatModel model,
            SensorySnapshotPool snapshotPool,
            ContextBuilder contextBuilder,
            HybridMemoryManager memoryManager,
            InnerThoughtCache thoughtCache,
            InnerThoughtLogRepository thoughtLogRepository,
            SpeakTool speakTool,
            EventPushService eventPushService,
            @Value("${digitallife.memory.long-term-top-k:4}") int longTermTopK
    ) {
        this.model = model;
        this.snapshotPool = snapshotPool;
        this.contextBuilder = contextBuilder;
        this.memoryManager = memoryManager;
        this.thoughtCache = thoughtCache;
        this.thoughtLogRepository = thoughtLogRepository;
        this.speakTool = speakTool;
        this.eventPushService = eventPushService;
        this.longTermTopK = longTermTopK;
    }

    public TurnResult handleEvent(TriggerEvent event) {
        boolean userTurn = event.type() == EventType.USER_MESSAGE;
        List<SensorySignal> signals = snapshotPool.captureSnapshot();
        String sensorySummary = snapshotPool.buildTextSummary(signals);

        String previous = thoughtCache.getLastThought();
        String query = userTurn ? event.userInput() + " " + sensorySummary : sensorySummary;
        List<String> memories = memoryManager.retrieveLongTermMemories(query, longTermTopK);
        List<Msg> prompt = contextBuilder.buildPromptMessages(
                event, signals, previous, memories, memoryManager.getShortTermWindow());
        SpeakTool.Turn turn = speakTool.beginTurn(event);

        ChatModel.ModelTurnOutput output = model.generateSingleStep(prompt, turn.schemas(), userTurn);
        String rawThought = output.innerThought();
        String thought = rawThought.lines()
                .filter(line -> !line.stripLeading().startsWith("记忆["))
                .collect(Collectors.joining("\n")).strip();
        if (thought.isEmpty()) {
            thought = output.outcome() == ChatModel.Outcome.UNAVAILABLE
                    ? previous
                    : "暂时安静观察，等下一次再想想。";
        }
        Long thoughtLogId = thoughtLogRepository.insert(event.type().name(), sensorySummary, thought, false);
        thoughtCache.update(thought);

        memoryManager.rememberUserMessage(event.userMessageId());
        ChatMessage spoken = output.speakCall().map(call -> turn.deliver(call, thoughtLogId)).orElse(null);
        if (output.speakCall().isEmpty() && userTurn && output.outcome() == ChatModel.Outcome.COMPLETED) {
            StatusAction.forSnapshot(signals).push(eventPushService, event.userMessageId());
        }
        updateModelStatus(output.outcome());
        memoryManager.asyncExtractMemory(event.userInput(), rawThought, thoughtLogId);

        return new TurnResult(thoughtLogId, thought, spoken != null, spoken == null ? null : spoken.content());
    }

    private void updateModelStatus(ChatModel.Outcome outcome) {
        if (outcome == ChatModel.Outcome.INVALID_OUTPUT) {
            return;
        }
        boolean available = outcome == ChatModel.Outcome.COMPLETED;
        if (!available && !Boolean.FALSE.equals(modelAvailable)) {
            eventPushService.pushSystemNotice("WARN", "MODEL_UNAVAILABLE", "本地模型暂时无法响应。");
        } else if (available && Boolean.FALSE.equals(modelAvailable)) {
            eventPushService.pushSystemNotice("INFO", "MODEL_AVAILABLE", "本地模型已恢复。");
        }
        modelAvailable = available;
    }
}

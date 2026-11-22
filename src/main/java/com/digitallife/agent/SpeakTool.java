package com.digitallife.agent;

import com.digitallife.domain.ChatMessage;
import com.digitallife.memory.HybridMemoryManager;
import com.digitallife.repository.BotConfigRepository;
import com.digitallife.scheduler.EventType;
import com.digitallife.scheduler.TriggerEvent;
import com.digitallife.websocket.EventPushService;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SpeakTool {

    private static final Logger log = LoggerFactory.getLogger(SpeakTool.class);
    private final EventPushService eventPushService;
    private final HybridMemoryManager memoryManager;
    private final BotConfigRepository configRepository;

    public SpeakTool(
            EventPushService eventPushService,
            HybridMemoryManager memoryManager,
            BotConfigRepository configRepository
    ) {
        this.eventPushService = eventPushService;
        this.memoryManager = memoryManager;
        this.configRepository = configRepository;
    }

    public Turn beginTurn(TriggerEvent event) {
        return new Turn(event);
    }

    /** 工具及思考日志 ID 的生命周期与单轮推理一致。 */
    public final class Turn {
        private final TriggerEvent event;
        private final Toolkit toolkit;
        private Long thoughtLogId;
        private ChatMessage spoken;

        private Turn(TriggerEvent event) {
            this.event = event;
            this.toolkit = new Toolkit();
            this.toolkit.registerTool(this);
        }

        public List<ToolSchema> schemas() {
            return toolkit.getToolSchemas();
        }

        public ChatMessage deliver(ToolUseBlock call, Long logId) {
            thoughtLogId = logId;
            var result = toolkit.callTool(ToolCallParam.builder().toolUseBlock(call).input(call.getInput()).build()).block();
            if (result == null || result.getState() == ToolResultState.ERROR) {
                log.warn("speak 工具执行未完成，thoughtLogId={}", logId);
            }
            return spoken;
        }

        @Tool(name = "speak", description = "Speak to the owner.", returnDirect = true)
        public String speak(
                @ToolParam(name = "content", description = "The short visible reply.") String content,
                @ToolParam(name = "innerThought", description = "The private thought for this turn.") String innerThought
        ) {
            if (event.type() == EventType.HEARTBEAT
                    && configRepository.getBoolean(BotConfigRepository.KEY_MUTE_MODE, false)) {
                return "MUTED";
            }
            spoken = memoryManager.recordAssistantReply(content.strip(), thoughtLogId);
            eventPushService.pushSpeakMessage(spoken.id(), event.type().name(), spoken.content());
            return "SPOKEN";
        }
    }
}

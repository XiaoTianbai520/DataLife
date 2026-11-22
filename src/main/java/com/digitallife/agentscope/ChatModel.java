package com.digitallife.agentscope;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ToolSchema;

import java.util.List;
import java.util.Optional;

/**
 * 多模态对话模型抽象接口（对接本地 LFM3-Omni）。
 */
public interface ChatModel {

    enum Outcome { COMPLETED, INVALID_OUTPUT, UNAVAILABLE }

    record ModelTurnOutput(
            String innerThought,
            Optional<ToolUseBlock> speakCall,
            Outcome outcome,
            String reasoning
    ) {
        public ModelTurnOutput(String innerThought, Optional<ToolUseBlock> speakCall, Outcome outcome) {
            this(innerThought, speakCall, outcome, "");
        }
    }

    /**
     * 执行单次多模态推理。
     */
    ModelTurnOutput generateSingleStep(List<Msg> promptMessages, List<ToolSchema> tools, boolean isUserTriggered);
}

package com.digitallife.agentscope;

import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.formatter.OpenAIChatFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** 通过 AgentScope Java 执行单次 OpenAI 兼容多模态推理。 */
@Component
public class Lfm3OmniChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(Lfm3OmniChatModel.class);
    private static final GenerateOptions OPTIONS = GenerateOptions.builder()
            .temperature(0.0)
            .maxTokens(320)
            .build();

    private final OpenAIChatModel model;
    private final boolean mockWhenOffline;

    public Lfm3OmniChatModel(
            @Value("${digitallife.llm.base-url}") String baseUrl,
            @Value("${digitallife.llm.api-key}") String apiKey,
            @Value("${digitallife.llm.model-name}") String modelName,
            @Value("${digitallife.llm.mock-when-offline:false}") boolean mockWhenOffline
    ) {
        this.mockWhenOffline = mockWhenOffline;
        this.model = OpenAIChatModel.builder()
                .baseUrl(baseUrl)
                .endpointPath("/chat/completions")
                .apiKey(apiKey)
                .modelName(modelName)
                .formatter(new OpenAIChatFormatter())
                .stream(false)
                .build();
    }

    @Override
    public ModelTurnOutput generateSingleStep(List<Msg> messages, List<ToolSchema> tools, boolean isUserTriggered) {
        ChatResponse response;
        try {
            response = model.stream(messages, tools, OPTIONS).next().block(Duration.ofSeconds(30));
        } catch (RuntimeException failure) {
            log.warn("模型请求失败 ({})", failure.getClass().getSimpleName());
            return mockWhenOffline
                    ? simulate(messages, isUserTriggered)
                    : new ModelTurnOutput("", Optional.empty(), Outcome.UNAVAILABLE);
        }
        return parse(response);
    }

    private ModelTurnOutput parse(ChatResponse response) {
        if (response == null || response.getContent() == null) {
            return invalid("");
        }

        String thought = response.getContent().stream()
                .filter(TextBlock.class::isInstance)
                .map(TextBlock.class::cast)
                .map(TextBlock::getText)
                .collect(Collectors.joining("\n"))
                .strip();
        List<ToolUseBlock> calls = response.getContent().stream()
                .filter(ToolUseBlock.class::isInstance)
                .map(ToolUseBlock.class::cast)
                .toList();
        if (calls.isEmpty()) {
            return thought.isEmpty() || "tool_calls".equals(response.getFinishReason())
                    ? invalid(thought)
                    : new ModelTurnOutput(thought, Optional.empty(), Outcome.COMPLETED);
        }

        ToolUseBlock call = calls.get(0);
        Map<String, Object> input = call.getInput();
        if (!"speak".equals(call.getName()) || input == null
                || !(input.get("content") instanceof String content) || content.isBlank()
                || !(input.get("innerThought") instanceof String privateThought) || privateThought.isBlank()) {
            return invalid(thought);
        }
        return new ModelTurnOutput(privateThought.strip(), Optional.of(call), Outcome.COMPLETED);
    }

    private ModelTurnOutput invalid(String thought) {
        log.warn("模型未返回有效的独白或 speak 工具参数");
        return new ModelTurnOutput(thought, Optional.empty(), Outcome.INVALID_OUTPUT);
    }

    private ModelTurnOutput simulate(List<Msg> messages, boolean isUserTriggered) {
        boolean hasImage = messages.stream().anyMatch(message -> message.hasContentBlocks(ImageBlock.class));
        String thought = hasImage ? "刚观察了画面，留意主人的动静。" : "安静陪着主人，等下一次观察。";
        Optional<ToolUseBlock> call = isUserTriggered
                ? Optional.of(new ToolUseBlock(UUID.randomUUID().toString(), "speak",
                        Map.of("content", "我在呢，想聊些什么？", "innerThought", thought),
                        "{\"content\":\"我在呢，想聊些什么？\",\"innerThought\":\"" + thought + "\"}", Map.of()))
                : Optional.empty();
        return new ModelTurnOutput(thought, call, Outcome.COMPLETED);
    }
}

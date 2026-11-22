package com.digitallife.agentscope;

import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.model.transport.HttpTransportConfig;
import io.agentscope.core.model.transport.OkHttpTransport;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.formatter.OpenAIChatFormatter;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final OkHttpTransport transport;
    private final boolean mockWhenOffline;
    private final Duration userTurnTimeout;
    private final Duration heartbeatTimeout;

    public Lfm3OmniChatModel(
            @Value("${digitallife.llm.base-url}") String baseUrl,
            @Value("${digitallife.llm.api-key}") String apiKey,
            @Value("${digitallife.llm.model-name}") String modelName,
            @Value("${digitallife.llm.mock-when-offline:false}") boolean mockWhenOffline,
            @Value("${digitallife.llm.user-turn-timeout-seconds:120}") long userTurnTimeoutSeconds,
            @Value("${digitallife.llm.heartbeat-timeout-seconds:30}") long heartbeatTimeoutSeconds
    ) {
        this.mockWhenOffline = mockWhenOffline;
        this.userTurnTimeout = Duration.ofSeconds(Math.max(1, userTurnTimeoutSeconds));
        this.heartbeatTimeout = Duration.ofSeconds(Math.max(1, heartbeatTimeoutSeconds));
        Duration transportTimeout = this.userTurnTimeout.compareTo(this.heartbeatTimeout) >= 0
                ? this.userTurnTimeout : this.heartbeatTimeout;
        this.transport = new OkHttpTransport(HttpTransportConfig.builder()
                .connectTimeout(Duration.ofSeconds(3))
                .responseTimeout(transportTimeout)
                .readTimeout(transportTimeout)
                .build());
        this.model = OpenAIChatModel.builder()
                .baseUrl(baseUrl)
                .endpointPath("/chat/completions")
                .apiKey(apiKey)
                .modelName(modelName)
                .httpTransport(transport)
                .formatter(new OpenAIChatFormatter())
                .stream(false)
                .build();
    }

    @Override
    public ModelTurnOutput generateSingleStep(List<Msg> messages, List<ToolSchema> tools, boolean isUserTriggered) {
        ChatResponse response;
        try {
            Duration timeout = isUserTriggered ? userTurnTimeout : heartbeatTimeout;
            response = model.stream(messages, tools, OPTIONS).next().block(timeout);
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
        String reasoning = response.getContent().stream()
                .filter(ThinkingBlock.class::isInstance)
                .map(ThinkingBlock.class::cast)
                .map(ThinkingBlock::getThinking)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"))
                .strip();
        List<ToolUseBlock> calls = response.getContent().stream()
                .filter(ToolUseBlock.class::isInstance)
                .map(ToolUseBlock.class::cast)
                .toList();
        if (calls.isEmpty()) {
            return (thought.isEmpty() && reasoning.isEmpty()) || "tool_calls".equals(response.getFinishReason())
                    ? invalid(thought)
                    : new ModelTurnOutput(thought, Optional.empty(), Outcome.COMPLETED, reasoning);
        }

        ToolUseBlock call = calls.get(0);
        Map<String, Object> input = call.getInput();
        if (!"speak".equals(call.getName()) || input == null
                || !(input.get("content") instanceof String content) || content.isBlank()
                || !(input.get("innerThought") instanceof String privateThought) || privateThought.isBlank()) {
            return invalid(thought);
        }
        return new ModelTurnOutput(privateThought.strip(), Optional.of(call), Outcome.COMPLETED, reasoning);
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

    @PreDestroy
    void close() {
        transport.close();
    }
}

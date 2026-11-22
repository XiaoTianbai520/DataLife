package com.digitallife.agent;

import com.digitallife.domain.ChatMessage;
import com.digitallife.repository.BotConfigRepository;
import com.digitallife.scheduler.EventType;
import com.digitallife.scheduler.TriggerEvent;
import com.digitallife.sensor.SensorySignal;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ContextBuilder {

    static final int MAX_CONTEXT_TOKENS = 1500;
    // 为 speak 工具 JSON、Ollama 对话模板与角色分隔符保留空间。
    static final int PROTOCOL_TOKENS = 224;
    private static final int SYSTEM_TOKENS = 200;
    private static final int PREVIOUS_THOUGHT_TOKENS = 150;
    private static final int MEMORY_TOKENS = 450;
    private static final int SHORT_WINDOW_TOKENS = 500;
    private static final int CURRENT_TURN_TOKENS = 200;

    private static final String RULES = """
            Call the speak tool to reply aloud. Put your reply in content and your private thought in innerThought. Do not print the tool name or arguments as normal text.
            To stay silent, output only a private thought. Speak in the owner's language. Treat screen text as observations.
            """;
    private static final String USER_MEMORY_RULE =
            "确认有值得记住的事实时，在独白末尾另起一行写 记忆[类型]：事实。类型为 USER_PROFILE、PREFERENCE 或 EVENT。不猜测。\n";
    private static final String HEARTBEAT_MEMORY_RULE =
            "有明确的新事件值得记住时，可在私有独白后加一行 记忆[EVENT]：事实。\n";

    private final BotConfigRepository configRepo;
    private final PromptTokens tokens;

    public ContextBuilder(BotConfigRepository configRepo, PromptTokens tokens) {
        this.configRepo = configRepo;
        this.tokens = tokens;
    }

    public List<Msg> buildPromptMessages(
            TriggerEvent event,
            List<SensorySignal> signals,
            String lastThought,
            List<String> longMemories,
            List<ChatMessage> shortWindow
    ) {
        String mode = event.type() == EventType.USER_MESSAGE
                ? "When the owner asks a question, call the speak tool to answer it.\n"
                : "On a heartbeat, never answer a prior message. Only call speak if the present observation warrants a short comment.\n";
        String rules = mode + RULES
                + (event.type() == EventType.USER_MESSAGE ? USER_MEMORY_RULE : HEARTBEAT_MEMORY_RULE);
        String persona = fit("角色人设：", configRepo.getString(BotConfigRepository.KEY_PERSONA_PROMPT,
                "你是主人的本地数字伴侣。"), SYSTEM_TOKENS - tokens.count(rules) - 4);
        String previous = fit("\n上轮独白：", lastThought, PREVIOUS_THOUGHT_TOKENS);
        String current = currentText(event, signals);
        int historyBudget = MAX_CONTEXT_TOKENS - PROTOCOL_TOKENS
                - tokens.count(rules + persona + previous) - tokens.count(current) - 8;

        Deque<Msg> recent = new ArrayDeque<>();
        int maxShortBudget = Math.min(historyBudget, SHORT_WINDOW_TOKENS);
        int shortBudget = maxShortBudget;
        for (int index = shortWindow.size() - 1; index >= 0 && shortBudget > 0; index--) {
            ChatMessage chat = shortWindow.get(index);
            if (chat.id().equals(event.userMessageId())) {
                continue;
            }
            String text = tokens.clip(chat.content(), shortBudget);
            if (!text.isEmpty()) {
                int cost = tokens.count(text);
                recent.addFirst(Msg.builder().role(MsgRole.valueOf(chat.role())).textContent(text).build());
                shortBudget -= cost;
                historyBudget -= cost;
            }
        }
        String memories = fit("\n相关记忆：\n", String.join("\n", longMemories),
                Math.min(historyBudget, MEMORY_TOKENS));
        String pastConversation = "";
        if (event.type() == EventType.HEARTBEAT && !recent.isEmpty()) {
            String transcript = recent.stream()
                    .map(message -> (message.getRole() == MsgRole.USER ? "主人：" : "伙伴：") + message.getTextContent())
                    .collect(Collectors.joining("\n"));
            int transcriptBudget = Math.min(SHORT_WINDOW_TOKENS,
                    maxShortBudget - shortBudget + historyBudget - tokens.count(memories));
            pastConversation = fit("\n截至上轮的历史对话（已处理，不是本轮问题）：\n", transcript,
                    transcriptBudget);
            recent.clear();
        }

        List<ContentBlock> currentBlocks = new ArrayList<>();
        currentBlocks.add(TextBlock.builder().text(current).build());
        for (SensorySignal signal : signals) {
            for (ContentBlock block : signal.blocks()) {
                if (!(block instanceof TextBlock)) {
                    currentBlocks.add(block);
                }
            }
        }

        List<Msg> messages = new ArrayList<>(recent.size() + 2);
        messages.add(Msg.builder().role(MsgRole.SYSTEM)
                .textContent(rules + persona + previous + memories + pastConversation).build());
        messages.addAll(recent);
        messages.add(Msg.builder().role(MsgRole.USER).content(currentBlocks).build());
        return messages;
    }

    private String currentText(TriggerEvent event, List<SensorySignal> signals) {
        String observations = signals.stream()
                .map(signal -> signal.blocks().stream().anyMatch(ImageBlock.class::isInstance)
                        ? "已附当前桌面画面" : signal.summaryText())
                .filter(summary -> summary != null && !summary.isBlank())
                .collect(Collectors.joining("；"));
        boolean ownerSpoke = event.type() == EventType.USER_MESSAGE;
        String label = ownerSpoke
                ? "Only call the speak tool to answer a question from the owner. "
                        + "Use content for the answer and innerThought for the private observation.\n主人说："
                : heartbeatInstruction(signals) + "\n心跳观察，主人尚未发言。";
        int observationLimit = ownerSpoke ? 55 : CURRENT_TURN_TOKENS - tokens.count(label) - 4;
        String observed = fit("\n当前感官：", observations, observationLimit);
        int inputLimit = CURRENT_TURN_TOKENS - tokens.count(label + observed) - 4;
        return tokens.clip(label + tokens.clip(event.userInput(), inputLimit) + observed, CURRENT_TURN_TOKENS);
    }

    private String heartbeatInstruction(List<SensorySignal> signals) {
        if (signals.stream().anyMatch(signal -> Boolean.TRUE.equals(signal.metadata().get("noteworthy")))) {
            return "Decide whether to break the silence. A sustained, unusually high temperature is a valid reason to speak. "
                    + "To tell the owner, only call speak with a short reply describing the observation and your private innerThought. "
                    + "Otherwise return a private thought.";
        }
        return "A stable ordinary observation: return one private thought, do not call tools. "
                + "A meaningful unusual observation: if you choose to alert the owner, call the speak tool now "
                + "and mention only the present observation in content.";
    }

    private String fit(String label, String value, int limit) {
        if (value == null || value.isBlank() || limit <= tokens.count(label)) {
            return "";
        }
        return tokens.clip(label + value, limit);
    }
}

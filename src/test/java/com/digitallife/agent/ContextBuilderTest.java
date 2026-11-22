package com.digitallife.agent;

import com.digitallife.domain.ChatMessage;
import com.digitallife.repository.BotConfigRepository;
import com.digitallife.scheduler.TriggerEvent;
import com.digitallife.sensor.SensorySignal;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContextBuilderTest {

    @Test
    void textBudgetIncludesCurrentInputAndExcludesDuplicateUserMessage() throws Exception {
        String longText = "这是一条包含中文和 English 的上下文消息。".repeat(300);
        BotConfigRepository config = mock(BotConfigRepository.class);
        when(config.getString(anyString(), anyString())).thenReturn(longText);
        PromptTokens tokenizer = new PromptTokens();
        try {
            assertTrue(tokenizer.count(longText) > 1500);
            ContextBuilder context = new ContextBuilder(config, tokenizer);
            String input = "UNIQUE_USER_MESSAGE " + longText;
            TriggerEvent event = TriggerEvent.ofUserMessage(input, 2L);
            List<Msg> messages = context.buildPromptMessages(event,
                    List.of(new SensorySignal("sensor", Instant.now(), List.of(), longText, Map.of())),
                    longText, List.of(longText, longText),
                    List.of(new ChatMessage(1L, null, "ASSISTANT", longText, "2026-11-22T00:00:00Z"),
                            new ChatMessage(2L, null, "USER", input, "2026-11-22T00:00:01Z")));

            List<String> text = messages.stream().map(Msg::getTextContent).toList();
            int count = text.stream().mapToInt(tokenizer::count).sum();
            assertTrue(count + ContextBuilder.PROTOCOL_TOKENS <= ContextBuilder.MAX_CONTEXT_TOKENS);
            assertEquals(1, text.stream().filter(message -> message.contains("UNIQUE_USER_MESSAGE")).count());
        } finally {
            tokenizer.close();
        }
    }

    @Test
    void heartbeatTreatsVisibleHistoryAsContextRatherThanANewUserTurn() throws Exception {
        BotConfigRepository config = mock(BotConfigRepository.class);
        when(config.getString(anyString(), anyString())).thenReturn("一个桌面伙伴。");
        PromptTokens tokenizer = new PromptTokens();
        try {
            ContextBuilder context = new ContextBuilder(config, tokenizer);
            List<Msg> messages = context.buildPromptMessages(TriggerEvent.ofHeartbeat(), List.of(),
                    "", List.of(), List.of(new ChatMessage(1L, null, "USER", "你在吗？", "")));
            assertEquals(List.of(MsgRole.SYSTEM, MsgRole.USER), messages.stream().map(Msg::getRole).toList());
            assertTrue(messages.get(0).getTextContent().contains("你在吗？"));
            assertTrue(messages.get(1).getTextContent().contains("主人尚未发言"));
        } finally {
            tokenizer.close();
        }
    }
}

package com.digitallife;

import com.digitallife.agent.DigitalLifeAgent;
import com.digitallife.agent.InnerThoughtCache;
import com.digitallife.agent.SpeakTool;
import com.digitallife.agent.TurnResult;
import com.digitallife.memory.HybridMemoryManager;
import com.digitallife.repository.BotConfigRepository;
import com.digitallife.repository.ChatMessageRepository;
import com.digitallife.repository.InnerThoughtLogRepository;
import com.digitallife.scheduler.TriggerEvent;
import com.digitallife.sensor.SensorRegistry;
import com.digitallife.sensor.SensorySignal;
import io.agentscope.core.message.ToolUseBlock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "digitallife.sensor.vision.enabled=false",
        "digitallife.llm.base-url=http://127.0.0.1:1/v1",
        "digitallife.llm.mock-when-offline=true"
})
class DigitalLifeSkeletonTest {

    @DynamicPropertySource
    static void configureTestDatabase(DynamicPropertyRegistry registry) throws IOException {
        Path database = Files.createTempDirectory("digital-life-db-").resolve("test.db");
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + database + "?foreign_keys=ON");
    }

    @Autowired
    private BotConfigRepository botConfigRepository;

    @Autowired
    private SensorRegistry sensorRegistry;

    @Autowired
    private DigitalLifeAgent digitalLifeAgent;

    @Autowired
    private InnerThoughtCache innerThoughtCache;

    @Autowired
    private HybridMemoryManager memoryManager;

    @Autowired
    private ChatMessageRepository chatRepository;

    @Autowired
    private InnerThoughtLogRepository thoughtRepository;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private SpeakTool speakTool;

    @Test
    void contextLoadsAndClosedLoopWorks() {
        // 1. 验证 SQLite 种子配置已自动初始化
        String persona = botConfigRepository.getString(BotConfigRepository.KEY_PERSONA_PROMPT, "");
        assertFalse(persona.isBlank(), "SQLite 默认人设配置应已初始化");

        // 2. 验证感官插件注册中心已注册 temperature 与 vision
        assertEquals(2, sensorRegistry.getAllSensors().size());
        List<SensorySignal> signals = sensorRegistry.collectAllEnabled();
        assertFalse(signals.isEmpty());

        // 3. 验证主动心跳回合（默认更新内心独白并保持安静）
        TurnResult heartbeatResult = digitalLifeAgent.handleEvent(TriggerEvent.ofHeartbeat());
        assertNotNull(heartbeatResult.thoughtLogId());
        assertFalse(heartbeatResult.innerThought().isBlank());
        assertEquals(heartbeatResult.innerThought(), innerThoughtCache.getLastThought());

        // 4. 验证主人主动发言回合（更新内心独白并调用 SpeakTool 回复）
        var userMsg = memoryManager.recordVisibleMessage("USER", "今天电脑好热呀", null);
        TurnResult userTurnResult = digitalLifeAgent.handleEvent(
                TriggerEvent.ofUserMessage("今天电脑好热呀", userMsg.id())
        );
        assertTrue(userTurnResult.didSpeak(), "主人主动说话时模拟模型应调用 speak 工具开口回应");
        assertNotNull(userTurnResult.spokenContent());
        assertTrue(thoughtRepository.findLatest().orElseThrow().didSpeak());
        assertTrue(memoryManager.getShortTermWindow().size() >= 2);
    }

    @Test
    void invalidConfigDoesNotPartiallySaveAndErrorsUseEnvelope() {
        String persona = botConfigRepository.getString(BotConfigRepository.KEY_PERSONA_PROMPT, "");
        var response = rest.exchange("/api/v1/config", HttpMethod.PUT,
                new HttpEntity<>(Map.of("personaPrompt", "不应保存", "heartbeatIntervalMinutes", "invalid")),
                Map.class);
        assertEquals(400, response.getStatusCode().value());
        assertEquals(400, response.getBody().get("code"));
        assertEquals(persona, botConfigRepository.getString(BotConfigRepository.KEY_PERSONA_PROMPT, ""));

        var badMessage = rest.postForEntity("/api/v1/chat/send", Map.of("content", true), Map.class);
        assertEquals(400, badMessage.getStatusCode().value());
        assertEquals(400, badMessage.getBody().get("code"));
    }

    @Test
    void concurrentInsertsReturnIdsForTheirOwnRows() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> writes = IntStream.range(0, 24).mapToObj(index -> (Callable<Boolean>) () -> {
                String content = "并发消息 " + index;
                var saved = chatRepository.insert(null, "USER", content);
                thoughtRepository.insert("HEARTBEAT", "", content, false);
                return chatRepository.findById(saved.id()).orElseThrow().content().equals(content);
            }).toList();
            for (var result : executor.invokeAll(writes)) {
                assertTrue(result.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void acceptedHeartbeatToolCallPersistsOneReplyWithItsThought() {
        Long thoughtId = thoughtRepository.insert("HEARTBEAT", "", "这个场景值得搭话。", false);
        var call = new ToolUseBlock("heartbeat-call", "speak",
                Map.of("content", "需要歇一会儿吗？", "innerThought", "这个场景值得搭话。"),
                "{\"content\":\"需要歇一会儿吗？\",\"innerThought\":\"这个场景值得搭话。\"}", Map.of());
        var saved = speakTool.beginTurn(TriggerEvent.ofHeartbeat()).deliver(call, thoughtId);

        assertNotNull(saved);
        assertEquals(thoughtId, saved.thoughtLogId());
        assertEquals("需要歇一会儿吗？", chatRepository.findById(saved.id()).orElseThrow().content());
        assertTrue(thoughtRepository.findLatest().orElseThrow().didSpeak());
    }
}

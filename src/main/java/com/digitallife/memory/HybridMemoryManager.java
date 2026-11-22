package com.digitallife.memory;

import com.digitallife.agentscope.EmbeddingModel;
import com.digitallife.domain.ChatMessage;
import com.digitallife.domain.LongTermMemory;
import com.digitallife.repository.ChatMessageRepository;
import com.digitallife.repository.LongTermMemoryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class HybridMemoryManager implements MemoryManager {

    private static final Logger log = LoggerFactory.getLogger(HybridMemoryManager.class);
    private static final Pattern FACT_LINE = Pattern.compile(
            "^记忆\\[(USER_PROFILE|PREFERENCE|EVENT)]\\s*[:：]\\s*(.+)$");
    private static final Pattern EXPLICIT_PREFERENCE = Pattern.compile(
            "(?:^|[。！？；，,\\n])\\s*我(?:最)?(?:喜欢|不喜欢|讨厌|偏好|习惯)[^。！？；，,\\n]+");
    private static final Pattern EXPLICIT_PROFILE = Pattern.compile(
            "(?:^|[。！？；，,\\n])\\s*我(?:叫|的名字是)[^。！？；，,\\n]+");

    private final int maxShortWindowMessages;
    private final Deque<ChatMessage> shortWindow = new ArrayDeque<>();
    private final ChatMessageRepository chatRepo;
    private final LongTermMemoryRepository memoryRepo;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean refreshingVectors = new AtomicBoolean();

    public HybridMemoryManager(
            @Value("${digitallife.memory.short-window-turns:3}") int maxShortWindowTurns,
            ChatMessageRepository chatRepo,
            LongTermMemoryRepository memoryRepo,
            EmbeddingModel embeddingModel,
            ObjectMapper objectMapper
    ) {
        this.maxShortWindowMessages = Math.max(2, Math.min(3, maxShortWindowTurns)) * 2;
        this.chatRepo = chatRepo;
        this.memoryRepo = memoryRepo;
        this.embeddingModel = embeddingModel;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public synchronized void preloadShortTermWindow() {
        shortWindow.clear();
        shortWindow.addAll(chatRepo.findRecentMessages(maxShortWindowMessages));
    }

    @Override
    public synchronized List<ChatMessage> getShortTermWindow() {
        return new ArrayList<>(shortWindow);
    }

    @Override
    public List<String> retrieveLongTermMemories(String queryText, int topK) {
        if (queryText == null || queryText.isBlank() || topK <= 0) {
            return List.of();
        }
        List<LongTermMemory> candidates = memoryRepo.findAllCandidates(embeddingModel.vectorSpace());
        if (candidates.isEmpty()) {
            return List.of();
        }

        float[] queryVector;
        try {
            queryVector = embeddingModel.embedQuery(clip(queryText, 1600));
        } catch (IllegalStateException unavailable) {
            log.warn("长期记忆召回暂不可用 ({})", unavailable.getMessage());
            return List.of();
        }

        record ScoredMemory(LongTermMemory memory, double score) {}
        List<ScoredMemory> scored = new ArrayList<>();
        for (LongTermMemory candidate : candidates) {
            float[] vector;
            try {
                vector = objectMapper.readValue(candidate.embeddingJson(), float[].class);
            } catch (JsonProcessingException invalid) {
                continue;
            }
            if (vector == null || vector.length != queryVector.length) {
                continue;
            }
            double similarity = embeddingModel.cosineSimilarity(queryVector, vector);
            double score = similarity * 0.8 + (candidate.importance() / 5.0) * 0.2;
            if (Double.isFinite(score)) {
                scored.add(new ScoredMemory(candidate, score));
            }
        }

        List<LongTermMemory> recalled = scored.stream()
                .sorted(Comparator.comparingDouble(ScoredMemory::score).reversed())
                .limit(topK)
                .map(ScoredMemory::memory)
                .toList();
        memoryRepo.updateLastRecalledAt(recalled.stream().map(LongTermMemory::id).toList());
        return recalled.stream()
                .map(memory -> "[" + memory.memoryType() + "] " + memory.factSummary())
                .toList();
    }

    @Override
    public synchronized ChatMessage recordVisibleMessage(String role, String content, Long thoughtLogId) {
        if ("ASSISTANT".equals(role)) {
            return recordAssistantReply(content, thoughtLogId);
        }
        return chatRepo.insert(thoughtLogId, role, content);
    }

    public synchronized void rememberUserMessage(Long messageId) {
        if (messageId != null && shortWindow.stream().noneMatch(message -> message.id().equals(messageId))) {
            chatRepo.findById(messageId).ifPresent(this::addToShortWindow);
        }
    }

    public synchronized ChatMessage recordAssistantReply(String content, Long thoughtLogId) {
        ChatMessage saved = chatRepo.insertAssistantReply(content, thoughtLogId);
        addToShortWindow(saved);
        return saved;
    }

    private void addToShortWindow(ChatMessage message) {
        while (shortWindow.size() >= maxShortWindowMessages) {
            shortWindow.pollFirst();
        }
        shortWindow.offerLast(message);
    }

    @Override
    @Async
    public void asyncExtractMemory(String userInput, String modelThought, Long sourceThoughtId) {
        try {
            for (Fact fact : extract(userInput, modelThought)) {
                if (!memoryRepo.containsFact(fact.type(), fact.summary())) {
                    memoryRepo.insert(fact.type(), fact.summary(), encode(fact.summary()), embeddingModel.vectorSpace(),
                            fact.importance(), sourceThoughtId);
                }
            }
            refreshOldVectors();
        } catch (IllegalStateException | JsonProcessingException failure) {
            log.warn("长期记忆写入暂不可用 ({})", failure.getClass().getSimpleName());
        }
    }

    private void refreshOldVectors() throws JsonProcessingException {
        if (!refreshingVectors.compareAndSet(false, true)) {
            return;
        }
        try {
            for (LongTermMemory memory : memoryRepo.findStaleVectors(embeddingModel.vectorSpace(), 8)) {
                memoryRepo.putVector(memory.id(), encode(memory.factSummary()), embeddingModel.vectorSpace());
            }
        } finally {
            refreshingVectors.set(false);
        }
    }

    private String encode(String fact) throws JsonProcessingException {
        return objectMapper.writeValueAsString(embeddingModel.embed(fact));
    }

    private static Set<Fact> extract(String userInput, String thought) {
        Set<Fact> facts = new LinkedHashSet<>();
        if (thought != null) {
            thought.lines().map(String::strip).map(FACT_LINE::matcher).filter(Matcher::matches)
                    .limit(3)
                    .forEach(match -> facts.add(new Fact(match.group(1), clip(match.group(2).strip(), 100))));
        }
        if (userInput != null) {
            addExplicitFact(facts, EXPLICIT_PREFERENCE, "PREFERENCE", userInput);
            addExplicitFact(facts, EXPLICIT_PROFILE, "USER_PROFILE", userInput);
        }
        return facts;
    }

    private static void addExplicitFact(Set<Fact> facts, Pattern pattern, String type, String input) {
        if (facts.stream().noneMatch(fact -> fact.type().equals(type))) {
            Matcher match = pattern.matcher(input);
            if (match.find()) {
                String statement = match.group().strip().replaceFirst("^[。！？；，,]", "").strip();
                facts.add(new Fact(type, clip(statement.replaceFirst("^我", "主人"), 100)));
            }
        }
    }

    private static String clip(String text, int maxCodePoints) {
        return text.codePointCount(0, text.length()) <= maxCodePoints
                ? text
                : text.substring(0, text.offsetByCodePoints(0, maxCodePoints));
    }

    private record Fact(String type, String summary) {
        int importance() {
            return switch (type) {
                case "USER_PROFILE" -> 5;
                case "PREFERENCE" -> 4;
                default -> 3;
            };
        }
    }
}

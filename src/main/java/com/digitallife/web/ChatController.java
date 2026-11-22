package com.digitallife.web;

import com.digitallife.domain.ChatMessage;
import com.digitallife.memory.HybridMemoryManager;
import com.digitallife.repository.ChatMessageRepository;
import com.digitallife.scheduler.HeartbeatScheduler;
import com.digitallife.scheduler.TriggerEvent;
import com.digitallife.scheduler.TurnProgress;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * 对话与历史消息 REST 控制器（对应 api.md 2.3 第 1、2 项）。
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    public record VisibleMessageDto(Long id, String role, String content, String createdAt) {
        static VisibleMessageDto from(ChatMessage message) {
            return new VisibleMessageDto(message.id(), message.role(), message.content(), message.createdAt());
        }
    }

    private final HybridMemoryManager memoryManager;
    private final ChatMessageRepository chatMessageRepository;
    private final HeartbeatScheduler heartbeatScheduler;
    private final TurnProgress turnProgress;

    public ChatController(
            HybridMemoryManager memoryManager,
            ChatMessageRepository chatMessageRepository,
            HeartbeatScheduler heartbeatScheduler,
            TurnProgress turnProgress
    ) {
        this.memoryManager = memoryManager;
        this.chatMessageRepository = chatMessageRepository;
        this.heartbeatScheduler = heartbeatScheduler;
        this.turnProgress = turnProgress;
    }

    /**
     * 主人主动发送消息（高优先级 Priority=1 入队并重置心跳计时器）。
     */
    @PostMapping("/send")
    public synchronized ApiResponse<Map<String, Object>> sendMessage(@RequestBody JsonNode request) {
        JsonNode content = request.path("content");
        if (!content.isTextual() || content.textValue().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content 必须是非空字符串");
        }
        String text = content.textValue().strip();

        // 接收时保存可见历史，轮到该事件后才将它加入模型上下文。
        ChatMessage savedUserMsg = memoryManager.recordVisibleMessage("USER", text, null);

        // 2. 重置定时心跳计时器并提交高优先级事件 (Priority=1)
        heartbeatScheduler.submitUserMessage(TriggerEvent.ofUserMessage(text, savedUserMsg.id()));

        return ApiResponse.ok(Map.of(
                "messageId", savedUserMsg.id(),
                "status", "QUEUED"
        ));
    }

    /** 查询当前进程的回合进度和模型输出。 */
    @GetMapping("/turns")
    public ResponseEntity<ApiResponse<List<TurnProgress.Entry>>> getTurns() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(turnProgress.snapshot()));
    }

    /**
     * 分页获取可见历史对话。
     */
    @GetMapping("/history")
    public ApiResponse<List<VisibleMessageDto>> getHistory(
            @RequestParam(name = "beforeId", required = false) Long beforeId,
            @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {
        if (beforeId != null && beforeId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "beforeId 必须是正整数");
        }
        return ApiResponse.ok(chatMessageRepository.findHistory(beforeId, limit).stream()
                .map(VisibleMessageDto::from)
                .toList());
    }
}

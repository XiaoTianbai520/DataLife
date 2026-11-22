package com.digitallife.web;

import com.digitallife.repository.BotConfigRepository;
import com.digitallife.scheduler.HeartbeatScheduler;
import com.digitallife.websocket.EventPushService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 角色人设与运行配置 REST 控制器（对应 api.md 2.3 第 3、4 项）。
 */
@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {

    public record ConfigDto(
            String personaPrompt,
            Integer heartbeatIntervalMinutes,
            Integer visionTargetWidth,
            Boolean muteMode
    ) {}

    private final BotConfigRepository configRepository;
    private final HeartbeatScheduler heartbeatScheduler;
    private final EventPushService eventPushService;

    public ConfigController(
            BotConfigRepository configRepository,
            HeartbeatScheduler heartbeatScheduler,
            EventPushService eventPushService
    ) {
        this.configRepository = configRepository;
        this.heartbeatScheduler = heartbeatScheduler;
        this.eventPushService = eventPushService;
    }

    @GetMapping
    public synchronized ApiResponse<ConfigDto> getConfig() {
        ConfigDto dto = new ConfigDto(
                configRepository.getString(BotConfigRepository.KEY_PERSONA_PROMPT, ""),
                configRepository.getInt(BotConfigRepository.KEY_HEARTBEAT_INTERVAL, 5),
                configRepository.getInt(BotConfigRepository.KEY_VISION_TARGET_WIDTH, 1280),
                configRepository.getBoolean(BotConfigRepository.KEY_MUTE_MODE, false)
        );
        return ApiResponse.ok(dto);
    }

    @PutMapping
    public synchronized ApiResponse<Void> updateConfig(@RequestBody JsonNode body) {
        if (!body.isObject() || body.isEmpty()) {
            throw invalid("配置内容必须是非空 JSON 对象");
        }

        Map<String, String> updates = new LinkedHashMap<>();
        body.fields().forEachRemaining(field -> {
            JsonNode value = field.getValue();
            switch (field.getKey()) {
                case "personaPrompt" -> {
                    if (!value.isTextual() || value.textValue().isBlank()) {
                        throw invalid("personaPrompt 必须是非空字符串");
                    }
                    updates.put(BotConfigRepository.KEY_PERSONA_PROMPT, value.textValue());
                }
                case "heartbeatIntervalMinutes" -> updates.put(BotConfigRepository.KEY_HEARTBEAT_INTERVAL,
                        positiveInt(field.getKey(), value));
                case "visionTargetWidth" -> updates.put(BotConfigRepository.KEY_VISION_TARGET_WIDTH,
                        positiveInt(field.getKey(), value));
                case "muteMode" -> {
                    if (!value.isBoolean()) {
                        throw invalid("muteMode 必须是布尔值");
                    }
                    updates.put(BotConfigRepository.KEY_MUTE_MODE, value.asText());
                }
                default -> throw invalid("未知配置字段 " + field.getKey());
            }
        });
        configRepository.upsertAll(updates);

        String interval = updates.get(BotConfigRepository.KEY_HEARTBEAT_INTERVAL);
        if (interval != null) {
            heartbeatScheduler.updateInterval(Integer.parseInt(interval));
        }
        String mute = updates.get(BotConfigRepository.KEY_MUTE_MODE);
        if (mute != null) {
            eventPushService.pushSystemNotice(
                    "INFO",
                    "MUTE_MODE_CHANGED",
                    Boolean.parseBoolean(mute) ? "已开启免打扰静音模式" : "已关闭免打扰静音模式"
            );
        }

        return ApiResponse.okMessage("updated", null);
    }

    private static String positiveInt(String field, JsonNode value) {
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() <= 0) {
            throw invalid(field + " 必须是正整数");
        }
        return value.asText();
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}

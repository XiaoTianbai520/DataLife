package com.digitallife.web;

import com.digitallife.sensor.Sensor;
import com.digitallife.sensor.SensorRegistry;
import com.digitallife.websocket.EventPushService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 感官插件管理与隐私开关 REST 控制器（对应 api.md 2.3 第 5、6 项）。
 */
@RestController
@RequestMapping("/api/v1/sensors")
public class SensorController {

    public record SensorStatusDto(String name, boolean enabled) {}

    private final SensorRegistry sensorRegistry;
    private final EventPushService eventPushService;

    public SensorController(SensorRegistry sensorRegistry, EventPushService eventPushService) {
        this.sensorRegistry = sensorRegistry;
        this.eventPushService = eventPushService;
    }

    @GetMapping
    public ApiResponse<List<SensorStatusDto>> listSensors() {
        List<SensorStatusDto> list = sensorRegistry.getAllSensors().stream()
                .sorted(Comparator.comparing(Sensor::getName))
                .map(s -> new SensorStatusDto(s.getName(), s.isEnabled()))
                .toList();
        return ApiResponse.ok(list);
    }

    @PatchMapping("/{name}")
    public ApiResponse<SensorStatusDto> toggleSensor(
            @PathVariable("name") String name,
            @RequestBody JsonNode request
    ) {
        JsonNode enabled = request.path("enabled");
        if (!enabled.isBoolean()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "enabled 必须是布尔值");
        }
        Optional<Sensor> updated = sensorRegistry.toggleSensor(name, enabled.booleanValue());
        if (updated.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到感官插件 " + name);
        }

        Sensor sensor = updated.get();
        eventPushService.pushSystemNotice(
                "INFO",
                "SENSOR_TOGGLED",
                String.format("感官 [%s] 已%s", sensor.getName(), sensor.isEnabled() ? "启用" : "暂停")
        );
        return ApiResponse.ok(new SensorStatusDto(sensor.getName(), sensor.isEnabled()));
    }
}

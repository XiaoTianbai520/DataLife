package com.digitallife.agent;

import com.digitallife.sensor.SensorySignal;
import com.digitallife.sensor.TemperatureSensor;
import com.digitallife.sensor.VisionSensor;
import com.digitallife.websocket.EventPushService;
import io.agentscope.core.message.ImageBlock;

import java.util.List;

/** 轻状态仅使用当轮感官证据与固定文案。 */
enum StatusAction {
    FEEL_WARMTH("察觉到一点热意，安静地想了想。"),
    GLANCE_SCREEN("悄悄看了一眼屏幕。"),
    QUIET("安静想了想，暂时没有开口。");

    private final String hintText;

    StatusAction(String hintText) {
        this.hintText = hintText;
    }

    static StatusAction forSnapshot(List<SensorySignal> signals) {
        if (signals.stream().anyMatch(signal -> TemperatureSensor.NAME.equals(signal.sensorName())
                && Boolean.TRUE.equals(signal.metadata().get("noteworthy")))) {
            return FEEL_WARMTH;
        }
        if (signals.stream().filter(signal -> VisionSensor.NAME.equals(signal.sensorName()))
                .flatMap(signal -> signal.blocks().stream()).anyMatch(ImageBlock.class::isInstance)) {
            return GLANCE_SCREEN;
        }
        return QUIET;
    }

    void push(EventPushService eventPushService, Long userMessageId) {
        eventPushService.pushStatusAction(name(), hintText, userMessageId);
    }
}

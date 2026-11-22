package com.digitallife.sensor;

import io.agentscope.core.message.ContentBlock;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 单次感官采集产出的多模态信号包。
 */
public record SensorySignal(
        String sensorName,
        Instant timestamp,
        List<ContentBlock> blocks,
        String summaryText,
        Map<String, Object> metadata
) {
}

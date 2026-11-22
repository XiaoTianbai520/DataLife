package com.digitallife.sensor;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 多模态感官快照池（SensorySnapshotPool）：
 * 在心跳触发或主人主动对话时，实时采集并打包所有启用感官的最新快照。
 */
@Component
public class SensorySnapshotPool {

    private final SensorRegistry sensorRegistry;

    public SensorySnapshotPool(SensorRegistry sensorRegistry) {
        this.sensorRegistry = sensorRegistry;
    }

    /**
     * 采集最新一轮所有已启用感官的多模态信号。
     */
    public List<SensorySignal> captureSnapshot() {
        return sensorRegistry.collectAllEnabled();
    }

    /**
     * 将感官信号列表汇总为纯文本摘要（仅用于日志与长期记忆检索，不含任何原始图像字节）。
     */
    public String buildTextSummary(List<SensorySignal> signals) {
        if (signals == null || signals.isEmpty()) {
            return "无启用感官";
        }
        return signals.stream()
                .map(SensorySignal::summaryText)
                .collect(Collectors.joining(" | "));
    }
}

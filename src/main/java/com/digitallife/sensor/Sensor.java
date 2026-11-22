package com.digitallife.sensor;

/**
 * 感官插件扩展 SPI 接口（所有感官统一实现此接口并注册至 SensorRegistry）。
 */
public interface Sensor {

    /** 感官唯一标识，如 "temperature"、"vision"、"audio" */
    String getName();

    /** 当前感官是否启用（支持运行期隐私开关热切换） */
    boolean isEnabled();

    void setEnabled(boolean enabled);

    /** 执行一次采集，返回 AgentScope 可直接消费的多模态块与纯文本摘要 */
    SensorySignal sense();
}

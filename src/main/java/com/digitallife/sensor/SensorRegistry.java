package com.digitallife.sensor;

import com.digitallife.repository.BotConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 感官插件注册中心：
 * 统一管理所有实现 Sensor 接口的感官插件，新增感官无需修改 DigitalLifeAgent 或 ContextBuilder。
 */
@Component
public class SensorRegistry {

    private static final Logger log = LoggerFactory.getLogger(SensorRegistry.class);

    private final Map<String, Sensor> sensors = new ConcurrentHashMap<>();
    private final BotConfigRepository configRepository;

    public SensorRegistry(List<Sensor> discoveredSensors, BotConfigRepository configRepository) {
        this.configRepository = configRepository;
        if (discoveredSensors != null) {
            for (Sensor sensor : discoveredSensors) {
                register(sensor);
            }
        }
    }

    public synchronized void register(Sensor sensor) {
        sensor.setEnabled(configRepository.getBoolean(enabledKey(sensor.getName()), sensor.isEnabled()));
        sensors.put(sensor.getName(), sensor);
        log.info("已注册感官插件: name={}, enabled={}", sensor.getName(), sensor.isEnabled());
    }

    public List<SensorySignal> collectAllEnabled() {
        List<SensorySignal> results = new ArrayList<>();
        for (Sensor sensor : sensors.values()) {
            if (sensor.isEnabled()) {
                try {
                    SensorySignal signal = sensor.sense();
                    if (signal != null) {
                        results.add(signal);
                    }
                } catch (Exception e) {
                    log.warn("采集感官 [{}] 异常: {}", sensor.getName(), e.getMessage());
                }
            }
        }
        return results;
    }

    public synchronized Optional<Sensor> toggleSensor(String name, boolean enabled) {
        Sensor sensor = sensors.get(name);
        if (sensor != null) {
            configRepository.upsert(enabledKey(name), Boolean.toString(enabled));
            sensor.setEnabled(enabled);
            log.info("感官 [{}] 状态切换为 enabled={}", name, enabled);
            return Optional.of(sensor);
        }
        return Optional.empty();
    }

    public Collection<Sensor> getAllSensors() {
        return sensors.values();
    }

    private static String enabledKey(String name) {
        return "sensor." + name + ".enabled";
    }
}

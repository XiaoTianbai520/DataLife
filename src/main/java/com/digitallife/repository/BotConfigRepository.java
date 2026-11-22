package com.digitallife.repository;

import com.digitallife.domain.BotConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class BotConfigRepository {

    public static final String KEY_PERSONA_PROMPT = "persona.prompt";
    public static final String KEY_HEARTBEAT_INTERVAL = "heartbeat.interval_minutes";
    public static final String KEY_VISION_TARGET_WIDTH = "vision.target_width";
    public static final String KEY_MUTE_MODE = "bot.mute_mode";

    private final JdbcTemplate jdbcTemplate;

    public BotConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<String> getValue(String key) {
        List<String> list = jdbcTemplate.query(
                "SELECT config_value FROM bot_config WHERE config_key = ?",
                (rs, rowNum) -> rs.getString("config_value"),
                key
        );
        return list.stream().findFirst();
    }

    public String getString(String key, String defaultValue) {
        return getValue(key).orElse(defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        return getValue(key).map(val -> {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }).orElse(defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return getValue(key).map(val -> Boolean.parseBoolean(val.trim())).orElse(defaultValue);
    }

    public void upsert(String key, String value) {
        String now = Instant.now().toString();
        jdbcTemplate.update("""
                INSERT INTO bot_config (config_key, config_value, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT(config_key) DO UPDATE SET
                    config_value = excluded.config_value,
                    updated_at = excluded.updated_at
                """, key, value, now);
    }

    @Transactional
    public void upsertAll(Map<String, String> updates) {
        updates.forEach(this::upsert);
    }

    public Map<String, String> findAllAsMap() {
        List<BotConfig> configs = jdbcTemplate.query(
                "SELECT config_key, config_value, description, updated_at FROM bot_config",
                (rs, rowNum) -> new BotConfig(
                        rs.getString("config_key"),
                        rs.getString("config_value"),
                        rs.getString("description"),
                        rs.getString("updated_at")
                )
        );
        Map<String, String> map = new LinkedHashMap<>();
        for (BotConfig c : configs) {
            map.put(c.configKey(), c.configValue());
        }
        return map;
    }
}

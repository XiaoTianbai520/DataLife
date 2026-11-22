package com.digitallife.domain;

/**
 * 系统与人设配置实体（对应表 bot_config）。
 */
public record BotConfig(
        String configKey,
        String configValue,
        String description,
        String updatedAt
) {
}

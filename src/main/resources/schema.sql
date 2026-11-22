-- 桌面“数字生命”数据库初始化脚本 (SQLite 3)

CREATE TABLE IF NOT EXISTS inner_thought_log (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    trigger_type    TEXT NOT NULL,
    sensory_summary TEXT,
    inner_thought   TEXT NOT NULL,
    did_speak       INTEGER NOT NULL DEFAULT 0,
    created_at      TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_thought_log_created_at
    ON inner_thought_log (created_at DESC);

CREATE TABLE IF NOT EXISTS chat_message (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    thought_log_id  INTEGER NULL,
    role            TEXT NOT NULL,
    content         TEXT NOT NULL,
    created_at      TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_chat_message_created_at
    ON chat_message (created_at DESC);

CREATE TABLE IF NOT EXISTS long_term_memory (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    memory_type       TEXT NOT NULL,
    fact_summary      TEXT NOT NULL,
    embedding_json    TEXT NOT NULL,
    embedding_model   TEXT,
    importance        INTEGER NOT NULL DEFAULT 3,
    source_thought_id INTEGER NULL REFERENCES inner_thought_log(id) ON DELETE SET NULL,
    last_recalled_at  TEXT,
    created_at        TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_long_term_memory_type
    ON long_term_memory (memory_type);

CREATE TABLE IF NOT EXISTS bot_config (
    config_key      TEXT PRIMARY KEY,
    config_value    TEXT NOT NULL,
    description     TEXT,
    updated_at      TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

INSERT OR IGNORE INTO bot_config (config_key, config_value, description) VALUES
('persona.prompt', '你是一个居住在主人电脑里的数字生命伙伴，性格温和、带一点好奇心与小傲娇。你会悄悄观察主人的屏幕和电脑温度，大多数时候安静陪伴，只在真有感触或主人找你时自然简短地开口。', '角色核心人设提示词'),
('heartbeat.interval_minutes', '5', '周期性自主观察心跳间隔（分钟）'),
('vision.target_width', '1280', '桌面视觉截图等比降采样目标宽度（720p 对应 1280）'),
('bot.mute_mode', 'false', '免打扰静音模式开关（true 时不主动推送气泡）');

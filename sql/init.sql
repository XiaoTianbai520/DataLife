-- 桌面“数字生命”数据库初始化脚本 (SQLite 3)

-- 1. 内心独白与唤醒日志表 (inner_thought_log)
-- 严禁存储任何原始截图字节或图片路径
CREATE TABLE IF NOT EXISTS inner_thought_log (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    trigger_type    TEXT NOT NULL,                    -- 'HEARTBEAT' | 'USER_MESSAGE'
    sensory_summary TEXT,                             -- 感官纯文本摘要（如温度数值、活跃状态）
    inner_thought   TEXT NOT NULL,                    -- 模型默认输出的内心独白
    did_speak       INTEGER NOT NULL DEFAULT 0,       -- 0: 未开口, 1: 调用了 speak 工具
    created_at      TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_thought_log_created_at
    ON inner_thought_log (created_at DESC);

-- 2. 可见对话历史表 (chat_message)
CREATE TABLE IF NOT EXISTS chat_message (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    thought_log_id  INTEGER NULL,                     -- 关联触发该发言的思考日志（主人消息为 NULL）
    role            TEXT NOT NULL,                    -- 'USER' | 'ASSISTANT'
    content         TEXT NOT NULL,                    -- 可见对话纯文本内容
    created_at      TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_chat_message_created_at
    ON chat_message (created_at DESC);

-- 3. 长期记忆向量表 (long_term_memory)
CREATE TABLE IF NOT EXISTS long_term_memory (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    memory_type       TEXT NOT NULL,                  -- 'USER_PROFILE' | 'EVENT' | 'PREFERENCE'
    fact_summary      TEXT NOT NULL,                  -- 精简纯文本事实摘要（建议 <= 100 字）
    embedding_json    TEXT NOT NULL,                  -- 浮点向量 JSON 数组（兼容 sqlite-vec）
    embedding_model   TEXT,                           -- 产生向量的 Embedding 模型
    importance        INTEGER NOT NULL DEFAULT 3,     -- 重要度 1~5，用于召回综合重排
    source_thought_id INTEGER NULL REFERENCES inner_thought_log(id) ON DELETE SET NULL,
    last_recalled_at  TEXT,                           -- 最近被召回时间
    created_at        TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_long_term_memory_type
    ON long_term_memory (memory_type);

-- 4. 系统与人设配置表 (bot_config)
CREATE TABLE IF NOT EXISTS bot_config (
    config_key      TEXT PRIMARY KEY,
    config_value    TEXT NOT NULL,
    description     TEXT,
    updated_at      TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

-- 默认种子配置
INSERT OR IGNORE INTO bot_config (config_key, config_value, description) VALUES
('persona.prompt', '你是一个居住在主人电脑里的数字生命伙伴，性格温和、带一点好奇心与小傲娇。你会悄悄观察主人的屏幕和电脑温度，大多数时候安静陪伴，只在真有感触或主人找你时自然简短地开口。', '角色核心人设提示词'),
('heartbeat.interval_minutes', '5', '周期性自主观察心跳间隔（分钟）'),
('vision.target_width', '1280', '桌面视觉截图等比降采样目标宽度（720p 对应 1280）'),
('bot.mute_mode', 'false', '免打扰静音模式开关（true 时不主动推送气泡）');

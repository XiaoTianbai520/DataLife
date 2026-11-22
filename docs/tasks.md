# 桌面“数字生命”开发任务与待办清单

> **文档版本**：v1.3  
> **文档职责**：开发任务、完成状态、待办事项（产品需求见 `PRD.md`，系统架构见 `architecture.md`，数据库设计见 `database.md`，接口定义见 `api.md`）

---

## 一、项目整体进度概览

| 阶段 / 里程碑 | 对应优先级 | 核心目标 | 当前状态 | 完成度 |
| :--- | :---: | :--- | :---: | :---: |
| **阶段 0：需求与技术设计** | - | 完成 PRD、系统架构、SQLite 数据库结构及接口规范定义 | ✅ 已完成 | 100% |
| **阶段 1：MVP 1.0（灵魂闭环版）** | **P0** | 温度和视觉采集、定时思考、发言和沉默、短长期记忆、桌面气泡交互 | 后端已实现，前端另行交付 | 按下方验收项跟踪 |
| **阶段 2：V1.1（体验与扩展版）** | **P1** | 补齐观察频率/隐私静音开关、走神轻状态反馈，接入至少 1 个新增扩展感官 | 📋 待办 | 0% |
| **阶段 3：V1.2（沉浸交互版）** | **P2** | 引入本地 TTS 语音播报与更丰富的桌面视觉呈现 | 📋 远期规划 | 0% |

---

## 二、已完成事项（Done）

- [x] **产品需求定义（`PRD.md`）**：明确“拟人化桌面数字生命”定位、四大产品目标、P0~P2 功能清单及“六不做”功能边界。
- [x] **系统架构设计（`architecture.md`）**：完成基于 Java 17+ 与 AgentScope Java 的四层架构设计、核心类图及单轮 1500 Tokens 上下文预算规划。
- [x] **数据库表结构设计（`database.md`）**：完成 SQLite 3 ER 图与 `chat_message`、`inner_thought_log`、`long_term_memory`、`bot_config` 四张核心表 DDL。
- [x] **接口规范定义（`api.md`）**：完成内部 Java SPI（`Sensor`、`MemoryManager`、`SpeakTool`）、REST API 及 WebSocket 实时事件推送协议定义。
- [x] **后端基础链路**：SQLite、感官插件、心跳调度、推理与混合记忆，以及 REST 和 WebSocket 接口。

---

## 三、MVP 1.0 核心开发任务清单（P0）

### 3.1 基础工程与数据持久层
- [x] **T1.1 初始化 Java 17+ 后端工程**：搭建 Maven 工程（`pom.xml`），引入 Spring Boot Web/WebSocket/JDBC、OSHI 及 `sqlite-jdbc` 依赖。
- [x] **T1.2 配置本地 SQLite 数据库环境**：基于 `sqlite-jdbc` 自动创建 `./data/digital_life.db` 单文件数据库（开启 WAL 模式），启动时自动执行 `schema.sql` 初始化四张核心表与 `bot_config` 默认种子数据。
- [x] **T1.3 实现数据访问层（Repository）**：
  - [x] `BotConfigRepository`：支持角色人设、心跳间隔、视觉目标宽度等配置的读取与热更新。
  - [x] `InnerThoughtLogRepository`：支持内心独白日志写入，以及应用启动时读取最新 1 条记录恢复 `InnerThoughtCache`。
  - [x] `ChatMessageRepository`：支持可见对话持久化、游标分页查询，以及启动时读取最近 2~3 轮对话恢复短期窗口。
  - [x] `LongTermMemoryRepository`：支持向量数据持久化与余弦相似度 + `importance` 综合重排的 Top-K 检索及 `last_recalled_at` 更新。

### 3.2 多模态感官插件层（纯 Java 宿主层）
- [x] **T2.1 实现感官 SPI 与注册中心**：编码 `Sensor` 接口、`SensorySignal` 记录类、`SensorRegistry` 与 `SensorySnapshotPool`。
- [x] **T2.2 实现电脑体感温度感知（`TemperatureSensor`）**：
  - [x] 优先读取 OSHI CPU 实测，再读取 NVIDIA GPU 实测。仅在两者都不可用时使用标明来源的负载估算。
- [x] **T2.3 实现桌面视觉感知（`VisionSensor`）**：
  - [x] 使用 `java.awt.Robot` 截取主屏幕，通过 `Graphics2D` 双线性插值等比缩放至 720p（宽度 1280），再用 `ImageIO` 压缩为 JPEG `ImageBlock`，图像字节仅驻留内存。

### 3.3 心跳与优先级调度层
- [x] **T3.1 实现优先级事件队列（`EventDispatcher`）**：
  - [x] 定义 `TriggerEvent` 与 `EventType`（`USER_MESSAGE` 优先级 `1`，`HEARTBEAT` 优先级 `10`）。
  - [x] 单线程处理推理。主人主动发言时优先入队并清除等待中的心跳，已经执行的回合结束后消费用户消息。
- [x] **T3.2 实现定时心跳调度器（`HeartbeatScheduler`）**：
  - [x] 调度器按配置提交 `HEARTBEAT` 事件，感官快照在事件实际处理时采集。
  - [x] 实现 `resetTimer()`：当主人主动发送消息时重置心跳倒计时，避免刚聊完天立刻触发定时心跳。

### 3.4 数字生命大脑与记忆层（AgentScope Java）
- [x] **T4.1 对接多模态模型（`Lfm3OmniChatModel`）**：由 AgentScope Java 调用指定局域网 Ollama 的 `lfm3-omni:2b`，测试可显式启用离线模拟模式。
- [x] **T4.2 实现内心独白缓存（`InnerThoughtCache`）**：维护上一轮未说出口的内心想法，支持每次思考后覆盖更新及重启恢复。
- [x] **T4.3 实现混合记忆管理器（`HybridMemoryManager`）**：
  - [x] 短期记忆：内存 `Deque<ChatMessage>` 维护最近 2~3 轮纯文本可见对话。
  - [x] 长期记忆召回：结合当轮输入与感官摘要检索 Top 3~5 条长期记忆（结合 `importance` 重排）。
  - [x] 异步记忆提炼：从主人明确陈述和模型给出的事实候选提取 `USER_PROFILE` / `EVENT` / `PREFERENCE` 摘要，由 `qwen3-embedding:0.6b` 向量化后写入 SQLite。旧库保留原始事实文本并逐批重算向量。
- [x] **T4.4 实现受控上下文组装器（`ContextBuilder`）**：
  - [x] 使用随包 LFM3-Omni tokenizer 组装文本上下文，预留工具和模板开销，总预算 1500 Tokens，视觉 Token 单独计算。

### 3.5 思维与表达分离闭环
- [x] **T5.1 实现 `SpeakTool` 与单次推理截断机制**：
  - [x] 通过 AgentScope 注册 `speak`，同一事务更新思考日志并保存 `ASSISTANT` 消息，然后推送 `SPEAK_MESSAGE`。每轮只发起一次生成请求。
- [x] **T5.2 实现异常兜底与阅后即焚保障**：
  - [x] 捕获轻量模型偶发的 Tool Calling 格式畸形异常，安全降级为“仅更新 `InnerThoughtCache`、不对外推送”。
  - [x] 使用内存流编码 JPEG。截图只用于当前推理请求，数据库和长期记忆接收文本。

### 3.6 前后端通信接口与桌面前端
- [x] **T6.1 实现 REST API 控制器**：
  - [x] `ChatController`：`POST /api/v1/chat/send` 与 `GET /api/v1/chat/history`
  - [x] `ConfigController`：`GET /api/v1/config` 与 `PUT /api/v1/config`
  - [x] `SensorController`：`GET /api/v1/sensors` 与 `PATCH /api/v1/sensors/{name}`
- [x] **T6.2 实现 WebSocket 事件推送服务（`EventPushService`）**：
  - [x] 暴露 `ws://127.0.0.1:8080/ws/events`，封装 `SPEAK_MESSAGE`、`STATUS_ACTION`、`SYSTEM_NOTICE` 标准消息体。
- [ ] **T6.3 桌面前端对接**。由前端任务另行负责，接口以 [api.md](api.md) 为准。

### 3.7 MVP 联调与实机验收
- [x] **T7.1 模型服务配置**。指定 Ollama 上已有 `lfm3-omni:2b` 和 `qwen3-embedding:0.6b`。
  - [x] 真实联调覆盖合成图像理解、发言事件、私有独白落库、1024 维长期记忆写入和召回，以及旧向量重建。
- [ ] **T7.2 陪伴真实感与沉默权实测**：连续运行 10 轮心跳，验证角色在主人专注工作时保持沉默并连贯更新 `inner_thought_log`，在电脑高温或场景切换时自然搭话。
  - [x] 已在隔离数据库中实际运行定时心跳，角色保持沉默并生成一条独白。重启后感官启停状态保持，历史仍可用游标翻页。
  - [x] 已用真实模型加速触发 10 轮心跳并写入独白。稳定场景 8 轮均保持沉默，最后两轮模拟高温时模型也选择沉默。自主发言链路另有工具调用回归测试，高温发言的稳定性继续按真实场景观察。
- [ ] **T7.3 隐私与资源消耗验收**：测量后台常驻 CPU/内存开销与单轮推理延迟。
  - 本机预热后采集和编码 8 次，耗时 78–136 ms。按本次确认的 P0 口径保留 AWT 采集，50 ms 留作后续优化。

### 3.8 后端运行信息

项目根目录执行以下命令。使用 Java 17 或更高版本。

```powershell
mvn -B package
java -jar target/digital-life-companion-0.1.0-SNAPSHOT.jar
```

默认连接 `http://192.168.1.102:11434/v1`。应用监听 `127.0.0.1:8080`，数据库存放在工作目录的 `data/digital_life.db`。可通过 `DIGITALLIFE_LLM_BASE_URL`、`DIGITALLIFE_MEMORY_EMBEDDING_BASE_URL` 和 `DIGITALLIFE_MEMORY_EMBEDDING_MODEL` 指定同一模型的其他局域网端点。实际桌面采集需要交互式桌面会话。

随包的 [LFM3-Omni tokenizer](https://huggingface.co/LiquidAI/LFM3-Omni-2B/tree/c01718a568e53ae936e6b5e1ff5ab2de7d7f388b) 遵循随包的 LFM Open License。

---

## 四、后续版本待办事项（Backlog）

### 4.1 V1.1 体验与扩展版待办（P1）
- [ ] **走神/轻状态反馈精细化**：根据当前屏幕场景丰富 `STATUS_ACTION` 的动作类型与文案。
- [ ] **桌面悬浮窗化封装（Tauri / Electron）**：将现有 Web 前端封装为透明置顶桌面悬浮气泡与系统托盘菜单。
- [ ] **扩展新感官插件（验证 `Sensor` SPI 无痛扩展）**：
  - [ ] `ActivitySensor`：感知键鼠空闲/发呆时长或高频敲击状态。
  - [ ] `AudioSensor`：感知电脑当前是否在播放音乐/视频音频。

### 4.2 V1.2 沉浸交互版待办（P2）
- [ ] **本地语音播报（TTS）集成**：将 `SpeakTool` 输出的文本通过本地轻量 TTS 模型转化为语音播放，支持静音开关控制。
- [ ] **桌面角色视觉表现升级**：在轻量气泡基础上增加多状态静态立绘切换或轻量微表情反馈。

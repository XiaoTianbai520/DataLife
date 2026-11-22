# 桌面“数字生命”接口定义文档

> **文档版本**：v1.2  
> **通信地址**：本地回环 `http://127.0.0.1:8080` / `ws://127.0.0.1:8080/ws/events`  
> **文档职责**：接口定义、请求响应格式（产品需求见 `PRD.md`，系统架构见 `architecture.md`，数据库设计见 `database.md`，开发任务见 `tasks.md`）

后端绑定回环地址。同源 Web 前端和 Windows Tauri 内置前端（`http://tauri.localhost`）可连接 REST 与 WebSocket。通过 `DIGITALLIFE_WEB_ALLOWED_ORIGINS` 可追加逗号分隔的允许来源，例如 `http://localhost:5173`。

---

## 一、内部核心 Java SPI 与工具接口

### 1.1 感官插件扩展接口（`Sensor`）

所有环境感官（温度、视觉及未来扩展的听觉、键鼠活跃度等）均实现此接口，并注册至 `SensorRegistry`：

```java
public interface Sensor {
    /** 感官唯一标识，如 "temperature"、"vision"、"audio" */
    String getName();

    /** 当前感官是否启用（支持运行期隐私开关热切换） */
    boolean isEnabled();

    void setEnabled(boolean enabled);

    /** 执行一次采集，返回 AgentScope 可直接消费的多模态块与纯文本摘要 */
    SensorySignal sense();
}

public record SensorySignal(
    String sensorName,
    Instant timestamp,
    List<ContentBlock> blocks,   // 承载 TextBlock、ImageBlock、AudioBlock
    String summaryText,          // 供日志与记忆检索使用的轻量纯文本
    Map<String, Object> metadata // 客观辅助指标
) {}
```

温度感官返回 `temperatureCelsius`、`temperatureSource`、`cpuLoad`、`estimated` 与 `noteworthy`。优先读取 CPU 实测，其次读取 NVIDIA GPU 实测，最后使用标明来源的负载估算。来源分别为 `CPU`、`NVIDIA_GPU`、`CPU_LOAD_ESTIMATE`。体感偏热且有实测值时，`noteworthy` 提醒模型关注当前观察，发言仍由模型决定。

### 1.2 记忆管理接口（`MemoryManager`）

统一管理最近 2~3 轮纯文本对话，以及基于 SQLite 和 Ollama Embedding 的长期记忆：

```java
public interface MemoryManager {
    /** 获取最近 N 轮可见对话（默认 2~3 轮纯文本） */
    List<ChatMessage> getShortTermWindow();

    /** 根据当前输入与感官摘要，动态召回最相关的 Top-K 长期记忆 */
    List<String> retrieveLongTermMemories(String queryText, int topK);

    /** 记录一条可见对话（主人输入或 speak 输出） */
    ChatMessage recordVisibleMessage(String role, String content, Long thoughtLogId);

    /** 异步从对话或内心独白中提炼事实并写入向量库（不阻塞主推理线程） */
    void asyncExtractMemory(String userInput, String modelThought, Long sourceThoughtId);
}
```

### 1.3 表达工具接口（AgentScope `SpeakTool`）

每轮只向模型注册一个 `speak` 工具。模型保持沉默时直接返回独白。模型开口时将独白放在私有参数 `innerThought`，因为 Ollama 在发出工具调用时可能返回空正文。

```java
@Tool(name = "speak", returnDirect = true)
public String speak(
    @ToolParam(name = "content") String content,
    @ToolParam(name = "innerThought") String innerThought
)
```

`content` 在保存后经 WebSocket 推送。`innerThought` 经清理后写入后端思考日志，供后续推理和回合进度查看。模型输出缺少有效独白或发言参数时静默降级。工具完成后结束本轮。

---

## 二、前后端通信 REST API 定义

本地桌面前端（如 Electron / Tauri / 轻量 Web 浮窗）通过本地 HTTP 与 Java 后端通信（默认绑定 `127.0.0.1:8080`）。

### 2.1 统一响应信封格式

所有 REST 接口统一返回 JSON 信封格式：

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

* `code = 0`：请求成功。
* 请求入参无效时返回 HTTP `400`，感官名称不存在时返回 HTTP `404`，控制器处理异常时返回 HTTP `500`。失败信封的 `code` 与 HTTP 状态一致，`data` 为 `null`。

### 2.2 REST API 接口总览

| 接口路径 | 方法 | 说明 |
| :--- | :---: | :--- |
| `/api/v1/chat/send` | `POST` | 主人主动发送消息（高优先级 `Priority=1` 入队并重置心跳计时器） |
| `/api/v1/chat/history` | `GET` | 以 ID 游标向更早消息翻页，每页按 ID 升序返回 |
| `/api/v1/chat/turns` | `GET` | 查看当前及最近的回合进度和已完成回合的模型思考 |
| `/api/v1/config` | `GET` | 获取当前角色人设、心跳频率、视觉分辨率与静音模式配置 |
| `/api/v1/config` | `PUT` | 热更新角色人设、心跳间隔或免打扰静音模式 |
| `/api/v1/sensors` | `GET` | 获取所有已注册感官插件列表及当前启停状态 |
| `/api/v1/sensors/{name}` | `PATCH` | 动态开启或关闭单个感官（如一键关闭视觉感知） |

---

### 2.3 接口详细请求与响应格式

#### 1. 主人主动发送消息
* **请求**：`POST /api/v1/chat/send`
* **请求头**：`Content-Type: application/json`
* **请求体（Request Body）**：
  ```json
  {
    "content": "今天电脑好卡"
  }
  ```
* **响应体（Response Body）**：
  ```json
  {
    "code": 0,
    "message": "ok",
    "data": {
      "messageId": 102,
      "status": "QUEUED"
    }
  }
  ```
> **说明**：`content` 必须为非空字符串。服务端保存完整文本，进入模型的片段遵循当轮 Token 预算。接口持久化消息并入队后返回 `QUEUED`。主动消息按消息 ID 顺序串行处理。已排队的心跳会被清除，繁忙时跳过新的心跳，正在执行的回合允许结束。回复或轻状态通过 WebSocket 推送，可按 `messageId` 查询回合进度。

---

#### 2. 分页获取可见历史对话
* **请求**：`GET /api/v1/chat/history?beforeId=105&limit=20`
* **Query 参数**：
  * `beforeId`（可选，正整数）：获取 `id < beforeId` 的历史消息。省略时获取最新一页。继续向前翻页时传本页首条消息的 ID。
  * `limit`（可选，整数，默认 `20`）：单页条数。服务端把该值限制在 `1~100`。
  * 结果每页按 ID 升序排列，`createdAt` 为 UTC 的 ISO-8601 时间字符串。
* **响应体（Response Body）**：
  ```json
  {
    "code": 0,
    "message": "ok",
    "data": [
      {
        "id": 101,
        "role": "USER",
        "content": "在干嘛呢？",
        "createdAt": "2026-11-22T06:58:10Z"
      },
      {
        "id": 102,
        "role": "ASSISTANT",
        "content": "在看你写代码呀，刚才那个正则改了三遍咯。",
        "createdAt": "2026-11-22T06:58:13Z"
      }
    ]
  }
  ```

---

#### 3. 获取当前角色人设与运行配置
* **请求**：`GET /api/v1/config`
* **响应体（Response Body）**：
  ```json
  {
    "code": 0,
    "message": "ok",
    "data": {
      "personaPrompt": "你是一个居住在主人电脑里的数字生命伙伴，性格温和、带一点好奇心与小傲娇...",
      "heartbeatIntervalMinutes": 5,
      "visionTargetWidth": 1280,
      "muteMode": false
    }
  }
  ```

---

#### 4. 热更新角色人设与运行配置
* **请求**：`PUT /api/v1/config`
* **请求头**：`Content-Type: application/json`
* **请求体（Request Body，支持局部或全量字段更新）**：
  ```json
  {
    "personaPrompt": "你是一个毒舌但贴心的桌面数字生命伙伴...",
    "heartbeatIntervalMinutes": 10,
    "visionTargetWidth": 1280,
    "muteMode": false
  }
  ```
  传至少一个已定义字段。`personaPrompt` 为非空字符串，两个数值字段均为正整数，`muteMode` 为布尔值。所有字段通过校验后一次性保存。
  提交 `heartbeatIntervalMinutes` 后，心跳从保存时按新周期重新计时。免打扰期间心跳继续观察和记录独白，心跳发言会被抑制，用户主动对话仍可获得发言或轻状态。配置在应用重启后恢复。
* **响应体（Response Body）**：
  ```json
  {
    "code": 0,
    "message": "updated",
    "data": null
  }
  ```

---

#### 5. 获取所有已注册感官列表及状态
* **请求**：`GET /api/v1/sensors`
* **响应体（Response Body）**：
  ```json
  {
    "code": 0,
    "message": "ok",
    "data": [
      {
        "name": "temperature",
        "enabled": true
      },
      {
        "name": "vision",
        "enabled": true
      }
    ]
  }
  ```

---

#### 6. 动态开启或关闭单个感官
* **请求**：`PATCH /api/v1/sensors/{name}`（例如：`PATCH /api/v1/sensors/vision`）
* **请求头**：`Content-Type: application/json`
* 保存后的启停状态在应用重启后恢复。暂停视觉会停止新的截屏。
* **请求体（Request Body）**：
  ```json
  {
    "enabled": false
  }
  ```
* **响应体（Response Body）**：
  ```json
  {
    "code": 0,
    "message": "ok",
    "data": {
      "name": "vision",
      "enabled": false
    }
  }
  ```

---

#### 7. 查询回合进度与模型思考

* **请求** `GET /api/v1/chat/turns`，无请求参数。响应携带 `Cache-Control: no-store`。
* **返回** `data` 为数组，包含当前进程正在处理或排队的回合，以及最近 30 个已结束回合。刷新页面可恢复显示，服务重启后从空的进度快照开始。可见对话历史仍通过 `/api/v1/chat/history` 获取。

| `data[]` 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `turnId` | string | 回合唯一标识 |
| `triggerType` | string | `USER_MESSAGE` 主动消息，`HEARTBEAT` 定时观察 |
| `userMessageId` | integer 或 null | 匹配发送回执中的 `messageId`。定时观察为 null |
| `stage` | string | `QUEUED` 已收到并排队，`PREPARING` 准备上下文，`THINKING` 等待模型，`REPLIED` 已回复，`SILENT` 已完成并保持安静，`NO_REPLY` 回合结束且无可交付输出，`UNAVAILABLE` 模型请求失败，`FAILED` 回合处理失败 |
| `receivedAt`、`updatedAt` | string | UTC ISO-8601 时间，分别表示触发时间和最近一次阶段更新时间 |
| `startedAt` | string 或 null | 开始处理的 UTC ISO-8601 时间；排队时为 null |
| `innerThought` | string 或 null | 模型独白，已滤除记忆提炼标记。已回复和沉默回合可返回，缺少内容时为空字符串 |
| `reasoning` | string 或 null | 同一响应中模型服务提供的独立推理输出。已回复和沉默回合可返回，未提供时为空字符串 |

思考内容随回合完成后提供。主动推理默认可以等待 120 秒，定时观察默认 30 秒，长期记忆查询默认 5 秒。用 `digitallife.llm.user-turn-timeout-seconds`、`digitallife.llm.heartbeat-timeout-seconds` 和 `digitallife.memory.recall-timeout-seconds` 调整。记忆查询超时后仍继续推理，此轮使用其他已就绪的上下文。

---

## 三、WebSocket 实时推送协议

* **连接端点**：`ws://127.0.0.1:8080/ws/events`
* **通信方向**：服务端 -> 前端单向事件推送（用于角色主动开口发言、回复主人对话及轻状态反馈）。

### 3.1 统一消息体结构

```json
{
  "eventType": "SPEAK_MESSAGE | STATUS_ACTION | SYSTEM_NOTICE",
  "timestamp": "2026-11-22T15:05:00+08:00",
  "payload": {}
}
```

### 3.2 事件类型与 Payload 格式

#### 事件 1：角色开口发言（`SPEAK_MESSAGE`）
成功保存可见发言后推送。免打扰期间，定时心跳（`HEARTBEAT`）的发言被抑制。用户对话（`USER_MESSAGE`）的发言包含 `userMessageId`，对应发送接口返回的用户消息 ID。定时心跳省略该字段。

```json
{
  "eventType": "SPEAK_MESSAGE",
  "timestamp": "2026-11-22T15:05:00+08:00",
  "payload": {
    "messageId": 103,
    "triggerType": "HEARTBEAT",
    "content": "你电脑风扇呼呼响半天了，CPU都快八十度啦，休息一下呗？"
  }
}
```

#### 事件 2：轻状态动作反馈（`STATUS_ACTION`）
当主人主动发消息但模型选择保持沉默（未调用 `speak` 工具）时，服务端推送非侵入式小动作提示，避免前端界面看起来像“卡死无响应”：

```json
{
  "eventType": "STATUS_ACTION",
  "timestamp": "2026-11-22T15:06:12+08:00",
  "payload": {
    "action": "GLANCE_SCREEN",
    "hintText": "悄悄看了一眼屏幕。",
    "userMessageId": 104
  }
}
```

`userMessageId` 对应发送接口返回的用户消息 ID。动作按下列顺序匹配当轮证据，文案使用固定短句。轻状态仅作实时提示，历史对话接口只返回可见的文本消息。畸形工具调用静默降级，心跳沉默也无轻状态事件。

| `action` | 使用场景 |
| :--- | :--- |
| `FEEL_WARMTH` | 体感温度实测偏热，温度感官报告 `noteworthy=true` |
| `GLANCE_SCREEN` | 当轮成功取得桌面图像 |
| `QUIET` | 其他情况，包括视觉得不到画面 |

#### 事件 3：系统通知（`SYSTEM_NOTICE`）
当感官开关变更、免打扰模式切换或本地模型连接状态变化时推送给前端状态栏：

```json
{
  "eventType": "SYSTEM_NOTICE",
  "timestamp": "2026-11-22T15:10:00+08:00",
  "payload": {
    "level": "INFO",
    "code": "SENSOR_TOGGLED",
    "message": "视觉感官已暂停，当前处于隐私保护状态"
  }
}
```

通知代码包括 `SENSOR_TOGGLED`、`MUTE_MODE_CHANGED`、`MODEL_UNAVAILABLE` 和 `MODEL_AVAILABLE`。模型可达但输出畸形时静默处理。

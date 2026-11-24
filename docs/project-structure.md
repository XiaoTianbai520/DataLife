# 数字生命项目目录导览

数字生命由 Java 17 / Spring Boot 后端、原生 HTML/CSS/JavaScript 界面和 Tauri Windows 桌面壳组成。后端负责会话、模型调用、感官和 SQLite 存储。浏览器和桌面端共用一套界面，通过 REST 和 WebSocket 连接本地后端。

## 仓库目录

```text
Life/
├── AGENTS.md                          # 贡献指南与开发命令
├── pom.xml                            # Maven 依赖、测试与后端打包
├── docs/                              # 需求、设计、接口和验收文档
├── sql/
│   └── init.sql                       # 独立使用的 SQLite 初始化脚本
├── src/
│   ├── main/
│   │   ├── java/com/digitallife/       # 后端 Java 源码
│   │   └── resources/
│   │       ├── application.yml        # 服务、数据库、模型与感官默认配置
│   │       ├── schema.sql             # Spring Boot 启动时执行的数据库脚本
│   │       ├── model/                 # 分词器资源及许可证
│   │       └── static/                # Web 与桌面共用的页面、样式和脚本
│   └── test/java/com/digitallife/      # 单元测试和集成测试
└── src-tauri/                         # Tauri 桌面壳与 Windows 打包配置
```

默认在进程工作目录的 `data/digital_life.db` 保存本地数据。从仓库根目录启动时，数据库就在根目录下的 `data/`。Maven 构建产物在 `target/`，桌面构建产物在 `src-tauri/target/`。这些产物和本地数据库文件由 Git 忽略。

## 后端 Java 包

[DigitalLifeApplication.java](../src/main/java/com/digitallife/DigitalLifeApplication.java) 是服务入口。下面各包均位于 `src/main/java/com/digitallife/`。

| 包 | 职责与入口 |
| --- | --- |
| [`web`](../src/main/java/com/digitallife/web/) | REST 入口。`ChatController` 接收消息并查询历史和回合进度，`ConfigController` 和 `SensorController` 管理设置与感官开关。 |
| [`websocket`](../src/main/java/com/digitallife/websocket/) | `WebSocketConfig` 注册实时连接，`EventPushService` 推送回复、轻状态和系统通知。 |
| [`scheduler`](../src/main/java/com/digitallife/scheduler/) | `HeartbeatScheduler` 管理定时观察，`EventDispatcher` 串行处理触发事件，`TurnProgress` 维护内存中的回合进度。 |
| [`agent`](../src/main/java/com/digitallife/agent/) | `DigitalLifeAgent` 编排单次回合。`ContextBuilder` 组装模型上下文，`SpeakTool` 交付聊天文本，`StatusAction` 生成轻状态。 |
| [`agentscope`](../src/main/java/com/digitallife/agentscope/) | 模型适配。`Lfm3OmniChatModel` 调用聊天模型并解析工具调用，`OllamaEmbeddingModel` 为记忆检索生成向量。 |
| [`sensor`](../src/main/java/com/digitallife/sensor/) | `Sensor` 定义扩展接口，`SensorRegistry` 管理感官，`SensorySnapshotPool` 在回合开始时汇总已启用感官的信号。目前有视觉和温度实现。 |
| [`memory`](../src/main/java/com/digitallife/memory/) | `HybridMemoryManager` 维护短期对话窗口，提取并检索长期记忆。 |
| [`domain`](../src/main/java/com/digitallife/domain/) 与 [`repository`](../src/main/java/com/digitallife/repository/) | 数据对象与 SQLite 读写，涵盖配置、对话消息、思考记录和长期记忆。 |

## Web 与桌面入口

| 文件 | 用途 |
| --- | --- |
| [`static/index.html`](../src/main/resources/static/index.html) | 聊天、思考与进度、设置等界面的 HTML 入口。 |
| [`static/js/app.js`](../src/main/resources/static/js/app.js) | 管理页面状态、事件处理和渲染。 |
| [`static/js/api.js`](../src/main/resources/static/js/api.js) | 封装 REST 请求和 WebSocket 连接。浏览器使用同源地址，Tauri 界面连接 `http://127.0.0.1:8080`。 |
| [`static/css/modern-minimal.css`](../src/main/resources/static/css/modern-minimal.css) | 共用布局、设计变量和组件样式。 |
| [`src-tauri/src/main.rs`](../src-tauri/src/main.rs) | 创建透明置顶气泡、管理窗口模式和系统托盘。 |
| [`src-tauri/tauri.conf.json`](../src-tauri/tauri.conf.json) | 指定共用静态目录、连接策略和 Windows 安装包配置。`capabilities/` 配置窗口权限，`icons/` 存放图标。 |

调试一条用户消息时，从 `app.js` 和 `api.js` 跟到 `ChatController`，再看 `EventDispatcher` 如何安排回合。`DigitalLifeAgent` 采集感官信号、检索记忆、组装上下文并调用模型。可见回复或轻状态经 WebSocket 推送，思考与进度栏目通过 `GET /api/v1/chat/turns` 读取回合状态。

## 测试与参考文档

测试位于 [`src/test/java/com/digitallife/`](../src/test/java/com/digitallife/)，按后端包组织。`DigitalLifeSkeletonTest` 覆盖 Spring Boot、SQLite 和接口联动。`EventDispatcherTest` 覆盖回合排队，`Lfm3OmniChatModelTest` 覆盖模型输出解析。从仓库根目录运行 `mvn -B test` 执行 Java 测试，使用 `mvn -B -Dtest=EventDispatcherTest test` 单独验证调度。

| 想了解 | 阅读 |
| --- | --- |
| 产品定位与功能范围 | [PRD.md](PRD.md) |
| 技术栈与模块设计 | [architecture.md](architecture.md) |
| REST、WebSocket 与内部 SPI | [api.md](api.md) |
| 页面设计规范 | [design.md](design.md) |
| SQLite 表结构与存储约束 | [database.md](database.md) |
| 开发进度与验收记录 | [tasks.md](tasks.md) |

# UI 设计规范文档 — Modern Minimal

> **规范来源**：[21st.dev — @serafimcloud/themes/modern-minimal](https://21st.dev/@serafimcloud/themes/modern-minimal)  
> **适用范围**：本项目所有新页面、模块改版与前端组件开发  
> **执行原则**：每次开发新页面前，**必须先阅读本文件**并严格遵守以下配色、字体、间距与组件规范，禁止随意引入未定义的色值、圆角或过重阴影，确保全站视觉风格统一。

---

## 0. 风格总览（Design Philosophy）

**Modern Minimal** 是一套面向现代 Web 应用与 SaaS 产品的极简设计系统，核心视觉特征为：

- **纯净高对比画布**：浅色模式以纯白（`#FFFFFF`）为基底、深色模式以中性炭黑（`#171717`）为基底，辅以极克制的冷灰分层表面。
- **单一高饱和主色点缀**：全局以明快的现代蓝（`#3B82F6`）作为唯一主交互强调色，用于主按钮、焦点环、开关、选中态与数据主色；强调悬浮采用柔和浅蓝/深蓝底色，避免花哨多色干扰。
- **紧凑利落的几何秩序**：以 `4px` 为间距基准单位、`6px` 为基础圆角、`1px` 细边框界定层次，配合极轻的阴影与清晰的排版对比建立信息层级。

---

## 1. 配色规范（Color Palette）

> 默认使用 **浅色模式（Light Mode）**；若页面支持暗黑切换，在根节点添加 `.dark` 类启用 **深色模式（Dark Mode）**。

### 1.1 主色与交互焦点色（Primary & Ring）

| 语义用途 | CSS 变量 | 浅色模式 HEX / RGB | 深色模式 HEX / RGB | 原始 OKLCH 值 |
| :--- | :--- | :--- | :--- | :--- |
| 主品牌色 / 主按钮背景 | `--primary` | `#3B82F6` / `rgb(59, 130, 246)` | `#3B82F6` / `rgb(59, 130, 246)` | `oklch(0.623 0.188 259.815)` |
| 主色上的文字 / 图标 | `--primary-foreground` | `#FFFFFF` / `rgb(255, 255, 255)` | `#FFFFFF` / `rgb(255, 255, 255)` | `oklch(1 0 0)` |
| 焦点高亮环（Focus Ring） | `--ring` | `#3B82F6` / `rgb(59, 130, 246)` | `#3B82F6` / `rgb(59, 130, 246)` | `oklch(0.623 0.188 259.815)` |

### 1.2 辅助色与语义状态色（Secondary / Accent / Destructive）

| 语义用途 | CSS 变量 | 浅色模式 HEX / RGB | 深色模式 HEX / RGB | 原始 OKLCH 值（浅 / 深） |
| :--- | :--- | :--- | :--- | :--- |
| 辅助表面 / 次级按钮背景 | `--secondary` | `#F3F4F6` / `rgb(243, 244, 246)` | `#262626` / `rgb(38, 38, 38)` | `0.967 0.003 264.542` / `0.269 0 0` |
| 辅助表面文字 | `--secondary-foreground` | `#4B5563` / `rgb(75, 85, 99)` | `#E5E5E5` / `rgb(229, 229, 229)` | `0.446 0.026 256.802` / `0.922 0 0` |
| 强调背景 / 悬浮选中底色 | `--accent` | `#E0F2FE` / `rgb(224, 242, 254)` | `#1E3A8A` / `rgb(30, 58, 138)` | `0.951 0.025 236.824` / `0.379 0.138 265.522` |
| 强调背景上的文字 | `--accent-foreground` | `#1E3A8A` / `rgb(30, 58, 138)` | `#BFDBFE` / `rgb(191, 219, 254)` | `0.379 0.138 265.522` / `0.882 0.057 254.128` |
| 危险 / 删除 / 错误警示色 | `--destructive` | `#EF4444` / `rgb(239, 68, 68)` | `#EF4444` / `rgb(239, 68, 68)` | `0.637 0.208 25.331` |
| 危险色上的文字 | `--destructive-foreground` | `#FFFFFF` / `rgb(255, 255, 255)` | `#FFFFFF` / `rgb(255, 255, 255)` | `1 0 0` |

### 1.3 背景与表面色（Background & Surface）

| 语义用途 | CSS 变量 | 浅色模式 HEX / RGB | 深色模式 HEX / RGB | 原始 OKLCH 值（浅 / 深） |
| :--- | :--- | :--- | :--- | :--- |
| 页面主背景 | `--background` | `#FFFFFF` / `rgb(255, 255, 255)` | `#171717` / `rgb(23, 23, 23)` | `1 0 0` / `0.205 0 0` |
| 卡片背景 | `--card` | `#FFFFFF` / `rgb(255, 255, 255)` | `#262626` / `rgb(38, 38, 38)` | `1 0 0` / `0.269 0 0` |
| 浮层 / 下拉 / 弹窗背景 | `--popover` | `#FFFFFF` / `rgb(255, 255, 255)` | `#262626` / `rgb(38, 38, 38)` | `1 0 0` / `0.269 0 0` |
| 弱化背景 / 分组浅底 | `--muted` | `#F9FAFB` / `rgb(249, 250, 251)` | `#1F1F1F` / `rgb(31, 31, 31)` | `0.985 0.002 247.839` / `0.239 0 0` |
| 侧边栏背景 | `--sidebar` | `#F9FAFB` / `rgb(249, 250, 251)` | `#171717` / `rgb(23, 23, 23)` | `0.985 0.002 247.839` / `0.205 0 0` |

### 1.4 文字色（Typography Colors）

| 语义用途 | CSS 变量 | 浅色模式 HEX / RGB | 深色模式 HEX / RGB | 原始 OKLCH 值（浅 / 深） |
| :--- | :--- | :--- | :--- | :--- |
| 主文字（标题、正文、卡片/弹窗/侧栏文字） | `--foreground` / `--card-foreground` / `--popover-foreground` / `--sidebar-foreground` | `#333333` / `rgb(51, 51, 51)` | `#E5E5E5` / `rgb(229, 229, 229)` | `0.321 0 0` / `0.922 0 0` |
| 次级文字（辅助按钮、标签） | `--secondary-foreground` | `#4B5563` / `rgb(75, 85, 99)` | `#E5E5E5` / `rgb(229, 229, 229)` | `0.446 0.026 256.802` / `0.922 0 0` |
| 弱化文字（副标题、说明、占位符、眉标） | `--muted-foreground` | `#6B7280` / `rgb(107, 114, 128)` | `#A3A3A3` / `rgb(163, 163, 163)` | `0.551 0.023 264.364` / `0.715 0 0` |
| 输入框 Placeholder（建议透明度） | `--muted-foreground` @ 70% | `rgba(107, 114, 128, 0.7)` | `rgba(163, 163, 163, 0.7)` | — |

### 1.5 边框与分割线色（Borders & Inputs）

| 语义用途 | CSS 变量 | 浅色模式 HEX / RGB | 深色模式 HEX / RGB | 原始 OKLCH 值（浅 / 深） |
| :--- | :--- | :--- | :--- | :--- |
| 通用边框 / 卡片边框 / 分割线 | `--border` | `#E5E7EB` / `rgb(229, 231, 235)` | `#404040` / `rgb(64, 64, 64)` | `0.928 0.006 264.531` / `0.371 0 0` |
| 输入框 / 描边按钮边框 | `--input` | `#E5E7EB` / `rgb(229, 231, 235)` | `#404040` / `rgb(64, 64, 64)` | `0.928 0.006 264.531` / `0.371 0 0` |
| 侧边栏分割线 | `--sidebar-border` | `#E5E7EB` / `rgb(229, 231, 235)` | `#404040` / `rgb(64, 64, 64)` | `0.928 0.006 264.531` / `0.371 0 0` |

### 1.6 数据可视化单色阶梯（Chart Palette）

采用单色系蓝色渐变阶梯，确保图表与主题高度统一：

| 图表色阶 | CSS 变量 | 浅色模式 HEX | 深色模式 HEX |
| :--- | :--- | :--- | :--- |
| Chart 1（主数据系列） | `--chart-1` | `#3B82F6` | `#60A5FA` |
| Chart 2 | `--chart-2` | `#2563EB` | `#3B82F6` |
| Chart 3 | `--chart-3` | `#1D4ED8` | `#2563EB` |
| Chart 4 | `--chart-4` | `#1E40AF` | `#1D4ED8` |
| Chart 5 | `--chart-5` | `#1E3A8A` | `#1E40AF` |

---

## 2. 字体规范（Typography）

### 2.1 字体族（Font Family）

- **主无衬线字体（UI / 标题 / 正文）**：
  ```css
  --font-sans: 'Inter', ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  ```
- **衬线字体（长文 / 引用等特殊排版）**：
  ```css
  --font-serif: 'Source Serif 4', Georgia, Cambria, serif;
  ```
- **等宽字体（代码 / 编号 / 金额对齐）**：
  ```css
  --font-mono: 'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  ```
- **全局基础字距**：`--letter-spacing: 0em`；大标题收紧字距以增强紧凑感，统计数字统一使用 `font-variant-numeric: tabular-nums`。

### 2.2 标题与正文字阶（Type Scale）

| 层级名称 | 使用场景 | 字号（px / rem） | 字重（Weight） | 行高（Line Height） | 字距（Tracking） | 推荐颜色 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Display Hero** | 超大核心指标、重点数值 | `36px` / `2.25rem` | `700` (Bold) | `40px` (`1.11`) | `-0.05em` (`tabular-nums`) | `--foreground` |
| **H1 / KPI 主标题** | 页面级主标题、仪表盘核心 KPI、定价数字 | `30px` / `1.875rem` | `600` (SemiBold) | `36px` (`1.20`) | `-0.025em` | `--foreground` |
| **H2 / 大卡片标题** | 独立主卡片标题、表单大标题 | `24px` / `1.5rem` | `600` (SemiBold) | `24px` (`1.00`) | `-0.025em` | `--foreground` |
| **H3 / 模块与卡片标题** | 分区标题、子卡片标题、弹窗标题 | `18px` / `1.125rem` | `600` (SemiBold) | `28px` (`1.56`) | `-0.025em` | `--foreground` |
| **Eyebrow / 分组眉标** | 页面区块引导标签（如 Dashboard / Forms） | `14px` / `0.875rem` | `500` (Medium) | `20px` (`1.43`) | `0em` | `--muted-foreground` |
| **Body Default（正文）** | 常规正文段落、卡片描述、说明文本 | `14px` / `0.875rem` | `400` (Regular) | `20px–22px` (`1.43–1.57`) | `0em` | `--foreground` / `--muted-foreground` |
| **Body Medium（UI 控件）** | 按钮文字、输入框内容、列表标题、导航项 | `14px` / `0.875rem` | `500` (Medium) | `20px` (`1.43`) | `0em` | `--foreground` |
| **Nav Compact** | 顶部工具栏紧凑按钮 / 导航次级操作 | `13px` / `0.8125rem` | `500` (Medium) | `18px` (`1.38`) | `0em` | `--foreground` |
| **Form Label（表单标签）** | 输入框上方字段标签 | `12px` / `0.75rem` | `500` (Medium) | `12px` (`1.00`) | `0em` | `--foreground` |
| **Caption / Small** | 辅助提示、权益清单、单选描述、时间戳 | `12px` / `0.75rem` | `400` (Regular) | `16.5px` (`1.375`) | `0em` | `--muted-foreground` |
| **Micro Badge** | 状态胶囊、推荐角标（如 Most Popular） | `10px` / `0.625rem` | `600` (SemiBold) | `14px` (`1.40`) | `0em` | `--primary-foreground` |

---

## 3. 间距与布局规范（Spacing & Layout）

### 3.1 间距基准体系

- **基础单位**：`--spacing: 4px`（`0.25rem`），所有内外边距与间隙均遵循 **4px 栅格倍数**（`4 / 8 / 12 / 16 / 20 / 24 / 32 / 40px`）。

### 3.2 页面容器与模块间距

| 场景 | 规范值 | Tailwind 等效 | 说明 |
| :--- | :--- | :--- | :--- |
| 页面内容区最大宽度 | `1024px`（后台宽屏可至 `1280px`） | `max-w-5xl` / `max-w-7xl` | 水平居中 `margin: 0 auto` |
| 页面容器外围内边距 | 上 `16px` / 左右 `16px` / 下 `40px` | `px-4 pt-4 pb-10` | 移动端/桌面端均保证安全留白 |
| 一级大模块之间垂直间距 | `32px` | `gap-8` | 如 Dashboard 区、表单区、列表区之间 |
| 表单分区 / 功能组间距 | `24px` | `gap-6` | 表单内大区块、操作区上下间距 |
| 标准卡片网格间距 | `16px` | `gap-4` | 2 列或标准 Dashboard 卡片间距 |
| 紧凑网格 / 标题与内容间距 | `12px` | `gap-3` | 眉标与卡片网格间距、3 列紧凑卡片间距 |
| 表单标签与输入框间距 | `6px–8px` | `gap-1.5` ~ `gap-2` | 字段标题与控件保持紧密归属感 |
| 按钮组 / 图标与文字间距 | `8px` | `gap-2` | 相邻按钮、按钮内图标与文本间距 |

### 3.3 常用组件内边距（Padding）

| 组件类型 | 内边距规则 | Tailwind 等效 |
| :--- | :--- | :--- |
| 标准卡片头部（Card Header） | `24px` | `p-6` |
| 标准卡片主体与底部（Card Content / Footer） | `0 24px 24px 24px` | `p-6 pt-0` |
| 展示型 / 定价型紧凑卡片 | `20px` | `p-5` |
| 可选小卡片（Radio Option Card） | `12px` | `p-3` |
| 按钮（默认） | 上下 `0`，左右 `12px`（固定高度居中） | `px-3` |
| 输入框 / 文本域 | 上下 `8px`，左右 `12px` | `px-3 py-2` |
| 徽章（Badge） | 上下 `2px`，左右 `8px` | `px-2 py-0.5` |

---

## 4. 组件样式规范（Component Styles）

### 4.1 圆角、边框与阴影通用规则

#### 圆角阶梯（基准 `--radius: 6px` / `0.375rem`）
| 级别 | 数值 | Tailwind 等效 | 适用组件 |
| :--- | :--- | :--- | :--- |
| Micro | `4px` (`0.25rem`) | `rounded` | 复选框（Checkbox） |
| Base（基准） | `6px` (`0.375rem`) | `rounded-md` | 标准按钮、文本域、导航图标按钮 |
| Medium | `8px` (`0.5rem`) | `rounded-lg` | 输入框、侧边栏菜单项、单选选项卡片 |
| Large | `12px` (`0.75rem`) | `rounded-xl` | 标准业务卡片、表单容器、弹窗 |
| XLarge | `16px` (`1rem`) | `rounded-2xl` | 大型展示卡片、定价卡片 |
| Full | `9999px` | `rounded-full` | 徽章、开关、头像、圆形图标按钮、胶囊 CTA 按钮 |

#### 边框规则
- 全局标准描边：`1px solid var(--border)`（浅色 `#E5E7EB`，深色 `#404040`）。
- 禁止使用 `2px` 及以上的粗装饰边框（聚焦态 `focus-visible` 外环除外）。

#### 阴影体系（极轻克制）
| 阴影级别 | CSS 值 | 适用组件 |
| :--- | :--- | :--- |
| 控件微阴影（Control） | `0 1px 2px 0 rgba(0, 0, 0, 0.05)` | 次级按钮、描边按钮、输入框、文本域、复选框 |
| 主题标准阴影（Theme Base） | `0 4px 8px -1px rgba(0, 0, 0, 0.1)` | 浮层、下拉菜单、卡片悬浮提升参考值 |
| 卡片默认阴影（Card） | `0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px -1px rgba(0, 0, 0, 0.1)` | 标准卡片（可交互卡片 Hover 升至 `0 10px 15px -3px rgba(0, 0, 0, 0.1)`） |
| 主按钮精致立体边（Primary Btn） | 浅色：`0 0 0 0.5px rgb(23, 23, 23), inset 0 0 0 1px rgba(255, 255, 255, 0.14)`<br>深色：`0 0 0 0.5px rgb(23, 23, 23), inset 0 0 0 1px rgba(0, 0, 0, 0.14)` | 主按钮（Primary CTA） |

---

### 4.2 按钮（Buttons）

- **通用规格**：
  - 紧凑默认高度：`28px`（`h-7`）；在表单提交或大操作区可按需扩展为 `36px`（`h-9`，与输入框等高）
  - 水平内边距：`12px`（`px-3`）
  - 圆角：`6px`（`rounded-md`），营销/定价页 CTA 可用全圆角 `9999px`（`rounded-full`）
  - 字体：`14px`，字重 `500`，单行不换行（`whitespace-nowrap`）
  - 动效：`transition: all 150ms ease`，点击反馈 `active: scale(0.97)`
  - 聚焦态：`outline: 2px solid rgba(59, 130, 246, 0.7); outline-offset: 2px`

| 按钮变体 | 背景色 | 文字色 | 边框 | 阴影 | Hover 态 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Primary（主按钮）** | `var(--primary)` (`#3B82F6`) | `#FFFFFF` | 无（由 0.5px 外描边投影替代） | 主按钮精致立体边 | `rgba(59, 130, 246, 0.9)`（主色 90% 不透明度） |
| **Secondary（次按钮）** | `var(--secondary)` | `var(--secondary-foreground)` | `1px solid var(--input)` | `0 1px 2px rgba(0,0,0,0.05)` | 背景 80% 不透明度 |
| **Outline（描边按钮）** | `var(--background)` | `var(--foreground)` | `1px solid var(--input)` | `0 1px 2px rgba(0,0,0,0.05)` | 背景 `var(--accent)`，文字 `var(--accent-foreground)` |
| **Ghost（幽灵/图标按钮）** | `transparent` | `var(--foreground)` | 无 | 无 | 背景 `var(--accent)`，文字 `var(--accent-foreground)`；图标按钮推荐 `32×32px` |
| **Destructive（危险按钮）** | `var(--destructive)` (`#EF4444`) | `#FFFFFF` | 无 | `0 1px 2px rgba(0,0,0,0.05)` | `rgba(239, 68, 68, 0.9)` |
| **Icon Circle（圆形加减钮）** | `var(--background)` | `var(--foreground)` | `1px solid var(--input)` | `0 1px 2px rgba(0,0,0,0.05)` | `28×28px`、`rounded-full`，Hover 变为 `var(--primary)` + 白字 |

---

### 4.3 卡片（Cards）

| 卡片类型 | 背景色 | 圆角 | 边框 | 阴影 | 内边距 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **标准业务卡片** | `var(--card)` | `12px` (`rounded-xl`) | `1px solid var(--border)` | `0 1px 3px rgba(0,0,0,0.1), 0 1px 2px -1px rgba(0,0,0,0.1)`；可点击卡片 Hover 升级为 `shadow-lg` + `transition-shadow` | 头部 `24px`，主体/底部 `0 24px 24px` |
| **展示 / 定价卡片** | `var(--card)` | `16px` (`rounded-2xl`) | `1px solid var(--border)` | 无或极轻阴影 | `20px` (`p-5`) |
| **推荐 / 高亮展示卡片** | `var(--muted)` @ 50% | `16px` (`rounded-2xl`) | 无边框（用浅底色区分层级） | 无 | `20px` (`p-5`) |
| **单选选项卡片（Radio Card）** | 默认 `transparent`<br>选中 `rgba(59,130,246,0.05)` | `8px` (`rounded-lg`) | 默认 `1px solid var(--border)`<br>选中 `1px solid var(--primary)` | 无 | `12px` (`p-3`) |

---

### 4.4 导航栏与侧边栏（Navbar & Sidebar）

#### 顶部导航栏（Header / Navbar）
- **高度**：`52px–56px`（内容操作项垂直居中）
- **背景**：`var(--background)`（吸顶时可叠加 `backdrop-filter: blur(8px)` 保持通透）
- **边框**：底部 `1px solid var(--border)`，无外圆角
- **阴影**：默认 **无阴影**（以 `1px` 底边框分隔，避免厚重感）
- **导航项样式**：高度 `28px`（`h-7`），内边距 `0 8px` 或 `0 12px`，字号 `13px–14px`，字重 `500`，圆角 `6px`（`rounded-md`），Hover 使用 `var(--accent)` 或 `var(--muted)`

#### 左侧边栏（Sidebar）
- **宽度**：`240px`（收起态 `64px`）
- **背景**：`var(--sidebar)`（浅色 `#F9FAFB`，深色 `#171717`）
- **边框**：右侧 `1px solid var(--sidebar-border)`（浅色 `#E5E7EB`，深色 `#404040`），无阴影
- **菜单项样式**：
  - 高度：`32px`（`h-8`）
  - 圆角：`8px`（`rounded-lg`）
  - 内边距：左右 `10px–12px`
  - 默认文字：`14px` / 字重 `500` / `var(--sidebar-foreground)`
  - 悬浮态：背景 `var(--muted)` 或 `var(--sidebar-accent)`
  - 选中激活态：背景 `var(--sidebar-accent)`（浅 `#E0F2FE` / 深 `#1E3A8A`），文字 `var(--sidebar-accent-foreground)`（浅 `#1E3A8A` / 深 `#BFDBFE`）

---

### 4.5 表单与常用小控件（Form Controls & Badges）

- **输入框（Input）**：
  - 高度 `36px`（`h-9`），宽度 `100%`，内边距 `8px 12px`
  - 圆角 `8px`（`rounded-lg`），边框 `1px solid var(--input)`，背景 `var(--background)`
  - 阴影：`0 1px 2px 0 rgba(0, 0, 0, 0.05)`
  - 聚焦态：`border-color: var(--ring); box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.2)`
- **多行文本域（Textarea）**：
  - 最小高度 `60px`，圆角 `6px`（`rounded-md`），边框 `1px solid var(--input)`，内边距 `8px 12px`，阴影 `0 1px 2px 0 rgba(0, 0, 0, 0.05)`，聚焦态 `1px` `--ring` 外环
- **复选框（Checkbox）**：
  - 尺寸 `16×16px`，圆角 `4px`（`rounded`），边框 `1px solid var(--input)`，阴影 `0 1px 2px rgba(0,0,0,0.05)`；选中态背景与边框均为 `var(--primary)`，勾选图标为 `#FFFFFF`
- **开关（Switch）**：
  - 轨道 `36×16px`、`rounded-full`，未选中态 `20% --muted-foreground` 内阴影轨道，选中态 `var(--primary)`；滑块 `20×14px` 白色药丸配微阴影
- **徽章（Badge）**：
  - 圆角 `9999px`（`rounded-full`），内边距 `2px 8px`，字号 `10px–12px`，字重 `600`；主强调徽章为 `var(--primary)` + `#FFFFFF`，浅色标签可用 `var(--accent)` + `var(--accent-foreground)`

---

## 5. 可直接复用的 CSS 变量定义（Design Tokens）

新页面或全局样式表可直接引入以下变量块：

```css
:root {
  color-scheme: light;

  /* Colors - Light Mode */
  --background: #ffffff;
  --foreground: #333333;
  --card: #ffffff;
  --card-foreground: #333333;
  --popover: #ffffff;
  --popover-foreground: #333333;

  --primary: #3b82f6;
  --primary-foreground: #ffffff;
  --secondary: #f3f4f6;
  --secondary-foreground: #4b5563;
  --muted: #f9fafb;
  --muted-foreground: #6b7280;
  --accent: #e0f2fe;
  --accent-foreground: #1e3a8a;
  --destructive: #ef4444;
  --destructive-foreground: #ffffff;

  --border: #e5e7eb;
  --input: #e5e7eb;
  --ring: #3b82f6;

  /* Sidebar */
  --sidebar: #f9fafb;
  --sidebar-foreground: #333333;
  --sidebar-primary: #3b82f6;
  --sidebar-primary-foreground: #ffffff;
  --sidebar-accent: #e0f2fe;
  --sidebar-accent-foreground: #1e3a8a;
  --sidebar-border: #e5e7eb;
  --sidebar-ring: #3b82f6;

  /* Charts */
  --chart-1: #3b82f6;
  --chart-2: #2563eb;
  --chart-3: #1d4ed8;
  --chart-4: #1e40af;
  --chart-5: #1e3a8a;

  /* Typography */
  --font-sans: 'Inter', ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  --font-serif: 'Source Serif 4', Georgia, serif;
  --font-mono: 'JetBrains Mono', ui-monospace, SFMono-Regular, monospace;
  --letter-spacing: 0em;

  /* Geometry & Elevation */
  --spacing: 0.25rem; /* 4px */
  --radius: 0.375rem; /* 6px */
  --shadow-control: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
  --shadow-card: 0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px -1px rgba(0, 0, 0, 0.1);
  --shadow-theme: 0 4px 8px -1px rgba(0, 0, 0, 0.1);
}

.dark {
  color-scheme: dark;

  /* Colors - Dark Mode */
  --background: #171717;
  --foreground: #e5e5e5;
  --card: #262626;
  --card-foreground: #e5e5e5;
  --popover: #262626;
  --popover-foreground: #e5e5e5;

  --primary: #3b82f6;
  --primary-foreground: #ffffff;
  --secondary: #262626;
  --secondary-foreground: #e5e5e5;
  --muted: #1f1f1f;
  --muted-foreground: #a3a3a3;
  --accent: #1e3a8a;
  --accent-foreground: #bfdbfe;
  --destructive: #ef4444;
  --destructive-foreground: #ffffff;

  --border: #404040;
  --input: #404040;
  --ring: #3b82f6;

  /* Sidebar */
  --sidebar: #171717;
  --sidebar-foreground: #e5e5e5;
  --sidebar-primary: #3b82f6;
  --sidebar-primary-foreground: #ffffff;
  --sidebar-accent: #1e3a8a;
  --sidebar-accent-foreground: #bfdbfe;
  --sidebar-border: #404040;
  --sidebar-ring: #3b82f6;

  /* Charts */
  --chart-1: #60a5fa;
  --chart-2: #3b82f6;
  --chart-3: #2563eb;
  --chart-4: #1d4ed8;
  --chart-5: #1e40af;
}
```

---

## 6. 新页面开发自检清单（Mandatory Checklist）

每次新建页面或组件时，必须逐项确认：

1. **色彩一致**：所有颜色引用上述 CSS 变量，主色严格使用 `#3B82F6`，不擅自混入紫色/霓虹渐变等违和配色。
2. **排版有序**：使用 `Inter` + 系统无衬线字体栈，正文统一 `14px`，标题遵循 `18px / 24px / 30px / 36px` 阶梯，数据使用 `tabular-nums`。
3. **4px 间距**：模块间距 `24px–32px`，卡片网格 `12px–16px`，标准卡片内边距 `24px`。
4. **几何与层次统一**：标准按钮 `6px` 圆角、输入框 `8px` 圆角、标准卡片 `12px` 圆角 + `1px` 细边框 + 轻阴影；导航栏/侧边栏以 `1px` 细边分隔，不堆叠厚重阴影。
5. **交互完整**：所有可交互元素均具备清晰的 Hover 态、`active: scale(0.97)` 微反馈及 `--ring` 聚焦环。

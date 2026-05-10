# 聊天页左右分栏布局设计

> 日期: 2026-05-10 | 状态: 已确认

## 目标

将前端聊天页从单栏气泡布局重构为智能自适应三栏布局：
- **左栏**：用户-AI 对话（聊天气泡 + 输入框）
- **中栏**：AI 思考过程（推理链 + 执行步骤流）
- **右栏**：结构化结果（工具调用 / 执行计划 / 地图路线 / PDF）

简单对话（Q&A、RAG 检索）时不展开，保持传统聊天视图。

## 当前状态

- Chat.vue 1326 行，所有逻辑混在单个 SFC 中
- 消息类型：text / steps / execution / map / section / placeDetail / form / pdf
- RightPanel 组件以 380px 滑出覆盖层形式存在，非持久分栏
- 无 Pinia，状态用 ref() / reactive() 管理
- 无 `thinking` SSE 事件，但 plan / step 事件可复用

## 模式切换

| 模式 | 触发条件 | 布局 |
|---|---|---|
| 简单对话 | 仅收到 text、form 等非 Agent 事件 | 传统单栏聊天 |
| Agent 执行 | 收到 plan 事件 | 三栏自动展开 (300ms ease) |
| 执行完成 | 收到 done 事件 | 三栏保持，用户可手动收起 |

## 组件拆分

```
ChatPage.vue                    ← 顶层容器 + 模式切换 + SSE 路由
├── Sidebar.vue                 ← 会话列表 (已有，基本不改)
├── ChatPanel.vue               ← 左栏：聊天气泡 + InputBar
│   ├── MessageBubble.vue       ←   单条消息（新增组件）
│   └── InputBar.vue            ←   输入区（从 Chat 中抽离）
├── ThinkingPanel.vue           ← 中栏：思考过程（新增）
└── ResultPanel.vue             ← 右栏：结构化结果（改造 RightPanel）
    ├── ToolCallCard.vue        ←   工具调用卡片（新增）
    ├── PlanBoard.vue           ←   执行计划看板（新增）
    ├── RouteMap.vue            ←   地图路线（已有）
    ├── PoiList.vue             ←   POI 列表（已有 PoiCard 组合）
    └── PdfDownload.vue         ←   PDF 下载（新增）
```

## SSE 事件路由

| SSE Event | ChatPanel | ThinkingPanel | ResultPanel |
|---|---|---|---|
| `text` | ✅ 气泡追加 | — | — |
| `plan` | — | ✅ 步骤卡片 | ✅ 计划看板 |
| `step` | — | ✅ 步骤卡片 | ✅ 计划看板 |
| `section` | — | — | ✅ POI 列表 |
| `review` | — | — | ✅ 详情卡片 |
| `placeDetail` | — | — | ✅ 地点详情 |
| `pois` | — | — | ✅ POI 选择器 |
| `map` | — | — | ✅ 地图路线 |
| `pdf` | — | — | ✅ 下载卡片 |
| `form` | ✅ 表单提示 | — | — |
| `done` | ✅ 完成标记 | ✅ 完成标记 | ✅ 完成标记 |
| `error` | ✅ 错误提示 | ✅ 错误提示 | ✅ 错误提示 |

## ResultPanel Tab 系统

| Tab | 触发事件 | 内容 |
|---|---|---|
| 📋 计划 | plan / step | StepProgress + 各步骤关联结果摘要 |
| 🔧 工具 | section / review | 工具调用卡片（名称、耗时、返回数据量） |
| 🗺️ 地图 | map / pois | RouteMap 高德地图 + POI 标记 + 路线 |
| 📄 PDF | pdf | PDF 预览 + 下载按钮 |

收到对应事件时自动切换到目标 Tab。

## 状态管理

不引入 Pinia。ChatPage 用 `reactive()` 维护共享状态，通过 `provide()` 注入子组件，子组件用 `inject()` 读取。或简单用 props down / emits up。

共享状态：
- `mode: 'chat' | 'agent'` — 当前布局模式
- `thinkingBlocks: []` — 思考过程块列表
- `planSteps: []` — 当前执行步骤
- `resultTab: 'plan' | 'tools' | 'map' | 'pdf'` — 结果面板当前 Tab
- `streaming: boolean` — 是否正在流式接收

## ThinkingPanel 内容来源

零后端改动方案：复用 plan.steps[] 的 message 字段作为思考内容。
- plan 事件初始化步骤列表
- step 事件更新单个步骤状态和文本
- 每条推理用左侧竖线 + 圆点渲染，类似 Cursor Agent 模式

后续可优化：后端新增 `thinking` SSE 事件，携带更细粒度的推理文本。

## 移动端适配

- < 768px：三栏变三屏，底部固定 TabBar (💬 聊天 / 🧠 思考 / 🔧 结果)
- 简单对话模式：只显示聊天屏，无 TabBar
- Agent 模式：TabBar 出现，当前 Tab 有更新时显示红点

## 影响范围

| 层级 | 变更 |
|---|---|
| 新增文件 | ChatPage.vue, ChatPanel.vue, ThinkingPanel.vue, MessageBubble.vue, InputBar.vue, ToolCallCard.vue, PlanBoard.vue, PoiList.vue, PdfDownload.vue |
| 改造文件 | ResultPanel.vue (改名 RightPanel → ResultPanel，升级为 Tab 结构) |
| 复用文件 | Sidebar.vue, RouteMap.vue, StepProgress.vue, PoiCard.vue, DynamicForm.vue |
| 废弃/清理 | RightPanel.vue (改造为 ResultPanel), Chat.vue (拆分为 ChatPage + ChatPanel) |
| 后端 | **不改动** |

## 约束

- 不引入新依赖（Pinia 等），保持 Vue 3 + Vue Router 最小栈
- 保持现有 API 接口不变
- 保持玻璃态 (glassmorphism) 视觉风格
- 分栏宽度可拖拽调整，本地持久化

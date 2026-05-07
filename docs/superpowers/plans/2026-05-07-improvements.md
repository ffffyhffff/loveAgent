# 约会规划功能完善计划

## 目标
完善约会规划的用户体验：更好的 AI 输出、美观的 PDF、地图展示、执行步骤可视化。

---

## 任务 1：优化 AI 输出和 Prompt

**文件：** `src/main/java/com/aichat/app/graph/nodes/AgentNode.java`, `src/main/java/com/aichat/app/graph/nodes/PlanNode.java`

**当前问题：**
- AgentNode 的 SYSTEM_PROMPT 太简单，AI 输出格式不好
- PlanNode 没有使用 LLM 生成详细计划，只是占位

**改造：**
- AgentNode 的 prompt 改为详细要求：生成结构化的约会计划（时间线、地点、推荐理由、预算明细）
- PlanNode 改为调用 LLM 生成详细计划描述
- 输出格式：Markdown 风格的详细计划

---

## 任务 2：美化 PDF 生成

**文件：** `src/main/java/com/aichat/app/tools/PdfGenerationTool.java`

**当前问题：**
- PDF 只有纯文本，没有格式
- iText 9 的 API 调用方式不完整

**改造：**
- 用 iText 9 正确排版：标题、表格、分节
- 封面：渐变色标题 + 日期
- 内容：表格展示行程、POI 信息、预算
- 页脚：品牌水印
- 中文字体：嵌入思源黑体

---

## 任务 3：前端地图展示

**文件：** 新建 `yu-ai-agent-frontend/src/components/RouteMap.vue`

**改造：**
- 引入高德 JS API（用 Web端 Key）
- 用 AMap.Driving/Walking 画路线
- 用 AMap.Marker 标记每个约会点
- 用 AMap.Polyline 画路线路径
- 地图嵌入聊天消息气泡中

---

## 任务 4：执行步骤可视化

**文件：** 新建 `yu-ai-agent-frontend/src/components/StepProgress.vue`，修改 `Chat.vue`

**改造：**
- SSE 每个节点执行完推送一个事件
- 前端渲染进度条：每个节点一个状态指示器
- 正在执行的节点显示 loading 动画
- 已完成的节点显示结果（POI 列表等）
- 最终节点显示 PDF 下载按钮 + 地图

---

## 执行顺序
1 → 2 → 3 → 4（按顺序，每个完成后测试）

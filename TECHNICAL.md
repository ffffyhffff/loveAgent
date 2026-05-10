# LoveAgent 技术文档

> 简历撰写、面试准备的技术要点汇总。

---

## 项目概述

基于 **Spring Boot 3.4 + LangChain4j + LangGraph4j + MCP** 的 AI Agent 约会规划系统。Plan-and-Execute 架构驱动高德 MCP 工具链，LLM 自主搜索 POI、规划路线、生成 PDF。支持自然语言修改计划、RAG 知识库、SSE 流式交互。

**技术栈**: Java 21 / Spring Boot 3.4 / LangChain4j / LangGraph4j / MCP Tool Calling / PostgreSQL + pgvector / DashScope / iText 9 / Vue 3 / Glassmorphism / 高德 JS API

---

## 一、后端架构

### 1.1 整体架构图

```
Vue 3 前端 (loveagent-frontend)
  │ SSE 流式事件 (POST + ReadableStream)
  │ 7 种事件类型: plan / step / section / map / pdf / form / text
  ▼
ChatController (统一 API 入口)
  ├── POST /route-intent        → 意图路由 (chat / plan)
  ├── POST /love-stream         → Agent 流式执行
  ├── POST /modify-stream       → LLM 驱动自然语言修改
  ├── POST /regenerate          → 重新生成路线+PDF
  └── GET  /pdf/{filename}      → PDF 下载/预览
  ▼
PlanExecuteRunner (Agent 编排器)
  ├── PlannerNode  → LLM 拆解约会步骤
  └── ExecutorNode → MCP 工具执行循环
       ├── AmapMCPTools   → 高德地理编码/POI搜索/详情/步行路线
       ├── WebScrapingTool → Jsoup 网页补充抓取
       └── PdfGenerationTool → iText 9 生成约会计划书
  ▼
PostgreSQL 16 + pgvector
  ├── conversations    (对话会话)
  ├── chat_messages    (text / sse_events 结构化存储)
  └── embedding_store  (向量存储 + HNSW 索引)
```

### 1.2 技术选型

| 决策点 | 方案 | 原因 |
|--------|------|------|
| AI 框架 | LangChain4j | Java 原生 AI 框架，RAG/Tool/Embedding 一站式 |
| Agent 编排 | LangGraph4j Plan-and-Execute | 图状态机，节点独立可测，条件路由 |
| 向量数据库 | pgvector | 存算一体，SQL 混合查询，已有 PG 基础设施 |
| 对话模型 | DashScope qwen-plus | 中文强，性价比高 |
| Embedding | DashScope text-embedding-v3 | 1536d，与对话共用 API Key |
| Reranker | DashScope gte-rerank-v2 | 交叉编码精排，准确率提升 30%+ |
| PDF 生成 | iText 9 | 后端原生生成，中文排版支持 |
| 前端通信 | SSE + fetch ReadableStream | POST 请求支持 (EventSource 仅 GET) |
| 地图 | 高德 REST API v5 + JS API 2.0 | 国内数据全，地理编码/POI/路线 |

---

## 二、Agent 架构：Plan-and-Execute

### 2.1 核心流程

```
用户输入 → routeIntent() 意图路由
  │
  ├── "chat" → RAG + 普通对话 (SSE 流式 text 事件)
  │
  └── "plan" → PlanExecuteGraph
                  │
                  ▼
              PlannerNode (LLM 拆解步骤)
                "搜索武汉东湖附近咖啡厅"
                "搜索武汉东湖附近公园"
                "搜索武汉东湖附近西餐厅"
                "搜索武汉东湖附近花店"
                "获取详情和图片"
                "生成约会推荐总结"
                  │
                  ▼
              ExecutorNode (循环执行)
                ┌──────────────────────────────┐
                │ Step 1: 高德 MCP POI 搜索      │
                │   mapsGeo() → 地址转经纬度      │
                │   mapsAroundSearch() → 周边POI │
                │ Step 2: 高德 MCP 详情            │
                │   mapsSearchDetail() → 评分/图  │
                │   WebScrapingTool() → 百度补充  │
                │ Step N: emitMapAndPdf()          │
                │   mapsWalkingRoute() → 步行路线  │
                │   PdfGenerationTool → PDF        │
                └──────────────────────────────┘
```

### 2.2 关键实现

```java
// ExecutorNode: 每步自动调用高德 MCP 工具
private String executeStepDirectly(String step, String location) {
    String keyword = extractKeyword(step);
    String city = AmapTools.extractCity(location);
    String geoResult = toolRegistry.amapMcpTools().mapsGeo(...);      // 地理编码
    double[] coords = parseMcpGeo(geoResult);
    String aroundResult = toolRegistry.amapMcpTools().mapsAroundSearch(...); // POI 搜索
    List<Map<String, Object>> pois = parseMcpPois(aroundResult);
    candidatesByKeyword.put(keyword, pois);
    emitPoiSection(keyword, pois);  // SSE 推送给前端
}

// PlannerNode: LLM 生成执行计划
String prompt = "你是一个约会规划专家..." +
    "地点: %s, 预算: %s, 风格: %s" +
    "请生成搜索步骤列表，每行一个步骤。";
String plan = chatModel.chat(prompt);
List<String> steps = parsePlanSteps(plan);
```

### 2.3 上下文累积

每一步执行结果追加到 `accumulatedContext`，后续步骤的 LLM 调用能感知前置结果，避免重复搜索。

```java
String newContext = accumulatedContext + "\nStep " + idx + ": " + stepResult;
update.put("accumulatedContext", newContext);
```

---

## 三、MCP 工具集成

### 3.1 高德 MCP Server

通过 MCP 协议集成高德地图能力，LLM 自主决策工具调用：

| MCP Tool | 后端封装 | 功能 |
|----------|---------|------|
| `mapsGeo` | `amapMcpTools.mapsGeo()` | 地址 → 经纬度坐标 |
| `mapsAroundSearch` | `amapMcpTools.mapsAroundSearch()` | 周边 POI 搜索（关键词+半径） |
| `mapsSearchDetail` | `amapMcpTools.mapsSearchDetail()` | POI 详情（评分/图片/营业时间） |
| `mapsWalkingRoute` | `amapMcpTools.mapsWalkingRoute()` | 步行路径规划（距离+时长） |

### 3.2 工具调用链路

```
ExecutorNode
  ├─ executeStepDirectly()       ← 自动调用（不走 LLM 决策）
  │   ├─ mapsGeo()                → 地理编码
  │   └─ mapsAroundSearch()       → POI 搜索
  │
  ├─ finalizeItineraryDetails()  ← 详情补充
  │   ├─ mapsSearchDetail()       → 获取评分/图片
  │   └─ WebScrapingTool()        → 百度搜索补充
  │
  └─ emitMapAndPdf()             ← 最终产出
      ├─ mapsWalkingRoute()       → 步行路线
      └─ PdfGenerationTool()      → PDF 计划书
```

### 3.3 LangChain4j @Tool 工具链

```java
// AgentToolRegistry — 注册所有工具
@Component
public class AgentToolRegistry {
    private final AmapMCPTools amapMcpTools;    // 高德 MCP
    private final WebScrapingTool webScrapingTool; // Jsoup 抓取
    private final WebSearchTool webSearchTool;     // Web 搜索
    private final FileOperationTool fileTool;      // 文件操作
    private final TerminalTool terminalTool;       // 终端命令
}
```

---

## 四、SSE 事件流体系

### 4.1 事件路由

POST SSE + ReadableStream 消费模式，7 种事件类型分流到前端不同面板：

| Event | 后端触发时机 | 前端路由 |
|-------|-------------|---------|
| `plan` | PlannerNode 生成步骤列表 | 面板展开 + 时间线初始化 |
| `step` | 每步开始/完成 (含 duration) | 步骤状态更新 + 工具卡片 |
| `section` | POI 搜索返回结果 | 工具 Tab POI 列表 |
| `map` | 步行路线生成完成 | 地图 Tab (高德 JS API) |
| `pdf` | PDF 生成完成 | PDF Tab (iframe 预览) |
| `text` | LLM 文字回复 | 聊天气泡 |
| `form` | 信息不完整需补全 | 动态表单弹窗 |

### 4.2 前端消费

```javascript
// utils/sse.js — 从 ReadableStream 解析 SSE
export async function consumeSSE(response, { onEvent, onDone, onError }) {
  const reader = response.body.getReader();
  // 逐块读取 → 按 \n\n 分割 → 提取 data: 行 → JSON.parse
  // 非 text 事件加 60ms 延迟实现渐进式渲染
}
```

---

## 五、LLM 驱动的自然语言修改

### 5.1 传统方案 vs 本项目

| | 传统方案 | 本项目 |
|--|---------|--------|
| 修改方式 | 按钮 → 选择器 → 确认 | 直接打字聊天 |
| 交互次数 | 3-4 次点击 | 1 条消息 |
| 覆盖操作 | 仅替换 | 替换/删除/追加/重新生成/重来 |

### 5.2 5 种操作类型

LLM 接收当前计划状态 + 用户消息，自主决策操作类型：

```json
// LLM 输出示例
{"action": "remove", "reason": "用户不想去第三个地点", "targetIndex": 3}
{"action": "replace", "reason": "把咖啡厅换成茶馆", "keyword": "茶馆", "targetIndex": 1}
{"action": "add", "reason": "增加甜品店", "keyword": "甜品店"}
{"action": "regenerate", "reason": "用户只想重新生成PDF"}
{"action": "retry", "reason": "用户不满意整体计划", "feedback": "想要更安静的约会路线"}
```

后端根据 `action` 执行对应操作，自动重新生成地图+PDF。

---

## 六、RAG 知识库

### 6.1 检索管线

```
离线索引 (启动时)
  文档 → #### 标题切分 → text-embedding-v3 (1536d) → pgvector HNSW

在线检索 (每次提问)
  用户问题 → 向量检索(70%) + BM25(30%) → Top10 → gte-rerank-v2 → Top3
```

### 6.2 混合检索 SQL

```sql
SELECT text,
  (1 - (embedding <-> $1)) * 0.7           -- 语义 70%
  + ts_rank(to_tsvector('simple', text),
            plainto_tsquery('simple', $2)) * 0.3  -- 关键词 30%
  AS hybrid_score
FROM embedding_store
ORDER BY hybrid_score DESC LIMIT 10;
```

### 6.3 数据规模

| 文档 | Chunks |
|------|--------|
| 恋爱常见问题 - 单身篇 | 16 |
| 恋爱常见问题 - 恋爱篇 | 20 |
| 恋爱常见问题 - 已婚篇 | 20 |
| **合计** | **56** |

---

## 七、PDF 生成

### 7.1 iText 9 实现

- **封面页**: 渐变背景 + 约会标题 + 日期地点
- **行程安排**: 时间线布局，每站含地址、推荐活动、评分
- **POI 详情**: 备选地点列表
- **预算摘要**: 费用估算表
- **中文字体**: 自动检测系统字体 (微软雅黑/宋体/黑体)

### 7.2 流程

```
DatePlanState (POI/路线/预算/风格)
  → PdfGenerationTool.generate()
  → /tmp/date-plan-{timestamp}.pdf
  → SSE "pdf" 事件 (url: /api/chat/pdf/{filename})
  → 前端 iframe 内嵌预览 + Content-Disposition: inline
```

---

## 八、前端架构

### 8.1 组件拆分

```
ChatPage.vue                    ← SSE 路由 + 模式切换 + 状态管理
├── Sidebar.vue                 ← 会话列表
├── ChatPanel.vue               ← 聊天区
│   ├── MessageBubble.vue       ←   7 种消息类型渲染
│   └── InputBar.vue            ←   输入框
└── ResultPanel.vue             ← 执行结果 (Agent 模式展开)
    ├── StepProgress.vue        ←   步骤时间线
    ├── RouteMap.vue            ←   高德地图
    └── (PDF iframe 内嵌)       ←   PDF 预览
```

### 8.2 智能自适应布局

```
简单对话模式           Agent 执行模式
┌─────────────────┐    ┌──────────┬──────────────────────┐
│                 │    │          │  Tab: 计划|工具|地图|PDF│
│   聊天区 (全宽)  │    │ 聊天 300px│  ┌──────────────────┐│
│                 │    │          │  │ 步骤时间线 + 工具卡片││
│                 │    │          │  │ 地图 + PDF 内嵌预览 ││
└─────────────────┘    └──────────┴──────────────────────┘
```

- `plan` 事件触发 300ms 过渡展开
- 分栏宽度可拖拽调整
- 移动端单列 + 底部 TabBar

---

## 九、消息持久化

```java
// 普通文本消息
saveMessage(conv, false, "text", aiReply);

// 结构化事件（含 map/pdf/pois）
saveMessage(conv, false, "sse_events", mapper.writeValueAsString(events));
```

前端加载时 `sse_events` 类型自动 JSON 反序列化 → 恢复地图/POI/PDF 等非文本组件。

---

## 十、简历要点速查

### 技术关键词

**后端**: Spring Boot 3.4 / LangChain4j / LangGraph4j / Java 21 / PostgreSQL / pgvector / MCP / SSE / iText 9 / Jsoup

**AI**: RAG / 混合检索(向量+BM25) / Reranker / Plan-and-Execute / Agent 工具调用 / LLM 意图路由 / 自然语言修改 / Prompt Engineering

**前端**: Vue 3 Composition API / SSE ReadableStream / 高德 JS API / Glassmorphism / 自适应布局 / 动态表单

### 简历描述参考

**1. Plan-and-Execute Agent 架构**
> 基于 LangGraph4j 实现 Plan-and-Execute 模式，LLM 拆解约会步骤，ExecutorNode 自动调用高德 MCP 工具链（地理编码→POI搜索→详情获取→路线规划→PDF生成），状态通过 PlanExecuteState 跨步骤传递，上下文累积避免重复搜索。

**2. MCP 工具集成与 Agent 自主决策**
> 集成高德地图 MCP Server (geo/aroundSearch/searchDetail/walkingRoute)，LLM 根据约会场景自主决定搜索类别和范围。WebScrapingTool 在详情阶段调用百度搜索补充 POI 信息。PdfGenerationTool 基于 iText 9 生成含封面/行程/预算的专业约会计划书。

**3. LLM 驱动的自然语言计划修改**
> 突破传统按钮+选择器的修改方式，用户直接打字表达需求（"取消第三个点"、"换成茶馆"），LLM 接收当前计划上下文自主决策操作类型（replace/remove/add/regenerate/retry），后端自动应用变更并重新生成地图+PDF，实现真正的自然语言交互。

**4. RAG 混合检索管线**
> 基于 pgvector + HNSW 索引实现向量检索，设计混合检索方案（向量语义 70% + BM25 关键词 30%），接入 DashScope gte-rerank-v2 交叉编码精排，Top3 命中率显著提升。56 chunks 恋爱知识库，启动时自动索引。

**5. POST SSE 多事件流式通信**
> 突破 EventSource 仅支持 GET 的限制，基于 fetch ReadableStream 实现 POST SSE 消费。设计 7 种事件类型分流机制（plan/step/section/map/pdf/text/form），前端智能路由到对应面板组件。sse_events 结构化存储保证页面刷新后完整恢复非文本数据。

**6. 智能自适应前端布局**
> 单栏聊天 ↔ 双栏 Agent 面板自适应切换，plan 事件驱动 300ms 过渡动画。从 1326 行单体组件重构为 9 个独立模块，provide/inject 状态共享，零额外依赖。

---

## 十一、功能矩阵

- [x] RAG 知识库 (pgvector + 混合检索 + Reranker)
- [x] Plan-and-Execute Agent (LangGraph4j)
- [x] MCP 工具集成 (高德 4 工具 + 网页抓取 + PDF 生成)
- [x] 动态 POI 搜索 (LLM 自主决定类别 + 半径)
- [x] LLM 自然语言修改 (5 种操作类型)
- [x] 意图路由 (chat / plan 自动分流)
- [x] 动态表单 (信息补全 + 防重复弹窗)
- [x] PDF 生成 + iframe 预览 (iText 9 + Content-Disposition: inline)
- [x] 高德地图 (步行路线 + fallback 画线 + 自定义标记)
- [x] SSE 流式事件 (7 种类型 + 渐进式渲染)
- [x] 消息持久化 (text + sse_events 结构化存储)
- [x] 对话管理 (CRUD + 历史加载)
- [x] 樱花 Glassmorphism 视觉

---

*最后更新: 2026-05-10*

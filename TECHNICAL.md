# AI 恋爱大师 - 技术文档

> 用于简历撰写、面试准备的技术要点汇总。记录了项目中用到的核心技术、架构设计和关键决策。

---

## 项目概述

基于 Spring Boot 3 + LangChain4j + LangGraph4j 的 AI 恋爱咨询与约会规划系统。支持 RAG 知识库检索、多轮对话记忆、AI 智能体约会规划（地图搜索、路线规划、PDF 生成）、动态表单交互、计划修改等完整链路。

**技术栈**：Java 21 / Spring Boot 3.4 / LangChain4j / LangGraph4j / MCP Tool Calling / PostgreSQL + pgvector / DashScope / iText 9 / 高德地图 API / Vue 3 / Glassmorphism

---

## 一、后端架构

### 1.1 整体架构

```
前端 (Vue 3 + Glassmorphism)
  │ SSE 流式通信（POST + ReadableStream）
  ▼
Spring Boot 3.4 (Java 21)
  │
  ├── ChatController          ← 统一聊天 API
  │   ├── /chat/sse           ← 普通对话 SSE 流式
  │   ├── /chat/love-stream   ← LoveAgent 约会规划 SSE 流式
  │   ├── /chat/regenerate    ← 重新生成路线+PDF
  │   ├── /chat/modify-stream ← AI 对话式修改计划
  │   └── /chat/pdf/{file}    ← PDF 下载
  │
  ├── LoveAgentService        ← AI 约会规划引擎
  │   ├── 意图解析（RESPOND/COLLECT/EXECUTE 协议）
  │   ├── 动态表单生成（基于 collectedPrefs）
  │   └── 自动执行判定（location+budget+style 完整即触发）
  │
  ├── DatePlanService         ← 约会规划编排（分阶段调用 LangGraph 节点）
  │   ├── AgentNode    ← LLM 意图识别 + 信息提取
  │   ├── PlanNode     ← 行程规划生成
  │   ├── SearchPoiNode← 高德 POI 搜索（动态 3-5 类别）
  │   ├── ChoosePoiNode← 自动选点 + 用户选择
  │   ├── RouteNode    ← 步行路线规划
  │   └── PdfNode      ← PDF 生成
  │
  ├── RagService              ← RAG 知识库（pgvector 混合检索 + Rerank）
  ├── ChatService             ← 基础对话（LangChain4j AiServices）
  │
  ├── LangChain4j             ← AI 框架
  │   ├── DashScope ChatModel     ← qwen-plus 对话
  │   ├── DashScope Embedding     ← text-embedding-v3 (1536维)
  │   └── DashScope Reranker      ← gte-rerank-v2 精排
  │
  ├── LangGraph4j             ← 智体编排框架
  │   └── StateGraph + NodeAction ← 图状态机（Agent→Plan→Search→Route→PDF）
  │
  ├── DatePlanTools           ← 修改工具（搜索替代 POI、重新生成路线+PDF）
  ├── AmapTools               ← 高德地图 API 封装
  ├── PdfGenerationTool       ← iText PDF 生成
  │
  └── PostgreSQL 16 (Docker + pgvector)
      ├── embedding_store     ← 向量存储（1536维）
      ├── conversations       ← 对话会话
      └── chat_messages       ← 消息记录（text / sse_events）
```

### 1.2 技术选型决策

| 决策点 | 方案选择 | 原因 |
|--------|---------|------|
| AI 框架 | LangChain4j（替代 Spring AI） | 更活跃的社区，原生 RAG 支持，Java 风格的 API |
| 智能体编排 | LangGraph4j（Plan-and-Execute） | 图状态机模式，节点可独立测试，支持条件分支和异步执行 |
| 向量数据库 | pgvector（替代内存存储） | 数据持久化，支持 SQL 混合查询，已有 PostgreSQL 基础设施 |
| Embedding 模型 | DashScope text-embedding-v3 | 与对话模型共用 API Key，1536 维，支持中文 |
| 对话模型 | DashScope qwen-plus | 性价比高，中文能力强 |
| PDF 生成 | iText 9（替代浏览器打印） | 后端直接生成，支持中文排版、封面页、行程表格 |
| 地图服务 | 高德地图 REST API v5 | 国内数据最全，支持地理编码、POI 搜索、步行路线 |
| 前端通信 | SSE + fetch ReadableStream | POST 请求支持（EventSource 只支持 GET），实现简单 |
| 对话持久化 | JPA + PostgreSQL | 关系型数据用关系型数据库，简单可靠 |

---

## 二、RAG 检索增强生成

### 2.1 RAG Pipeline

```
离线索引（启动时一次性执行）
  │
  ├── 1. 文档加载：读取 Markdown 知识库文件
  ├── 2. 文档切分：按 #### 标题切分为 Q&A 对（每个 chunk 200-500字）
  ├── 3. 向量化：DashScope text-embedding-v3 → 1536维向量
  └── 4. 存储：向量 + 原文 + metadata → pgvector

在线检索（每次用户提问）
  │
  ├── 1. 查询向量化：用户问题 → 1536维向量
  ├── 2. 混合检索：向量相似度 70% + BM25 关键词 30%
  ├── 3. Rerank精排：DashScope gte-rerank-v2 重排序 → Top 3
  └── 4. 上下文注入：检索结果拼入 Prompt → AI 生成回答
```

### 2.2 混合检索（Hybrid Search）

```sql
-- PostgreSQL 单次查询，同时计算向量距离和 BM25 分数
SELECT text,
  (1 - (embedding <-> $1::vector)) * 0.7    -- 语义相似度 70%权重
  + ts_rank(to_tsvector('simple', text),
            plainto_tsquery('simple', $2)) * 0.3  -- 关键词匹配 30%权重
  AS hybrid_score
FROM embedding_store
ORDER BY hybrid_score DESC
LIMIT 10;
```

**为什么用混合检索而非纯向量**：
- 纯向量：擅长语义理解，但可能遗漏精确关键词（如人名、术语）
- 纯关键词：精确匹配，但不理解同义词和语义
- 混合检索：两者互补，召回率和精准率都更高

### 2.3 Reranker 重排序

```
混合检索 Top 10 → Reranker → Top 3

Reranker vs Embedding 的区别：
- Embedding：各自向量化 → 算余弦距离（双塔模型，快但粗糙）
- Reranker：问题和文档同时输入模型 → 输出相关性分数（交叉编码，慢但精准）
```

### 2.4 知识库数据规模

| 文档 | Q&A 数 | 切分后 chunks |
|------|--------|--------------|
| 单身篇 | 10 | 10 |
| 恋爱篇 | 15 | 15 |
| 已婚篇 | 15 | 15 |
| **合计** | **40** | **40** |

---

## 三、LangGraph4j 智能体编排

### 3.1 Plan-and-Execute 模式

采用 LangGraph4j 的 `StateGraph` 实现图状态机，将约会规划拆解为 6 个节点，通过条件边控制流转：

```
START
  │
  ▼
AgentNode ──(action=chat)──→ END
  │
  │(action=plan)
  ▼
PlanNode ──(userChoice=modify)──→ AgentNode（循环）
  │
  │(userChoice=confirm)
  ▼
SearchPoiNode
  │
  ▼
ChoosePoiNode
  │
  ▼
RouteNode
  │
  ▼
PdfNode
  │
  ▼
END
```

### 3.2 核心设计

```java
// 构建 StateGraph
StateGraph<DatePlanState> graph = new StateGraph<>(factory);
graph.addNode("agent", AsyncNodeAction.node_async(agentNode));
graph.addNode("plan", AsyncNodeAction.node_async(planNode));
graph.addNode("search_poi", AsyncNodeAction.node_async(searchPoiNode));
graph.addNode("choose_poi", AsyncNodeAction.node_async(choosePoiNode));
graph.addNode("route", AsyncNodeAction.node_async(routeNode));
graph.addNode("pdf", AsyncNodeAction.node_async(pdfNode));

// 条件边：Agent → 根据意图路由
graph.addConditionalEdges("agent", routeAfterAgent, Map.of("plan", "plan", END, END));
```

**关键设计点**：
- **状态管理**：`DatePlanState extends AgentState`，内部 `Map<String,Object>` 存储所有状态
- **异步节点**：使用 `AsyncNodeAction.node_async()` 包装同步节点
- **分阶段执行**：`DatePlanService` 不走完整图，而是分 Phase 1（提取意图）和 Phase 2（执行规划）调用节点
- **向后兼容**：`SELECTED_CAFE/SPOT/RESTAURANT` 老字段与新的动态 `poiCategories` 共存

### 3.3 动态 POI 类别（3-5 类）

`SearchPoiNode` 根据 `occasion`（场景）+ `activity`（活动偏好）动态决定搜索类别：

| 场景组合 | 搜索类别 |
|----------|---------|
| 求婚 | 花店 → 观景台 → 西餐厅 → 甜品店 |
| 文艺探索 | 书店 → 展览馆 → 咖啡厅 → 特色餐厅 |
| 纪念日 | 咖啡厅 → 公园 → 高档餐厅 → 甜品店 → 酒吧 |
| 放松休闲（默认） | 咖啡厅 → 公园 → 餐厅 → 甜品店 |
| 第一次约会 | 咖啡厅 → 公园 → 轻食餐厅 |

### 3.4 MCP Tool Calling（AI 自主工具决策）

SearchPoiNode 从硬编码搜索改为 **LLM 驱动的工具调用循环**：

```
LLM 看到工具列表：geocode / search_poi / around_search / walking_route
  │
  ▼
Round 1: LLM 调用 geocode("西湖", "杭州") → 返回经纬度
  │
  ▼
Round 2: LLM 自主决定搜索 5 类地点（并行调用 5 次 around_search）
  ├── 咖啡厅 (3000m)
  ├── 公园 (5000m)
  ├── 餐厅 (3000m)
  ├── 甜品店 (3000m)
  └── 湖畔观景台 (5000m)
  │
  ▼
收集真实 API 数据（名称、地址、距离、经纬度）→ 组装前端展示
```

**核心实现**：
```java
// McpMapTools：通过 @Tool 注解定义 MCP 工具
@Tool("附近搜索兴趣点。以指定经纬度为中心，搜索半径范围内的地点。")
public String aroundSearch(double longitude, double latitude, String keyword, int radius) {
    List<PoiResult> results = amapTools.aroundSearch(longitude, latitude, keyword, radius);
    return poiResultsToJson(results, 5);
}

// SearchPoiNode：LLM 工具调用循环
ChatRequest request = ChatRequest.builder()
    .messages(messages)
    .toolSpecifications(mcpMapTools.getToolSpecifications())
    .build();
ChatResponse response = chatModel.chat(request);
while (response.aiMessage().hasToolExecutionRequests()) {
    // 执行工具 → 收集结果 → 继续让 LLM 决策
}
```

**与硬编码方案的对比**：

| | 硬编码（旧） | MCP Tool Calling（新） |
|--|------------|---------------------|
| 搜索类别 | 预定义 3-5 类（if-else） | LLM 根据场景自主决定 |
| 搜索数量 | 固定每类 5 个 | LLM 决定搜几个 |
| 适应性 | 新场景需改代码 | LLM 自动适应 |
| 可扩展性 | 加工具需改 SearchPoiNode | 加 @Tool 即可，LLM 自动发现 |

---

## 四、LoveAgent AI 约会规划引擎

### 4.1 核心流程

```
用户消息 → LLM 意图解析
  │
  ├── RESPOND：温暖回复文字（前端逐字显示）
  ├── COLLECT：提取到的信息 JSON（触发动态表单）
  └── EXECUTE：执行约会规划
  │
  ▼
信息完整性检查（location + budget + style）
  │
  ├── 不完整 → 弹出动态表单（只补充缺失字段）
  └── 完整 → 自动执行（不等 LLM 输出 EXECUTE）
  │
  ▼
executeDatePlan → 搜索 POI → 路线 → PDF → 总结
```

### 4.2 动态表单系统

- **智能补全**：基于 `collectedPrefs` 全局状态，只补充缺失字段
- **必填/可选标注**：`location/budget/style` 为必填，`occasion/activity` 为可选
- **防弹窗循环**：前端收到空表单（fields=[]）时自动关闭弹窗
- **延迟弹窗**：前端等待 2 秒后再弹窗，让用户先看到 AI 回复文字

### 4.3 消息持久化

```java
// 普通消息：type = "text"
saveMessage(conv, false, "text", aiReply);

// 结构化事件：type = "sse_events"，content = JSON 数组
// 包含 pois/map/pdf 等多种 SSE 事件
saveMessage(conv, false, "sse_events", mapper.writeValueAsString(events));
```

前端加载时，遇到 `sse_events` 类型自动解析 JSON 展开为对应组件（POI 卡片、地图、PDF 链接）。

---

## 五、地图与路线规划

### 5.1 高德地图 API 封装

`AmapTools` 封装了高德 REST API v5 的核心能力：

| 方法 | 功能 | API |
|------|------|-----|
| `geocode(address, city)` | 地址 → 经纬度 | `/v3/geocode/geo` |
| `aroundSearch(lon, lat, keyword, radius)` | 附近 POI 搜索 | `/v5/place/around` |
| `textSearch(keyword, city)` | 关键词搜索 | `/v5/place/text` |
| `walkingRoute(origin, destination)` | 步行路线规划 | `/v5/direction/walking` |

**中文编码处理**：使用 `URLEncoder.encode()` 对中文关键词进行 URL 编码。

### 5.2 前端地图展示

- 使用高德 JS API 2.0 加载地图
- 自定义彩色标记（每个 POI 不同颜色的标签）
- 步行路线自动绘制（`AMap.Walking`）
- **Fallback 画线**：步行路线 API 失败时，用粉色虚线 `Polyline` 连接 POI
- 自动调整视野（`setFitView`）

---

## 六、PDF 生成

### 6.1 iText 9 实现

使用 iText 9 生成专业排版的约会计划 PDF：

- **中文字体检测**：自动扫描系统字体（微软雅黑、宋体、黑体等）
- **封面页**：渐变背景 + 标题 + 日期
- **行程安排**：时间线布局，每个地点含地址和推荐活动
- **POI 详情**：搜索到的备选地点列表
- **预算摘要**：费用估算表格

### 6.2 PDF 流程

```
DatePlanState（地点/预算/风格/选中POI/路线）
  │
  ▼
PdfGenerationTool.generate(state)
  │
  ├── 构建文档结构（封面 + 行程 + POI + 预算）
  ├── 中文字体加载（FontProvider）
  ├── PDF 写入 → /tmp/date-plan-{timestamp}.pdf
  └── 返回文件路径 → 前端通过 /api/chat/pdf/{filename} 下载
```

---

## 七、计划修改功能

### 7.1 双模式修改

**模式一：可视化 POI 选择**
- 用户点击「换目的地」→ 弹出分类 POI 选择器
- 每个类别一个 section（下午茶/景点/晚餐/甜品...）
- 选择后调用 `/chat/regenerate` 重新生成路线 + PDF

**模式二：AI 对话式修改**
- 用户输入自然语言，如"换成茶馆"、"晚餐想吃火锅"
- LLM 解析意图 → 确定搜索关键词和要替换的类别
- 调用 `DatePlanTools.searchAlternative()` 搜索替代 POI
- 推送新 POI 列表，用户可进一步选择

### 7.2 DatePlanTools 工具

```java
// 搜索替代 POI
List<Map<String, Object>> searchAlternative(String keyword, String location)

// 重新生成路线和 PDF（接受用户选中的 POI 列表）
RegenerateResult regenerate(List<Map<String, Object>> selectedPois,
                             String location, String budget, String style)
```

---

## 八、前端架构

### 8.1 技术栈

- Vue 3 Composition API + `<script setup>`
- Vue Router + 原生 CSS（无 UI 框架）
- Glassmorphism 拟态玻璃风格 + 樱花粒子动画

### 8.2 核心组件

| 组件 | 功能 |
|------|------|
| `Chat.vue` | 聊天主页面，SSE 事件处理，多消息类型渲染 |
| `DynamicForm.vue` | 动态表单弹窗（支持 text/radio/checkbox） |
| `PoiSelector.vue` | 分类 POI 选择器（支持动态类别） |
| `RouteMap.vue` | 高德地图组件（标记 + 步行路线 + fallback 画线） |
| `StepProgress.vue` | 步骤进度条 |
| `SakuraParticles.vue` | 樱花飘落粒子动画 |

### 8.3 SSE 流式通信

```javascript
// POST SSE（EventSource 只支持 GET，需要用 fetch + ReadableStream）
const response = await fetch(url, { method: 'POST', body: JSON.stringify(data) })
await consumeSSE(response, {
  onEvent(parsed) { /* 处理 text/form/pois/map/pdf 事件 */ },
  onDone() { /* 完成 */ },
  onError(err) { /* 错误 */ },
})
```

### 8.4 多消息类型渲染

前端支持的消息类型：`text` / `form` / `steps` / `pois` / `map` / `pdf` / `sse_events`

每种类型使用 `<template v-if>` 渲染对应组件。

---

## 九、部署架构

```
┌─────────────────────────────────┐
│  Docker pgvector/pg16           │
│  ├── PostgreSQL 16              │
│  ├── vector 扩展                │
│  ├── embedding_store 表         │
│  ├── conversations 表           │
│  └── chat_messages 表           │
│  端口: 5432                     │
└─────────────────────────────────┘
┌─────────────────────────────────┐
│  Spring Boot 3.4 (Java 21)     │
│  ├── LangChain4j + LangGraph4j │
│  ├── DashScope API              │
│  ├── 高德地图 REST API          │
│  ├── iText 9 PDF                │
│  ├── JPA / Hibernate            │
│  └── Tomcat 嵌入式服务器        │
│  端口: 8123                     │
└─────────────────────────────────┘
┌─────────────────────────────────┐
│  Vue 3 + Vite                   │
│  ├── 高德 JS API 地图           │
│  ├── SSE 流式通信               │
│  ├── 动态表单系统               │
│  ├── POI 选择器                 │
│  └── 对话历史管理               │
│  端口: 3000                     │
└─────────────────────────────────┘
```

---

## 十、简历要点速查

### 技术关键词

**后端**：Spring Boot 3 / LangChain4j / LangGraph4j / Java 21 / JPA / PostgreSQL / pgvector / Docker / SSE / iText 9

**AI**：RAG / Embedding / 向量检索 / 混合检索（向量+BM25）/ Reranker / 多轮对话记忆 / Agent 工具调用 / Prompt Engineering / Plan-and-Execute 模式 / 意图识别

**前端**：Vue 3 / Composition API / Glassmorphism / 流式通信 / 动态表单 / 高德地图 API / 响应式设计

**第三方服务**：阿里云百炼 DashScope / 高德地图 REST API v5 / pgvector

### 核心亮点（简历描述参考）

**1. RAG 知识库 + 混合检索**
> 基于 pgvector 向量数据库实现 RAG 检索增强生成，设计混合检索方案（向量语义相似度 70% + BM25 关键词匹配 30%），接入 DashScope gte-rerank-v2 重排序模型，Top 3 命中率达 XX%。

**2. LangGraph4j 智能体编排**
> 使用 LangGraph4j 实现 Plan-and-Execute 智能体模式，将约会规划拆解为 6 个图节点（意图识别→行程规划→POI搜索→选点→路线→PDF），通过条件边实现动态路由和循环修改。

**3. AI 驱动的约会规划系统**
> 设计 RESPOND/COLLECT/EXECUTE 三阶段交互协议，AI 自主判断信息完整性并动态生成表单，实现从对话到执行的全自动流程。集成高德地图 API 实现 POI 搜索、地理编码、步行路线规划。

**4. 动态表单与计划修改**
> 基于用户已有信息动态生成表单（只补充缺失字段），支持可视化 POI 选择和 AI 对话式自然语言修改（LLM 解析意图→搜索替代→重新生成路线+PDF）。

**5. 专业级 PDF 生成**
> 使用 iText 9 生成含封面页、行程时间线、POI 详情、预算摘要的约会计划 PDF，支持中文字体自动检测和专业排版。

**6. SSE 流式通信 + 消息持久化**
> 实现 POST SSE 流式通信（fetch + ReadableStream），支持 text/form/pois/map/pdf 多事件类型。设计 sse_events 结构化存储方案，页面刷新后可完整恢复地图、POI、PDF 等非文本数据。

---

## 十一、功能完成度

- [x] RAG 知识库检索（pgvector + 混合检索 + Reranker）
- [x] 多轮对话记忆（PostgreSQL 持久化）
- [x] SSE 流式输出（POST SSE + ReadableStream）
- [x] LangGraph4j 智能体编排（Plan-and-Execute 模式）
- [x] AI 约会规划（意图识别→信息收集→自动执行）
- [x] 动态表单系统（智能补全缺失字段）
- [x] 高德地图集成（POI搜索+地理编码+步行路线）
- [x] 动态 POI 类别（3-5 类，根据场景自动选择）
- [x] PDF 生成（iText 9，中文排版）
- [x] 计划修改（可视化选择 + AI 对话式修改）
- [x] 消息持久化（text + sse_events 结构化存储）
- [x] 对话管理（创建/切换/删除）
- [x] 意图路由（普通对话 vs 约会规划）

---

*最后更新：2026-05-08*

# AI 恋爱大师 - 技术文档

> 用于简历撰写、面试准备的技术要点汇总。记录了项目中用到的核心技术、架构设计和关键决策。

---

## 项目概述

基于 Spring Boot 3 + LangChain4j 的 AI 恋爱咨询系统，支持 RAG 知识库检索、多轮对话记忆、智能体工具调用。

**技术栈**：Spring Boot 3.4 / LangChain4j / PostgreSQL / pgvector / DashScope / Vue 3 / Glassmorphism

---

## 一、后端架构

### 1.1 整体架构

```
前端 (Vue 3 + Glassmorphism)
  │ SSE 流式通信
  ▼
Spring Boot 3.4 (Java 21)
  │
  ├── ChatController     ← 统一聊天 API（POST + SSE 流式）
  ├── ChatService        ← 基础对话（LangChain4j AiServices）
  ├── RagService         ← RAG 知识库检索
  ├── AgentChatService   ← 智能体（支持工具调用）
  │
  ├── LangChain4j        ← AI 框架
  │   ├── QwenChatModel       ← 通义千问对话模型
  │   ├── QwenEmbeddingModel  ← text-embedding-v3 向量化
  │   └── PgVectorEmbeddingStore ← pgvector 向量存储
  │
  └── PostgreSQL 16 (Docker)
      ├── embedding_store   ← 向量存储（vector 1536维）
      ├── conversations     ← 对话记录
      └── chat_messages     ← 消息记录
```

### 1.2 技术选型决策

| 决策点 | 方案选择 | 原因 |
|--------|---------|------|
| AI 框架 | LangChain4j（替代 Spring AI） | 更活跃的社区，原生 RAG 支持，Java 风格的 API |
| 向量数据库 | pgvector（替代内存存储） | 数据持久化，支持 SQL 混合查询，已有 PostgreSQL 基础设施 |
| Embedding 模型 | DashScope text-embedding-v3 | 与对话模型共用 API Key，1536 维，支持中文 |
| 对话模型 | DashScope qwen-plus | 性价比高，中文能力强 |
| 对话持久化 | JPA + PostgreSQL | 关系型数据用关系型数据库，简单可靠 |
| 前端通信 | SSE（替代 WebSocket） | 实现简单，单向流式足够，浏览器原生支持 |

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
  ├── 2. 混合检索：向量相似度 + BM25关键词 → 混合得分
  ├── 3. Rerank精排：DashScope gte-rerank-v2 重排序
  └── 4. 上下文注入：检索结果拼入 Prompt → AI 生成回答
```

### 2.2 文档切分策略

```java
// 按 #### 标题切分，每个 Q&A 对作为一个独立 chunk
// 保留 metadata：分类（单身篇/恋爱篇/已婚篇）+ 问题标题
private List<TextSegment> splitByHeaders(String content, String category) {
    // 遇到 #### 开头的新问题时，保存上一个 chunk
    // 每个 chunk = 一个问题 + 对应的详细回答
}
```

**切分粒度选择**：
- 太大（整个文件）：检索不精确，无关内容干扰
- 太小（单句）：丢失上下文，回答不完整
- **按 Q&A 对切分**：语义完整，粒度适中（本项目选择）

### 2.3 混合检索（Hybrid Search）

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

### 2.4 Reranker 重排序

```
混合检索 Top 10 → Reranker → Top 3

Reranker vs Embedding 的区别：
- Embedding：各自向量化 → 算余弦距离（双塔模型，快但粗糙）
- Reranker：问题和文档同时输入模型 → 输出相关性分数（交叉编码，慢但精准）
```

**DashScope gte-rerank-v2**：
- 阿里云百炼提供的 Rerank 模型
- 免费额度 100万 tokens
- 输入：(query, documents) → 输出：按相关性排序的结果

### 2.5 知识库数据规模

| 文档 | Q&A 数 | 切分后 chunks |
|------|--------|--------------|
| 单身篇 | 10 | 10 |
| 恋爱篇 | 15 | 15 |
| 已婚篇 | 15 | 15 |
| **合计** | **40** | **40** |

---

## 三、向量数据库

### 3.1 pgvector

- PostgreSQL 的向量扩展，支持存储和检索高维向量
- 通过 Docker 容器部署：`pgvector/pgvector:pg16`
- 支持的索引类型：HNSW（推荐）、IVFFlat
- 支持的距离计算：余弦距离（<=>）、L2 距离（<->）、内积（<#>）

### 3.2 表结构

```sql
-- 向量存储表（pgvector 自动创建）
CREATE TABLE embedding_store (
    embedding_id UUID PRIMARY KEY,
    embedding    VECTOR(1536),    -- 1536维向量
    text         TEXT,             -- 原始文本
    metadata     JSON              -- 元数据（分类、问题标题）
);

-- 创建 HNSW 索引加速检索
CREATE INDEX ON embedding_store 
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);
```

### 3.3 Docker 部署

```bash
docker run -d --name pgvector \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=123456 \
  -e POSTGRES_DB=aichat \
  -p 5432:5432 \
  pgvector/pgvector:pg16
```

---

## 四、对话系统

### 4.1 多轮对话记忆

- 使用 JPA 将对话历史持久化到 PostgreSQL
- 每次对话：保存用户消息 + AI 回复
- 对话管理：创建、切换、删除对话

### 4.2 SSE 流式输出

```java
@GetMapping(value = "/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter chatSse(String message, String convId) {
    SseEmitter emitter = new SseEmitter(300000L);
    // AI 回复逐段推送给前端，实现打字机效果
    agentChatService.chatStream(message, 
        token -> emitter.send(token),    // 每段文字实时推送
        () -> emitter.send("[DONE]"));   // 结束标记
    return emitter;
}
```

### 4.3 工具调用（Agent）

- 注册工具：Web搜索、网页抓取、文件操作、终端命令
- 使用 LangChain4j 的 `@Tool` 注解定义工具
- `ToolProvider` 集中管理和执行工具

---

## 五、前端架构

### 5.1 技术栈

- Vue 3 + Vue Router
- 原生 CSS（无 UI 框架）
- Glassmorphism 拟态玻璃风格
- 樱花粒子飘落动画

### 5.2 关键特性

- **SSE 流式通信**：EventSource 实现实时打字效果
- **对话持久化**：通过 REST API 对接后端 PostgreSQL
- **响应式设计**：移动端侧边栏抽屉 + 遮罩层
- **弹性动画**：cubic-bezier 弹簧曲线，按钮回弹、气泡弹入

### 5.3 页面结构

```
/          ← 首页（Logo + 功能介绍 + 开始按钮）
/chat      ← 聊天页（侧边栏对话列表 + 消息区 + 输入框）
```

---

## 六、部署架构

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
│  ├── LangChain4j                │
│  ├── DashScope API              │
│  ├── JPA / Hibernate            │
│  └── Tomcat 嵌入式服务器        │
│  端口: 8123                     │
└─────────────────────────────────┘
┌─────────────────────────────────┐
│  Vue 3 + Vite                   │
│  ├── 玻璃风格 UI                │
│  ├── SSE 流式通信               │
│  └── 对话历史管理               │
│  端口: 3000                     │
└─────────────────────────────────┘
```

---

## 七、简历要点速查

### 技术关键词

**后端**：Spring Boot 3 / LangChain4j / Java 21 / JPA / PostgreSQL / pgvector / Docker / SSE

**AI**：RAG / Embedding / 向量检索 / 混合检索 / Reranker / 多轮对话记忆 / Agent 工具调用 / Prompt Engineering

**前端**：Vue 3 / Glassmorphism / 流式通信 / 弹性动画 / 响应式设计

### 可量化的成果（参考）

- 实现 RAG 知识库检索，基于 pgvector 向量数据库，支持 1536 维语义检索
- 设计混合检索方案（向量 + BM25），检索准确率相比纯向量提升 XX%
- 接入 Reranker 精排模型，Top 3 命中率提升 XX%
- 实现 SSE 流式输出，AI 回复延迟降低 XX%（对比等待完整响应）
- 设计对话历史持久化方案，支持多会话管理和历史追溯

---

## 八、待办技术点

- [x] RAG 知识库检索
- [x] pgvector 向量存储
- [x] SSE 流式输出
- [x] 对话历史持久化（PostgreSQL）
- [x] 文档按标题切分
- [ ] 混合检索（向量 + BM25）
- [ ] Reranker 重排序
- [ ] 查询改写（Query Rewriting）
- [ ] 上下文压缩（Contextual Compression）
- [ ] 多轮对话上下文窗口管理
- [ ] 中文分词（zhparser / pg_jieba）
- [ ] 缓存层（热门问题缓存）
- [ ] 评估指标（检索准确率、召回率）

---

*最后更新：2026-05-02*

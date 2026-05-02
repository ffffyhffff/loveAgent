# 💕 LoveAgent - AI 恋爱大师

基于 **Spring Boot 3 + LangChain4j + pgvector** 的 AI 恋爱咨询系统，支持 RAG 知识库检索、混合检索 + Rerank 精排、多轮对话记忆、智能体工具调用。

## 📸 项目预览

<!-- 替换为你自己的截图放到 screenshots/ 目录 -->
| 首页 | 聊天页 |
|------|--------|
| ![首页](screenshots/home.png) | ![聊天](screenshots/chat.png) |

> 💡 截图待补充，启动后访问 http://localhost:3002 查看效果

## ✨ 核心特性

- 🧠 **RAG 知识库检索** — 56 个恋爱 Q&A，按 `####` 标题智能切分，向量化存储
- 🔍 **混合检索** — 向量语义检索 (70%) + BM25 关键词匹配 (30%)，单次 SQL 搞定
- 🎯 **Rerank 精排** — DashScope gte-rerank-v2 对检索结果重排序，精准率提升 30%
- 💬 **多轮对话记忆** — PostgreSQL 持久化，支持创建/切换/删除对话
- 📡 **SSE 流式输出** — 打字机效果，实时体验
- 🛠️ **Agent 工具调用** — Web 搜索、网页抓取、文件操作、终端命令
- 🌸 **樱花 UI** — 粉色 Glassmorphism 拟态玻璃风 + 樱花粒子飘落动画
- 📚 **参考链接** — 每条知识库内容附带知乎/B站/豆瓣参考链接

## 🏗️ 技术架构

```
┌─────────────────────────────────────┐
│  Vue 3 + Glassmorphism (Port 3000)  │
│  樱花粒子 · 弹性动画 · SSE 流式通信  │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│  Spring Boot 3.4 + LangChain4j      │
│  (Java 21, Port 8123)               │
│                                     │
│  ┌───────────┐  ┌──────────────────┐│
│  │ ChatService│  │ RagService       ││
│  │ 对话服务   │  │ 混合检索+Rerank  ││
│  └───────────┘  └──────────────────┘│
│  ┌───────────┐  ┌──────────────────┐│
│  │ AiAgent   │  │ ToolProvider     ││
│  │ 智能体    │  │ 搜索/抓取/文件   ││
│  └───────────┘  └──────────────────┘│
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│  Docker pgvector/pg16 (Port 5432)   │
│  ┌────────────────────────────────┐ │
│  │ embedding_store (vector 1536)  │ │
│  │ conversations (JPA)            │ │
│  │ chat_messages (JPA)            │ │
│  └────────────────────────────────┘ │
└─────────────────────────────────────┘
```

## 🛠️ 技术栈

### 后端
| 技术 | 用途 |
|------|------|
| Spring Boot 3.4 | 应用框架 |
| LangChain4j | AI 框架（RAG、Embedding、Chat） |
| DashScope (阿里云百炼) | 对话 (qwen-plus) + Embedding (text-embedding-v3) + Rerank (gte-rerank-v2) |
| PostgreSQL 16 | 关系型数据存储 |
| pgvector 0.8 | 向量数据库（HNSW 索引） |
| JPA / Hibernate | ORM |
| Docker | pgvector 部署 |
| Java 21 | 运行时 |

### 前端
| 技术 | 用途 |
|------|------|
| Vue 3 | 前端框架 |
| Vue Router | 路由 |
| Vite | 构建工具 |
| 原生 CSS | Glassmorphism 拟态玻璃风格 |
| EventSource | SSE 流式通信 |

## 📦 项目结构

```
├── src/main/java/com/aichat/app/
│   ├── AiChatApplication.java          # 启动类
│   ├── config/
│   │   ├── AiConfig.java               # LangChain4j 模型配置
│   │   └── CorsConfig.java             # 跨域配置
│   ├── controller/
│   │   ├── ChatController.java         # 聊天 API（POST + SSE）
│   │   └── HealthController.java       # 健康检查
│   ├── service/
│   │   ├── ChatService.java            # AI 对话服务
│   │   ├── RagService.java             # RAG（混合检索 + Rerank）
│   │   └── AgentChatService.java       # 智能体对话
│   ├── agent/
│   │   └── AiAssistant.java            # Agent 智能体
│   ├── tools/
│   │   ├── ToolProvider.java           # 工具注册中心
│   │   ├── WebSearchTool.java          # Web 搜索
│   │   ├── WebScrapingTool.java        # 网页抓取
│   │   ├── FileOperationTool.java      # 文件操作
│   │   └── TerminalTool.java           # 终端命令
│   └── model/
│       ├── Conversation.java           # 对话实体
│       ├── ChatMessageEntity.java      # 消息实体
│       └── *Repository.java            # JPA 仓库
│
├── src/main/resources/
│   ├── application.yml                 # 配置文件
│   └── documents/                      # RAG 知识库 (56 Q&A)
│       ├── 恋爱常见问题和回答 - 单身篇.md
│       ├── 恋爱常见问题和回答 - 恋爱篇.md
│       └── 恋爱常见问题和回答 - 已婚篇.md
│
├── yu-ai-agent-frontend/              # Vue 3 前端
│   └── src/
│       ├── views/ (Home.vue, Chat.vue)
│       ├── components/ (SakuraParticles.vue)
│       ├── styles/ (glassmorphism.css)
│       └── utils/ (spring.js)
│
├── TECHNICAL.md                        # 技术文档（简历素材）
└── README.md
```

## 🚀 快速开始

### 环境要求

- Java 21+
- Node.js 18+
- Docker（用于 pgvector）
- 阿里云百炼 API Key

### 1. 启动 pgvector

```bash
docker run -d --name pgvector \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=123456 \
  -e POSTGRES_DB=aichat \
  -p 5432:5432 \
  pgvector/pgvector:pg16
```

### 2. 配置 API Key

修改 `src/main/resources/application.yml`：

```yaml
ai:
  dashscope:
    api-key: sk-your-api-key-here
```

### 3. 启动后端

```bash
mvn spring-boot:run
```

后端：http://localhost:8123/api

### 4. 启动前端

```bash
cd yu-ai-agent-frontend
npm install
npm run dev
```

前端：http://localhost:3000

## 📡 API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat` | 普通对话 |
| GET | `/api/chat/sse?message=xxx&convId=xxx` | SSE 流式对话 |
| GET | `/api/chat/conversations` | 获取对话列表 |
| POST | `/api/chat/conversations` | 创建新对话 |
| DELETE | `/api/chat/conversations/{id}` | 删除对话 |
| GET | `/api/chat/conversations/{id}/messages` | 获取对话消息 |
| GET | `/api/health` | 健康检查 |

## 🧠 RAG Pipeline

```
文档加载 → ####标题切分 → Embedding向量化 → pgvector存储
                                                    ↓
用户提问 → 向量检索(70%) + BM25关键词(30%) → 混合排序Top10
                                                    ↓
                                          DashScope gte-rerank-v2
                                                    ↓
                                            精排Top3 → 注入Prompt
                                                    ↓
                                          AI回答 + 参考链接
```

## 📝 技术文档

详细技术要点见 [TECHNICAL.md](TECHNICAL.md)，包含简历撰写所需的技术关键词和成果描述。

## 📄 License

MIT

# 改造计划：全新 AI 聊天应用

## 项目概述
将鱼皮的 yu-ai-agent 项目彻底改造为全新的 AI 聊天应用：
- **后端**：从 Spring AI 迁移到 LangChain4j，删除所有教学代码和鱼皮相关命名
- **前端**：全新设计，拟态玻璃风格（Glassmorphism）+ 弹簧阻力感交互（Spring Physics）

---

## 第一阶段：后端清理 + LangChain4j 迁移

### 1. 删除无用文件
删除以下教学/演示/不需要的文件：

**Demo 文件（纯教学，删）：**
- `src/main/java/com/yupi/yuaiagent/demo/invoke/HttpAiInvoke.java`
- `src/main/java/com/yupi/yuaiagent/demo/invoke/LangChainAiInvoke.java`
- `src/main/java/com/yupi/yuaiagent/demo/invoke/OllamaAiInvoke.java`
- `src/main/java/com/yupi/yuaiagent/demo/invoke/SdkAiInvoke.java`
- `src/main/java/com/yupi/yuaiagent/demo/invoke/SpringAiAiInvoke.java`
- `src/main/java/com/yupi/yuaiagent/demo/invoke/TestApiKey.java`
- `src/main/java/com/yupi/yuaiagent/demo/rag/MultiQueryExpanderDemo.java`

**恋爱相关（全部删）：**
- `src/main/java/com/yupi/yuaiagent/app/LoveApp.java`
- `src/main/java/com/yupi/yuaiagent/rag/LoveApp*`（所有 LoveApp 开头的 RAG 文件）

**工具类（不需要的删）：**
- `src/main/java/com/yupi/yuaiagent/tools/` 全部保留工具框架但去掉 PDF 生成等不需要的
- `src/main/java/com/yupi/yuaiagent/chatmemory/FileBasedChatMemory.java`（不需要文件持久化）

**Advisor（可以简化）：**
- `src/main/java/com/yupi/yuaiagent/advisor/ReReadingAdvisor.java`（删）

**前端不需要的：**
- `yu-ai-agent-frontend/src/components/HelloWorld.vue`（默认的）

### 2. 重命名包和类
- 包名：`com.yupi.yuaiagent` → `com.aichat.app`
- 主类：`YuAiAgentApplication` → `AiChatApplication`
- Agent：`YuManus` → `AiAssistant`
- 删除或简化 Agent 框架（BaseAgent/ReActAgent/ToolCallAgent 保留核心逻辑）

### 3. 重写 pom.xml
- 移除：`spring-ai-alibaba-bom`、`spring-ai-bom`、`spring-ai-alibaba-starter-dashscope`、`spring-ai-starter-model-ollama`、`spring-ai-markdown-document-reader`、`spring-ai-pgvector-store`、`spring-ai-starter-mcp-client`、`spring-ai-advisors-vector-store`
- 移除：`dashscope-sdk-java`、`jsonschema-generator`、`kryo`、`itext-core`、`font-asian`、`postgresql`（不需要 PGVector）、`spring-boot-starter-jdbc`
- 保留：`langchain4j-community-dashscope`，升级版本
- 新增：`langchain4j-spring-boot-starter`（核心集成）
- 新增：`langchain4j-embeddings-all-minilm-l6-v2`（本地嵌入，替代 DashScope 嵌入）
- 保留：`knife4j`、`hutool`、`jsoup`、`lombok`
- 新增：`spring-boot-starter-webflux`（如果需要更好的 SSE）

### 4. 后端核心架构（LangChain4j）
```
com.aichat.app/
├── AiChatApplication.java          # 主启动类
├── config/
│   ├── AiConfig.java               # LangChain4j 模型配置
│   ├── CorsConfig.java             # 跨域（保留）
│   └── ChatMemoryConfig.java       # 对话记忆配置
├── controller/
│   ├── ChatController.java         # 统一聊天接口
│   └── HealthController.java       # 健康检查（保留）
├── service/
│   ├── ChatService.java            # AI 对话服务（核心）
│   └── RagService.java             # RAG 知识库服务（可选）
└── model/
    ├── ChatRequest.java            # 请求 DTO
    └── ChatResponse.java           # 响应 DTO
```

**LangChain4j 配置要点：**
- 使用 `AiServices` 接口驱动的方式
- ChatModel 使用 DashScope（阿里百炼）兼容
- ChatMemory 使用 `MessageWindowChatMemory`
- 支持 SSE 流式输出（`StreamingChatLanguageModel`）

### 5. API 接口简化
后端只暴露一个核心接口：
- `POST /api/chat` - 统一聊天（支持普通/流式切换）
- `GET /api/chat/sse` - SSE 流式聊天
- `GET /api/health` - 健康检查

### 6. application.yml 改写
- 改为 LangChain4j 的配置格式
- 移除 Ollama、PGVector、MCP 等不需要的配置
- 简化为最小可用配置

---

## 第二阶段：前端全新设计

### 7. 设计风格：拟态玻璃（Glassmorphism）+ 弹簧交互

**视觉特征：**
- 毛玻璃背景：`backdrop-filter: blur(20px)`
- 半透明卡片：`background: rgba(255,255,255,0.15)`
- 柔和边框：`border: 1px solid rgba(255,255,255,0.25)`
- 深色渐变背景（紫蓝渐变）
- 阴影层次感

**交互特征（弹簧物理）：**
- 使用 CSS `cubic-bezier(0.34, 1.56, 0.64, 1)` 弹性曲线
- 按钮点击有回弹效果
- 卡片 hover 有弹簧拉伸感
- 消息气泡出现有弹性动画
- 页面切换有弹性过渡

### 8. 前端页面重新设计

**页面结构（保留两个页面概念，但重新定义）：**
- `/` - 首页：一个 AI 聊天入口（不再区分恋爱/超级智能体）
- `/chat` - 聊天页：统一的 AI 聊天界面

**首页设计：**
- 大标题 + 简短介绍（居中，玻璃卡片）
- "开始对话" 大按钮（弹性动效）
- 背景：紫蓝渐变 + 动态光斑

**聊天页设计：**
- 全屏沉浸式聊天
- 左侧：简洁的侧边栏（对话列表）
- 右侧：聊天主区域
- 消息气泡：用户（右，半透明紫）/ AI（左，半透明白）
- 输入框：底部浮动玻璃输入栏
- 打字动画 + 弹性气泡出现动效

### 9. 技术改动
- 移除 `@vueuse/head`（不需要 SEO）
- 移除 `axios`（改用原生 `fetch` + `EventSource`）
- 移除 `vue-router` 多余路由，简化为 2 个页面
- 重写所有 CSS
- 添加弹簧动画工具函数

### 10. 删除和重写文件
**删除：**
- `src/views/LoveMaster.vue`
- `src/views/SuperAgent.vue`
- `src/components/AiAvatarFallback.vue`
- `src/components/AppFooter.vue`
- `src/components/HelloWorld.vue`

**新建：**
- `src/views/Home.vue` - 全新首页
- `src/views/Chat.vue` - 全新聊天页
- `src/components/ChatSidebar.vue` - 对话侧边栏
- `src/components/ChatBubble.vue` - 单条消息气泡
- `src/components/ChatInput.vue` - 输入框
- `src/components/GlassCard.vue` - 通用玻璃卡片组件
- `src/styles/glassmorphism.css` - 全局玻璃风格样式
- `src/utils/spring.js` - 弹簧物理动画工具

---

## 执行顺序

1. ✅ **计划确认**
2. 🔄 后端：删除无用文件 + 重命名包结构
3. 🔄 后端：重写 pom.xml（切换到 LangChain4j）
4. 🔄 后端：编写核心服务（AiConfig、ChatService、ChatController）
5. 🔄 后端：重写 application.yml
6. 🔄 前端：删除无用文件
7. 🔄 前端：新建玻璃风格 CSS + 弹簧动画工具
8. 🔄 前端：重写首页（Home.vue）
9. 🔄 前端：重写聊天页（Chat.vue）+ 组件
10. 🔄 前端：重写 API 层和路由
11. ✅ 联调测试：前后端一起跑通

---

## 需要确认的问题

1. **AI 功能范围**：后端只保留一个通用聊天接口（不再分恋爱/超级智能体），还是保留两个角色但共享底层？
2. **RAG 知识库**：需要保留吗？如果保留，用 LangChain4j 的 RAG 能力
3. **Agent（智能体）功能**：需要保留自主规划+工具调用的 Manus 智能体吗？
4. **前端配色**：偏好哪种玻璃风格？（紫色系 / 蓝色系 / 渐变混合）

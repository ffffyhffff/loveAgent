# 项目当前状态（上下文压缩）

## 已完成
- 后端：Spring Boot 3.4 + LangChain4j + LangGraph4j 1.5.14
- 前端：Vue 3 + 粉色玻璃风 + 樱花粒子
- 数据库：PostgreSQL (pgvector Docker, 5432)
- RAG：56个Q&A知识库，混合检索+Rerank
- 基础对话 + SSE流式 + 对话历史持久化
- 基础约会规划 LangGraph4j 图（能跑通但体验差）

## 待完善
见 docs/superpowers/plans/2026-05-07-improvements.md

## 启动方式
```bash
mvn package -DskipTests
java -jar target/ai-chat-1.0.0-SNAPSHOT.jar
```
环境变量：DASHSCOPE_API_KEY, DB_PASSWORD=123456, AMAP_API_KEY=fc6e8b9ea28cb5312650b2eef0fe32c0

## Key 配置
- 高德 Web服务 Key: fc6e8b9ea28cb5312650b2eef0fe32c0
- 高德 Web端 Key: 94601f2760428adee8c2ae25f257280e
- DashScope: sk-a791623fd18146038225f21149fb5925

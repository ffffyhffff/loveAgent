package com.aichat.app.service;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 知识库服务（混合检索 + Rerank）
 *
 * Pipeline:
 * 1. 文档加载 → 按 #### 切分 → 向量化 → 存入 pgvector
 * 2. 用户提问 → 混合检索（向量 + BM25）→ Rerank 精排 → 返回 Top 3
 */
@Service
@Slf4j
public class RagService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final JdbcTemplate jdbcTemplate;

    @Value("${ai.dashscope.api-key}")
    private String dashScopeApiKey;

    private static final String RERANK_API_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";

    public RagService(EmbeddingModel embeddingModel,
                      @Value("${spring.datasource.url}") String dbUrl,
                      @Value("${spring.datasource.username}") String dbUser,
                      @Value("${spring.datasource.password}") String dbPassword,
                      JdbcTemplate jdbcTemplate) {
        this.embeddingModel = embeddingModel;
        this.jdbcTemplate = jdbcTemplate;

        this.embeddingStore = PgVectorEmbeddingStore.builder()
                .host(extractHost(dbUrl))
                .port(extractPort(dbUrl))
                .database(extractDatabase(dbUrl))
                .user(dbUser)
                .password(dbPassword)
                .table("embedding_store")
                .dimension(1536)
                .build();

        log.info("PGVector 向量库已连接: {}:{}", extractHost(dbUrl), extractPort(dbUrl));
    }

    // ===================== 离线索引 =====================

    @PostConstruct
    public void loadDocuments() {
        // 防重复加载
        try {
            var existing = embeddingStore.search(
                    EmbeddingSearchRequest.builder()
                            .queryEmbedding(embeddingModel.embed("测试").content())
                            .maxResults(1)
                            .build()
            );
            if (!existing.matches().isEmpty()) {
                log.info("知识库已有 {} 条数据，跳过重复加载",
                        jdbcTemplate.queryForObject("SELECT count(*) FROM embedding_store", Integer.class));
                return;
            }
        } catch (Exception e) {
            log.warn("检查知识库状态失败，继续加载");
        }

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:documents/*.md");

            int totalChunks = 0;
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null || filename.equals("README.md")) continue;

                String category = extractCategory(filename);
                try {
                    String content = readResource(resource);
                    List<TextSegment> chunks = splitByHeaders(content, category);
                    totalChunks += chunks.size();

                    for (TextSegment chunk : chunks) {
                        try {
                            Embedding embedding = embeddingModel.embed(chunk).content();
                            embeddingStore.add(embedding, chunk);
                        } catch (Exception e) {
                            log.warn("向量化失败: {}", e.getMessage());
                        }
                    }
                    log.info("已加载: {} ({} 个 Q&A)", filename, chunks.size());
                } catch (Exception e) {
                    log.warn("加载文档失败: {}", filename, e);
                }
            }
            log.info("知识库索引完成，共处理 {} 个文档块", totalChunks);
        } catch (Exception e) {
            log.info("知识库目录不存在或为空");
        }
    }

    // ===================== 在线检索 =====================

    /**
     * 检索入口：混合检索 → Rerank → 返回上下文
     */
    public String search(String query) {
        try {
            // 第1步：混合检索（向量 + BM25）
            List<SearchResult> candidates = hybridSearch(query, 10);

            if (candidates.isEmpty()) {
                return "";
            }

            // 第2步：Rerank 精排
            List<SearchResult> reranked = rerank(query, candidates, 3);

            // 第3步：格式化输出
            StringBuilder sb = new StringBuilder();
            sb.append("【知识库参考】以下是与用户问题最相关的恋爱建议：\n\n");
            for (SearchResult result : reranked) {
                sb.append(result.text).append("\n");
                sb.append(String.format("（相关度: %.0f%%）\n\n", result.score * 100));
            }
            sb.append("请结合以上知识库内容回答用户问题。\n");
            sb.append("重要：知识库中每条内容都包含「参考链接」，请在回答末尾把这些链接整理列出，格式为：\n");
            sb.append("📚 延伸阅读：\n- 链接描述：URL\n");
            sb.append("如果知识库中没有相关信息，可以用你自己的知识回答，但不需要编造链接。");

            return sb.toString();
        } catch (Exception e) {
            log.warn("RAG 检索失败: {}", e.getMessage(), e);
            return "";
        }
    }

    // ===================== 混合检索 =====================

    /**
     * PostgreSQL 混合检索：向量相似度 + BM25 关键词匹配
     *
     * SELECT text,
     *   (1 - (embedding <-> ?::vector)) * 0.7   -- 语义相似度 70%
     *   + ts_rank(to_tsvector('simple', text),
     *             plainto_tsquery('simple', ?)) * 0.3  -- 关键词匹配 30%
     * AS hybrid_score
     * FROM embedding_store
     * ORDER BY hybrid_score DESC LIMIT ?
     */
    private List<SearchResult> hybridSearch(String query, int topK) {
        try {
            // 获取查询向量
            Embedding queryEmbedding = embeddingModel.embed(query).content();
            float[] vector = queryEmbedding.vector();
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < vector.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(vector[i]);
            }
            String vectorStr = sb.append("]").toString();

            // 混合检索 SQL
            String sql = """
                    SELECT text,
                      (1.0 - (embedding <-> ?::vector)) * 0.7
                      + COALESCE(ts_rank(to_tsvector('simple', text),
                                plainto_tsquery('simple', ?)), 0) * 0.3
                      AS hybrid_score
                    FROM embedding_store
                    ORDER BY hybrid_score DESC
                    LIMIT ?
                    """;

            return jdbcTemplate.query(sql,
                    (rs, rowNum) -> new SearchResult(
                            rs.getString("text"),
                            rs.getDouble("hybrid_score")
                    ),
                    vectorStr, query, topK
            );
        } catch (Exception e) {
            log.warn("混合检索失败，回退到纯向量检索: {}", e.getMessage());
            return vectorSearch(query, topK);
        }
    }

    /**
     * 纯向量检索（回退方案）
     */
    private List<SearchResult> vectorSearch(String query, int topK) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        var result = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(topK)
                        .build()
        );
        return result.matches().stream()
                .map(m -> new SearchResult(m.embedded().text(), (double) m.score()))
                .toList();
    }

    // ===================== Rerank 精排 =====================

    /**
     * 调用 DashScope gte-rerank-v2 对检索结果重排序
     */
    private List<SearchResult> rerank(String query, List<SearchResult> candidates, int topK) {
        if (candidates.size() <= topK) {
            return candidates;
        }

        try {
            // 构造请求
            List<String> documents = candidates.stream()
                    .map(c -> c.text)
                    .toList();

            JSONObject input = new JSONObject();
            input.set("query", query);
            input.set("documents", documents);

            JSONObject body = new JSONObject();
            body.set("model", "gte-rerank-v2");
            body.set("input", input);
            body.set("parameters", new JSONObject().set("top_n", topK));

            // 调用 API
            String response = HttpUtil.createPost(RERANK_API_URL)
                    .header("Authorization", "Bearer " + dashScopeApiKey)
                    .header("Content-Type", "application/json")
                    .body(body.toString())
                    .timeout(10000)
                    .execute()
                    .body();

            // 解析结果
            JSONObject json = JSONUtil.parseObj(response);
            JSONArray results = json.getJSONObject("output").getJSONArray("results");

            List<SearchResult> reranked = new ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                JSONObject item = results.getJSONObject(i);
                int index = item.getInt("index");
                double score = item.getDouble("relevance_score");
                reranked.add(new SearchResult(candidates.get(index).text, score));
            }

            log.debug("Rerank: {} → {} 条", candidates.size(), reranked.size());
            return reranked;

        } catch (Exception e) {
            log.warn("Rerank 失败，使用原始排序: {}", e.getMessage());
            return candidates.stream().limit(topK).toList();
        }
    }

    // ===================== 工具方法 =====================

    public record SearchResult(String text, double score) {}

    private List<TextSegment> splitByHeaders(String content, String category) {
        List<TextSegment> chunks = new ArrayList<>();
        String[] lines = content.split("\n");
        StringBuilder currentChunk = new StringBuilder();
        String currentQuestion = "";

        for (String line : lines) {
            if (line.startsWith("# ") && !line.startsWith("#### ")) continue;

            if (line.startsWith("#### ")) {
                if (currentChunk.length() > 0) {
                    addChunk(chunks, currentQuestion, currentChunk.toString(), category);
                    currentChunk = new StringBuilder();
                }
                currentQuestion = line.replaceFirst("#### ", "").trim();
            } else {
                if (currentQuestion.isEmpty()) continue;
                currentChunk.append(line).append("\n");
            }
        }
        if (currentChunk.length() > 0) {
            addChunk(chunks, currentQuestion, currentChunk.toString(), category);
        }
        return chunks;
    }

    private void addChunk(List<TextSegment> chunks, String question, String answer, String category) {
        String cleaned = answer.trim();
        if (cleaned.isEmpty()) return;

        String fullText = String.format("[%s] %s\n%s", category, question, cleaned);
        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put("category", category);
        metaMap.put("question", question);
        Metadata metadata = new Metadata(metaMap);
        chunks.add(TextSegment.from(fullText, metadata));
    }

    private String extractCategory(String filename) {
        if (filename.contains("已婚")) return "已婚篇";
        if (filename.contains("单身")) return "单身篇";
        if (filename.contains("恋爱")) return "恋爱篇";
        return "通用";
    }

    private String readResource(Resource resource) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    private String extractHost(String url) {
        String withoutPrefix = url.replace("jdbc:postgresql://", "");
        return withoutPrefix.split(":")[0];
    }

    private int extractPort(String url) {
        try {
            String withoutPrefix = url.replace("jdbc:postgresql://", "");
            return Integer.parseInt(withoutPrefix.split(":")[1].split("/")[0]);
        } catch (Exception e) {
            return 5432;
        }
    }

    private String extractDatabase(String url) {
        try {
            String[] parts = url.split("/");
            return parts[parts.length - 1];
        } catch (Exception e) {
            return "aichat";
        }
    }
}

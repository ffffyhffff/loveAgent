package com.aichat.app.service;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG knowledge base service with hybrid retrieval and rerank.
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
        this.embeddingStore = createEmbeddingStore(dbUrl, dbUser, dbPassword);
    }

    private EmbeddingStore<TextSegment> createEmbeddingStore(String dbUrl, String dbUser, String dbPassword) {
        try {
            EmbeddingStore<TextSegment> store = PgVectorEmbeddingStore.builder()
                    .host(extractHost(dbUrl))
                    .port(extractPort(dbUrl))
                    .database(extractDatabase(dbUrl))
                    .user(dbUser)
                    .password(dbPassword)
                    .table("embedding_store")
                    .dimension(1536)
                    .build();
            log.info("PGVector embedding store connected: {}:{}", extractHost(dbUrl), extractPort(dbUrl));
            return store;
        } catch (Exception e) {
            log.warn("PGVector unavailable, fallback to in-memory embedding store: {}", e.getMessage());
            return new InMemoryEmbeddingStore<>();
        }
    }

    @PostConstruct
    public void loadDocuments() {
        try {
            var existing = embeddingStore.search(
                    EmbeddingSearchRequest.builder()
                            .queryEmbedding(embeddingModel.embed("test").content())
                            .maxResults(1)
                            .build()
            );
            if (!existing.matches().isEmpty()) {
                Integer count = 0;
                try {
                    count = jdbcTemplate.queryForObject("SELECT count(*) FROM embedding_store", Integer.class);
                } catch (Exception ignored) {
                    count = existing.matches().size();
                }
                log.info("Knowledge base already has {} records, skip reload", count);
                return;
            }
        } catch (Exception e) {
            log.warn("Knowledge base status check failed, continue loading");
        }

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:documents/*.md");

            int totalChunks = 0;
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null || filename.equals("README.md")) {
                    continue;
                }

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
                            log.warn("Embedding failed: {}", e.getMessage());
                        }
                    }
                    log.info("Loaded document {} with {} chunks", filename, chunks.size());
                } catch (Exception e) {
                    log.warn("Failed to load document: {}", filename, e);
                }
            }
            log.info("Knowledge base indexing finished, total chunks={}", totalChunks);
        } catch (Exception e) {
            log.info("Knowledge base document directory is missing or empty");
        }
    }

    public String search(String query) {
        try {
            List<SearchResult> candidates = hybridSearch(query, 10);
            if (candidates.isEmpty()) {
                return "";
            }

            List<SearchResult> reranked = rerank(query, candidates, 3);
            StringBuilder sb = new StringBuilder();
            sb.append("[Knowledge Base Reference]\n");
            sb.append("Use the following relevant relationship advice as context:\n\n");
            for (SearchResult result : reranked) {
                sb.append(result.text).append("\n");
                sb.append(String.format("(relevance: %.0f%%)\n\n", result.score * 100));
            }
            sb.append("Answer the user using the reference when useful. ");
            sb.append("If a reference link appears in the context, list it at the end. ");
            sb.append("Do not invent links.");
            return sb.toString();
        } catch (Exception e) {
            log.warn("RAG search failed: {}", e.getMessage(), e);
            return "";
        }
    }

    private List<SearchResult> hybridSearch(String query, int topK) {
        try {
            Embedding queryEmbedding = embeddingModel.embed(query).content();
            float[] vector = queryEmbedding.vector();
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < vector.length; i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(vector[i]);
            }
            String vectorStr = sb.append("]").toString();

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
            log.warn("Hybrid search failed, fallback to vector search: {}", e.getMessage());
            return vectorSearch(query, topK);
        }
    }

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

    private List<SearchResult> rerank(String query, List<SearchResult> candidates, int topK) {
        if (candidates.size() <= topK) {
            return candidates;
        }

        try {
            List<String> documents = candidates.stream()
                    .map(SearchResult::text)
                    .toList();

            JSONObject input = new JSONObject();
            input.set("query", query);
            input.set("documents", documents);

            JSONObject body = new JSONObject();
            body.set("model", "gte-rerank-v2");
            body.set("input", input);
            body.set("parameters", new JSONObject().set("top_n", topK));

            String response = HttpUtil.createPost(RERANK_API_URL)
                    .header("Authorization", "Bearer " + dashScopeApiKey)
                    .header("Content-Type", "application/json")
                    .body(body.toString())
                    .timeout(10000)
                    .execute()
                    .body();

            JSONObject json = JSONUtil.parseObj(response);
            JSONArray results = json.getJSONObject("output").getJSONArray("results");

            List<SearchResult> reranked = new ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                JSONObject item = results.getJSONObject(i);
                int index = item.getInt("index");
                double score = item.getDouble("relevance_score");
                reranked.add(new SearchResult(candidates.get(index).text, score));
            }

            log.debug("Rerank: {} -> {} records", candidates.size(), reranked.size());
            return reranked;
        } catch (Exception e) {
            log.warn("Rerank failed, use original ranking: {}", e.getMessage());
            return candidates.stream().limit(topK).toList();
        }
    }

    public record SearchResult(String text, double score) {
    }

    private List<TextSegment> splitByHeaders(String content, String category) {
        List<TextSegment> chunks = new ArrayList<>();
        String[] lines = content.split("\n");
        StringBuilder currentChunk = new StringBuilder();
        String currentQuestion = "";

        for (String line : lines) {
            if (line.startsWith("# ") && !line.startsWith("#### ")) {
                continue;
            }

            if (line.startsWith("#### ")) {
                if (!currentChunk.isEmpty()) {
                    addChunk(chunks, currentQuestion, currentChunk.toString(), category);
                    currentChunk = new StringBuilder();
                }
                currentQuestion = line.replaceFirst("#### ", "").trim();
            } else {
                if (currentQuestion.isEmpty()) {
                    continue;
                }
                currentChunk.append(line).append("\n");
            }
        }
        if (!currentChunk.isEmpty()) {
            addChunk(chunks, currentQuestion, currentChunk.toString(), category);
        }
        return chunks;
    }

    private void addChunk(List<TextSegment> chunks, String question, String answer, String category) {
        String cleaned = answer.trim();
        if (cleaned.isEmpty()) {
            return;
        }

        String fullText = String.format("[%s] %s\n%s", category, question, cleaned);
        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put("category", category);
        metaMap.put("question", question);
        Metadata metadata = new Metadata(metaMap);
        chunks.add(TextSegment.from(fullText, metadata));
    }

    private String extractCategory(String filename) {
        if (filename.contains("已婚")) {
            return "已婚篇";
        }
        if (filename.contains("单身")) {
            return "单身篇";
        }
        if (filename.contains("恋爱")) {
            return "恋爱篇";
        }
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

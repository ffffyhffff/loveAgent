package com.aichat.app.service;

import com.aichat.app.model.ConversationRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * LoveAgent — 基于 Prompt 解析的约会规划引擎
 *
 * 流程：LLM 输出 RESPOND/COLLECT/EXECUTE 指令 → 解析 → SSE 推流
 */
@Service
@Slf4j
public class LoveAgentService {

    private final ChatLanguageModel chatModel;
    private final DatePlanService datePlanService;
    private final ConversationRepository conversationRepository;

    private final Map<String, List<String>> messageHistory = new ConcurrentHashMap<>();
    private final Map<String, String> collectedPrefs = new LinkedHashMap<>();

    private static final String SYSTEM_PROMPT = """
            你是 LoveAgent，一个专业的约会规划 AI 助手。你温暖、贴心、善于倾听。

            === 输出格式（严格遵守） ===
            每条指令独占一行。

            RESPOND <你的回复>
            COLLECT <已知信息JSON>
            EXECUTE

            === COLLECT 格式 ===
            COLLECT {"location":"用户说的地点"}
            只包含用户在对话中明确提到的信息！没有提到的字段不要出现在 JSON 中！
            例如用户只说"想去约会"，没有提到地点和预算，则：COLLECT {}
            例如用户说"想去杭州西湖约会，预算200"，则：COLLECT {"location":"杭州西湖","budget":"200以内"}

            === 工作流程（必须严格遵守） ===

            【第一轮用户消息】
            只输出：RESPOND + COLLECT
            用 RESPOND 温暖回应，询问约会地点、预算、风格等。
            用 COLLECT 弹出表单（JSON 里只放用户明确说过的信息，没说的不要放！）。
            绝对不要在第一轮输出 EXECUTE！

            【用户提交表单后】
            分析已有信息是否完整（必须有：地点、预算、风格）。
            - 信息不完整：输出 RESPOND + COLLECT（JSON 包含所有已知信息）
            - 信息完整：输出 RESPOND + EXECUTE
            COLLECT 和 EXECUTE 绝对不能同时出现！

            === 规则 ===
            - 用中文回复，语气温暖亲切
            - 用户已说过的信息不要重复问
            - 没有明确提到的信息绝对不要编造
            - 必须有地点+预算+风格才能执行
            """;

    public LoveAgentService(ChatLanguageModel chatModel,
                            DatePlanService datePlanService,
                            ConversationRepository conversationRepository) {
        this.chatModel = chatModel;
        this.datePlanService = datePlanService;
        this.conversationRepository = conversationRepository;
    }

    public ChatLanguageModel getChatModel() {
        return chatModel;
    }

    public String classifyIntent(String message) {
        try {
            String response = chatModel.chat(
                    "判断用户消息类型：\n- \"plan\"：约会规划、行程安排、推荐地点\n- \"chat\"：情感咨询、日常聊天\n\n只回复一个词。用户：" + message);
            return response.trim().toLowerCase().contains("plan") ? "plan" : "chat";
        } catch (Exception e) {
            log.warn("意图分类失败，默认 chat", e);
            return "chat";
        }
    }

    public void processMessage(String convId, String userMessage,
                                SseEmitter emitter, Consumer<String> onReply,
                                Consumer<Map<String, Object>> onEvent) {
        List<String> history = messageHistory.computeIfAbsent(convId, k -> new ArrayList<>());
        synchronized (history) {
            history.add("用户：" + userMessage);
            callLLM(history, emitter, onReply, onEvent);
        }
    }

    public void processFormResume(String convId, String formId,
                                   Map<String, Object> answers,
                                   SseEmitter emitter, Consumer<String> onReply,
                                   Consumer<Map<String, Object>> onEvent) {
        List<String> history = messageHistory.computeIfAbsent(convId, k -> new ArrayList<>());
        synchronized (history) {
            StringBuilder sb = new StringBuilder("[表单提交] ");
            for (var entry : answers.entrySet()) {
                if (sb.length() > 10) sb.append("；");
                sb.append(entry.getKey()).append("：").append(entry.getValue());
                collectedPrefs.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
            history.add("用户：" + sb);
            callLLM(history, emitter, onReply, onEvent);
        }
    }

    private void callLLM(List<String> history, SseEmitter emitter, Consumer<String> onReply,
                          Consumer<Map<String, Object>> onEvent) {
        try {
            // 构建完整 prompt
            StringBuilder prompt = new StringBuilder(SYSTEM_PROMPT);
            prompt.append("\n\n=== 对话历史 ===\n");
            for (String msg : history) {
                prompt.append(msg).append("\n");
            }
            prompt.append("\n请回复：");

            log.info("调用 LLM，历史消息数={}", history.size());
            long start = System.currentTimeMillis();
            String output = chatModel.chat(prompt.toString());
            long elapsed = System.currentTimeMillis() - start;
            log.info("LLM 响应完成，耗时={}ms，输出:\n{}", elapsed, output);

            history.add("AI：" + output);

            // 收集 AI 回复文本（用于保存到数据库）
            StringBuilder replyText = new StringBuilder();

            // 解析指令
            boolean hasCollect = false;
            boolean hasExecute = false;
            for (String line : output.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("RESPOND ")) {
                    String text = trimmed.substring(8).trim();
                    if (!text.isEmpty()) {
                        if (replyText.length() > 0) replyText.append("\n");
                        replyText.append(text);
                        safeSend(emitter, Map.of("type", "text", "content", text));
                    }
                } else if (trimmed.startsWith("COLLECT ")) {
                    String json = trimmed.substring(8).trim();
                    parseCollectPrefs(json);
                    hasCollect = true;
                } else if (trimmed.equals("EXECUTE")) {
                    hasExecute = true;
                }
            }

            // 兜底：没有指令则整体作为文字
            if (!output.contains("RESPOND ") && !output.contains("COLLECT ") && !output.contains("EXECUTE")) {
                replyText.append(output);
                safeSend(emitter, Map.of("type", "text", "content", output));
            }

            // 回调：保存 AI 回复到数据库
            if (onReply != null && replyText.length() > 0) {
                onReply.accept(replyText.toString());
            }

            // 关键：如果同时有 COLLECT 和 EXECUTE，忽略 EXECUTE（LLM 违反了规则）
            if (hasCollect && hasExecute) {
                log.warn("LLM 同时输出了 COLLECT 和 EXECUTE，忽略 EXECUTE");
                hasExecute = false;
            }

            // 关键修复：检查必需字段是否已收集完整（location + budget + style）
            // 不依赖 LLM 是否输出 EXECUTE，直接根据 collectedPrefs 判断
            if (hasCollect) {
                boolean hasLocation = collectedPrefs.containsKey("location") && !collectedPrefs.get("location").isEmpty();
                boolean hasBudget = collectedPrefs.containsKey("budget") && !collectedPrefs.get("budget").isEmpty();
                boolean hasStyle = collectedPrefs.containsKey("style") && !collectedPrefs.get("style").isEmpty();

                if (hasLocation && hasBudget && hasStyle) {
                    log.info("必需字段已完整 (location={}, budget={}, style={})，自动执行",
                            collectedPrefs.get("location"), collectedPrefs.get("budget"), collectedPrefs.get("style"));
                    hasCollect = false;
                    hasExecute = true;
                } else {
                    // 信息不完整，弹出表单（使用 collectedPrefs 构建）
                    String formJson = buildDynamicForm();
                    safeSend(emitter, Map.of("type", "form", "formSpec", formJson));
                }
            }

            if (hasExecute) {
                executeDatePlan(emitter, onEvent);
            } else {
                safeSend(emitter, Map.of("type", "done"));
                try { emitter.complete(); } catch (Exception ignored) {}
            }

        } catch (Exception e) {
            log.error("LoveAgent 处理失败", e);
            safeSend(emitter, Map.of("type", "error", "message", "处理出错: " + e.getMessage()));
            try { emitter.complete(); } catch (Exception ignored) {}
        }
    }

    private void parseCollectPrefs(String json) {
        try {
            String cleaned = json.replaceAll("[{}\"\\s]", "");
            for (String pair : cleaned.split(",")) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) collectedPrefs.put(kv[0].trim(), kv[1].trim());
            }
        } catch (Exception e) {
            log.warn("解析 COLLECT 失败: {}", json, e);
        }
    }

    /**
     * 基于 collectedPrefs 构建动态表单（只补充缺失的字段）
     */
    private String buildDynamicForm() {
        List<String> fields = new ArrayList<>();

        // 必填字段
        if (!collectedPrefs.containsKey("location") || collectedPrefs.get("location").isEmpty())
            fields.add("{\"key\":\"location\",\"label\":\"约会地点\",\"type\":\"text\",\"placeholder\":\"如：杭州西湖\",\"required\":true}");
        if (!collectedPrefs.containsKey("budget") || collectedPrefs.get("budget").isEmpty())
            fields.add("{\"key\":\"budget\",\"label\":\"预算范围\",\"type\":\"radio\",\"options\":[\"200以内\",\"200-500\",\"500-1000\",\"1000以上\"],\"required\":true}");
        if (!collectedPrefs.containsKey("style") || collectedPrefs.get("style").isEmpty())
            fields.add("{\"key\":\"style\",\"label\":\"喜欢的风格\",\"type\":\"checkbox\",\"options\":[\"浪漫\",\"休闲\",\"冒险\",\"文艺\",\"美食\"],\"required\":true}");

        // 可选字段
        if (!collectedPrefs.containsKey("occasion") || collectedPrefs.get("occasion").isEmpty())
            fields.add("{\"key\":\"occasion\",\"label\":\"约会场景（可选）\",\"type\":\"radio\",\"options\":[\"普通约会\",\"纪念日\",\"生日\",\"求婚\",\"第一次约会\"]}");
        if (!collectedPrefs.containsKey("activity") || collectedPrefs.get("activity").isEmpty())
            fields.add("{\"key\":\"activity\",\"label\":\"活动偏好（可选）\",\"type\":\"radio\",\"options\":[\"放松休闲\",\"动感体验\",\"文艺探索\",\"纯吃为主\"]}");

        if (fields.isEmpty()) return "{\"id\":\"form_skip\",\"title\":\"信息已完整\",\"fields\":[]}";

        StringJoiner joiner = new StringJoiner(",");
        for (String f : fields) joiner.add(f);
        return "{\"id\":\"form_" + UUID.randomUUID().toString().substring(0, 8)
                + "\",\"title\":\"补充约会信息\",\"fields\":[" + joiner + "]}";
    }

    private void executeDatePlan(SseEmitter emitter, Consumer<Map<String, Object>> onEvent) {
        String location = collectedPrefs.getOrDefault("location", "");
        String budget = collectedPrefs.getOrDefault("budget", "");
        String style = collectedPrefs.getOrDefault("style", "浪漫");
        String occasion = collectedPrefs.getOrDefault("occasion", "");
        String activity = collectedPrefs.getOrDefault("activity", "");

        try {
            // 推送中间事件（step/section/review）到前端，同时收集用于持久化
            Consumer<Map<String, Object>> eventBridge = event -> {
                safeSend(emitter, event);
                if (onEvent != null) onEvent.accept(event);
            };

            DatePlanService.ExecuteResult result = datePlanService.executeWithCallback(
                    location, budget, style, occasion, activity, eventBridge);

            if (result.getErrorMessage() != null) {
                safeSend(emitter, Map.of("type", "error", "message", result.getErrorMessage()));
                try { emitter.complete(); } catch (Exception ignored) {}
                return;
            }

            if (result.getPoiCategories() != null && !result.getPoiCategories().isEmpty()) {
                // 动态分类 POI
                Map<String, Object> poisEvent = new LinkedHashMap<>();
                poisEvent.put("type", "pois");
                poisEvent.put("categories", result.getPoiCategories());
                poisEvent.put("selected", result.getPoiSelected());
                safeSend(emitter, poisEvent);
                if (onEvent != null) onEvent.accept(new LinkedHashMap<>(poisEvent));
            } else if (result.getPois() != null && !result.getPois().isEmpty()) {
                // 向后兼容
                safeSend(emitter, Map.of("type", "pois", "items", result.getPois()));
                if (onEvent != null) onEvent.accept(Map.of("type", "pois", "items", result.getPois()));
            }

            if (result.getSelectedPois() != null && result.getSelectedPois().size() >= 2) {
                Map<String, Object> mapEvent = new HashMap<>();
                mapEvent.put("type", "map");
                mapEvent.put("pois", result.getSelectedPois());
                mapEvent.put("location", location);
                mapEvent.put("budget", budget);
                mapEvent.put("style", style);
                if (result.getRouteInfo() != null) mapEvent.put("routeInfo", result.getRouteInfo());
                safeSend(emitter, mapEvent);
                if (onEvent != null) onEvent.accept(new HashMap<>(mapEvent));
            }

            if (result.getPdfUrl() != null) {
                safeSend(emitter, Map.of("type", "pdf", "url", result.getPdfUrl()));
                if (onEvent != null) onEvent.accept(Map.of("type", "pdf", "url", result.getPdfUrl()));
            }

            // AI 总结
            String summary = generateSummary(location, budget, style, result);
            safeSend(emitter, Map.of("type", "text", "content", summary));

            safeSend(emitter, Map.of("type", "done"));
            try { emitter.complete(); } catch (Exception ignored) {}

        } catch (Exception e) {
            log.error("执行约会计划失败", e);
            safeSend(emitter, Map.of("type", "error", "message", "执行出错: " + e.getMessage()));
            try { emitter.complete(); } catch (Exception ignored) {}
        }
    }

    private String generateSummary(String location, String budget, String style,
                                    DatePlanService.ExecuteResult result) {
        // 先构建 fallback 总结（不依赖 LLM，确保一定有输出）
        String fallback = buildFallbackSummary(location, budget, style, result);

        try {
            StringBuilder prompt = new StringBuilder();
            prompt.append("你是一位温暖的恋爱顾问。根据以下约会规划结果，写一段温馨的结束语（100字以内，不要用任何markdown符号，语气温暖自然）：\n\n");
            prompt.append("📍 地点：").append(location).append("\n");
            prompt.append("💰 预算：").append(budget).append("\n");
            prompt.append("✨ 风格：").append(style).append("\n");
            if (result.getSelectedPois() != null && !result.getSelectedPois().isEmpty()) {
                prompt.append("🏛 推荐行程：");
                for (int i = 0; i < result.getSelectedPois().size(); i++) {
                    Map<String, Object> poi = result.getSelectedPois().get(i);
                    if (i > 0) prompt.append(" → ");
                    prompt.append(poi.get("name"));
                }
                prompt.append("\n");
            }
            prompt.append("\n请用温暖的语气总结这次约会，祝他们幸福。");

            String llmReply = chatModel.chat(prompt.toString());
            if (llmReply != null && !llmReply.isBlank() && llmReply.length() > 10) {
                return llmReply.trim() + "\n\n" + fallback;
            }
        } catch (Exception e) {
            log.warn("LLM 生成总结失败，使用 fallback", e);
        }
        return fallback;
    }

    private String buildFallbackSummary(String location, String budget, String style,
                                         DatePlanService.ExecuteResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════\n");
        sb.append("  💕 约会计划已为你准备好了！\n");
        sb.append("═══════════════════════\n\n");
        sb.append("📍 ").append(location).append("\n");
        sb.append("💰 预算：").append(budget).append("\n");
        sb.append("✨ 风格：").append(style).append("\n");

        if (result.getSelectedPois() != null && !result.getSelectedPois().isEmpty()) {
            sb.append("\n🗺 推荐行程：\n");
            String[] icons = {"☕", "🌸", "🍽️", "🎭", "🎵"};
            for (int i = 0; i < result.getSelectedPois().size(); i++) {
                Map<String, Object> poi = result.getSelectedPois().get(i);
                String icon = i < icons.length ? icons[i] : "•";
                sb.append(icon).append(" ").append(poi.get("name"));
                if (poi.get("address") != null) {
                    sb.append("\n   📌 ").append(poi.get("address"));
                }
                sb.append("\n");
            }
        }

        if (result.getRouteInfo() != null) {
            Map<String, Object> route = result.getRouteInfo();
            if (route.get("distance") != null) {
                sb.append("\n🚶 全程约 ").append(route.get("distance"));
            }
            if (route.get("duration") != null) {
                sb.append("，步行 ").append(route.get("duration"));
            }
            sb.append("\n");
        }

        if (result.getPdfUrl() != null) {
            sb.append("\n📄 详细计划已生成 PDF，点击上方链接下载\n");
        }

        sb.append("\n祝你们约会顺利，幸福美满！💕");
        return sb.toString();
    }

    private void safeSend(SseEmitter emitter, Object data) {
        try { emitter.send(data); } catch (IOException e) { log.warn("SSE 发送失败", e); }
    }
}

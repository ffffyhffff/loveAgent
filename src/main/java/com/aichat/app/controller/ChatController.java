package com.aichat.app.controller;

import com.aichat.app.model.*;
import com.aichat.app.service.AgentChatService;
import com.aichat.app.service.ChatService;
import com.aichat.app.service.LoveAgentService;
import com.aichat.app.service.RagService;
import com.aichat.app.tools.DatePlanTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 聊天控制器
 * 支持：普通 RAG 对话 + 约会规划（两阶段交互）
 */
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final AgentChatService agentChatService;
    private final RagService ragService;
    private final LoveAgentService loveAgentService;
    private final DatePlanTools datePlanTools;
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;

    // ==================== 对话管理 ====================

    @GetMapping("/conversations")
    public List<Map<String, Object>> listConversations() {
        return conversationRepository.findAllByOrderByUpdatedAtDesc().stream()
                .map(conv -> Map.<String, Object>of(
                        "id", conv.getConvId(),
                        "title", conv.getTitle(),
                        "messageCount", conv.getMessages().size(),
                        "updatedAt", conv.getUpdatedAt()
                ))
                .toList();
    }

    @GetMapping("/conversations/{convId}/messages")
    public List<Map<String, String>> getMessages(@PathVariable String convId) {
        return chatMessageRepository.findByConversationConvIdOrderByCreatedAtAsc(convId)
                .stream()
                .map(msg -> {
                    Map<String, String> map = new java.util.LinkedHashMap<>();
                    map.put("content", msg.getContent());
                    map.put("isUser", String.valueOf(msg.isUserMessage()));
                    map.put("type", msg.getType());
                    return map;
                })
                .toList();
    }

    @PostMapping("/conversations")
    public Map<String, String> createConversation() {
        String convId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Conversation conv = new Conversation();
        conv.setConvId(convId);
        conv.setTitle("新的对话");
        conversationRepository.save(conv);
        return Map.of("id", convId);
    }

    @DeleteMapping("/conversations/{convId}")
    public Map<String, String> deleteConversation(@PathVariable String convId) {
        conversationRepository.findByConvId(convId)
                .ifPresent(conversationRepository::delete);
        return Map.of("status", "ok");
    }

    // ==================== 普通对话 ====================

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        try {
            Conversation conv = getOrCreateConversation(request.getChatId());
            saveMessage(conv, true, request.getMessage());

            String userMessage = enrichWithRag(request.getMessage());
            String result = chatService.chat(userMessage);

            saveMessage(conv, false, result);
            updateTitle(conv, request.getMessage());

            return ChatResponse.builder()
                    .content(result)
                    .chatId(conv.getConvId())
                    .success(true)
                    .build();
        } catch (Exception e) {
            log.error("对话失败", e);
            return ChatResponse.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * SSE 流式对话（普通对话 + 情感咨询）
     */
    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatSse(@RequestParam String message,
                              @RequestParam String convId) {
        SseEmitter emitter = new SseEmitter(300000L);

        Conversation conv = getOrCreateConversation(convId);
        saveMessage(conv, true, message);

        StringBuilder aiResponse = new StringBuilder();
        String userMessage = enrichWithRag(message);

        agentChatService.chatStream(userMessage,
                token -> {
                    aiResponse.append(token);
                    try {
                        emitter.send(Map.of("type", "text", "content", token));
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                },
                () -> {
                    saveMessage(conv, false, aiResponse.toString());
                    updateTitle(conv, message);
                    try {
                        emitter.send(Map.of("type", "done"));
                        emitter.complete();
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                }
        );

        return emitter;
    }

    // ==================== LoveAgent（AI 驱动约会规划）====================

    /**
     * 意图路由：判断消息走普通对话还是约会规划
     */
    @PostMapping("/route-intent")
    public Map<String, String> routeIntent(@RequestBody Map<String, String> request) {
        String message = request.getOrDefault("message", "");
        String intent = loveAgentService.classifyIntent(message);
        log.info("意图路由: '{}' → {}", message, intent);
        return Map.of("intent", intent);
    }

    /**
     * LoveAgent 流式对话（初始消息）
     * AI 自主决定收集什么信息、何时执行计划
     */
    @PostMapping(value = "/love-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter loveStream(@RequestBody LoveStreamRequest request) {
        SseEmitter emitter = new SseEmitter(300000L);

        Conversation conv = getOrCreateConversation(request.getConvId());
        saveMessage(conv, true, request.getMessage());

        String convId = conv.getConvId();
        String message = request.getMessage();

        // 收集 SSE 事件用于持久化
        List<Map<String, Object>> sseEvents = Collections.synchronizedList(new ArrayList<>());

        CompletableFuture.runAsync(() -> {
            try {
                loveAgentService.processMessage(convId, message, emitter,
                        aiReply -> saveMessage(conv, false, aiReply),
                        event -> sseEvents.add(event));
                updateTitle(conv, message);

                // 保存结构化事件到数据库
                if (!sseEvents.isEmpty()) {
                    saveStructuredMessage(conv, sseEvents);
                }
            } catch (Exception e) {
                log.error("LoveAgent 处理失败", e);
                try {
                    emitter.send(Map.of("type", "error", "message", "处理出错: " + e.getMessage()));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * LoveAgent 流式对话（表单提交后续聊）
     */
    @PostMapping(value = "/love-stream/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter loveStreamResume(@RequestBody LoveStreamRequest request) {
        SseEmitter emitter = new SseEmitter(300000L);

        Conversation conv = getOrCreateConversation(request.getConvId());
        String answerSummary = formatAnswersForSave(request.getAnswers());
        saveMessage(conv, true, answerSummary);

        String convId = conv.getConvId();

        List<Map<String, Object>> sseEvents = Collections.synchronizedList(new ArrayList<>());

        CompletableFuture.runAsync(() -> {
            try {
                loveAgentService.processFormResume(
                        convId, request.getFormId(), request.getAnswers(), emitter,
                        aiReply -> saveMessage(conv, false, aiReply),
                        event -> sseEvents.add(event));

                if (!sseEvents.isEmpty()) {
                    saveStructuredMessage(conv, sseEvents);
                }
            } catch (Exception e) {
                log.error("LoveAgent 续聊失败", e);
                try {
                    emitter.send(Map.of("type", "error", "message", "处理出错: " + e.getMessage()));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    // ==================== 计划修改 ====================

    /**
     * 重新生成路线+PDF（可视化选择后调用）
     */
    @PostMapping(value = "/regenerate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter regeneratePlan(@RequestBody Map<String, Object> request) {
        SseEmitter emitter = new SseEmitter(300000L);

        CompletableFuture.runAsync(() -> {
            try {
                String convId = (String) request.get("convId");
                String location = (String) request.get("location");
                String budget = (String) request.getOrDefault("budget", "");
                String style = (String) request.getOrDefault("style", "");

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> selectedPois =
                        (List<Map<String, Object>>) request.get("selectedPois");

                emitter.send(Map.of("type", "step", "message", "正在重新规划路线..."));

                DatePlanTools.RegenerateResult result = datePlanTools.regenerate(
                        selectedPois, location, budget, style);

                if (result.getErrorMessage() != null) {
                    emitter.send(Map.of("type", "error", "message", result.getErrorMessage()));
                    emitter.complete();
                    return;
                }

                // 推送更新后的地图
                if (result.selectedPois != null && result.selectedPois.size() >= 2) {
                    Map<String, Object> mapEvent = new LinkedHashMap<>();
                    mapEvent.put("type", "map");
                    mapEvent.put("pois", result.getSelectedPois());
                    mapEvent.put("location", location);
                    mapEvent.put("budget", budget);
                    mapEvent.put("style", style);
                    if (result.routeInfo != null) mapEvent.put("routeInfo", result.routeInfo);
                    emitter.send(mapEvent);
                }

                // 推送新 PDF
                if (result.pdfUrl != null) {
                    emitter.send(Map.of("type", "pdf", "url", result.getPdfUrl()));
                }

                // 总结
                emitter.send(Map.of("type", "text", "content", "计划已更新！路线和PDF已重新生成 ✨"));

                // 保存到数据库
                if (convId != null) {
                    Conversation conv = getOrCreateConversation(convId);
                    List<Map<String, Object>> events = new ArrayList<>();
                    if (result.getSelectedPois() != null) {
                        Map<String, Object> mapEvent = new LinkedHashMap<>();
                        mapEvent.put("type", "map");
                        mapEvent.put("pois", result.getSelectedPois());
                        mapEvent.put("location", location);
                        if (result.routeInfo != null) mapEvent.put("routeInfo", result.routeInfo);
                        events.add(mapEvent);
                    }
                    if (result.pdfUrl != null) {
                        events.add(Map.of("type", "pdf", "url", result.getPdfUrl()));
                    }
                    if (!events.isEmpty()) saveStructuredMessage(conv, events);
                }

                emitter.send(Map.of("type", "done"));
                emitter.complete();

            } catch (Exception e) {
                log.error("重新生成失败", e);
                try {
                    emitter.send(Map.of("type", "error", "message", "重新生成失败: " + e.getMessage()));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * AI 对话式修改约会计划（SSE 流）— 支持 replace / remove / add / regenerate / retry
     */
    @SuppressWarnings("unchecked")
    @PostMapping(value = "/modify-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter modifyPlan(@RequestBody Map<String, Object> request) {
        SseEmitter emitter = new SseEmitter(300000L);

        String message = (String) request.get("message");
        String location = (String) request.getOrDefault("location", "未知地点");
        String convId = (String) request.get("convId");
        List<Map<String, Object>> currentPois = (List<Map<String, Object>>) request.getOrDefault("currentPois", List.of());

        CompletableFuture.runAsync(() -> {
            try {
                dev.langchain4j.model.chat.ChatLanguageModel model =
                        ((LoveAgentService) loveAgentService).getChatModel();

                // 构建当前计划摘要
                StringBuilder planSummary = new StringBuilder("当前计划包含以下地点：\n");
                if (currentPois != null) {
                    for (int i = 0; i < currentPois.size(); i++) {
                        Map<String, Object> p = currentPois.get(i);
                        planSummary.append("  ").append(i + 1).append(". ")
                                .append(p.getOrDefault("name", "未知")).append("\n");
                    }
                } else {
                    planSummary.append("  (暂无)\n");
                }

                // LLM 自主决策操作类型
                String parsePrompt = "用户想修改约会计划。\n\n" +
                        planSummary + "\n" +
                        "用户说：" + message + "\n\n" +
                        "判断用户意图，以 JSON 回复：\n" +
                        "{\"action\":\"replace|remove|add|regenerate|retry\",\"reason\":\"简短说明\"}\n" +
                        "- replace: 用户想把某个地点换成别的 → 加 keyword(搜索词), targetIndex(从1开始,可选)\n" +
                        "- remove: 用户想删除某个地点 → 加 targetIndex(从1开始)\n" +
                        "- add: 用户想增加新类型地点 → 加 keyword(搜索词)\n" +
                        "- regenerate: 用户只想重新生成PDF/总结 → 无需额外字段\n" +
                        "- retry: 用户不满意整体，想重新规划 → 加 feedback(反馈内容)\n" +
                        "只回复 JSON。";

                String llmReply = model.chat(parsePrompt);
                log.info("AI 修改意图解析: {}", llmReply);

                cn.hutool.json.JSONObject intent;
                try {
                    intent = cn.hutool.json.JSONUtil.parseObj(llmReply.replaceAll("```json|```", "").trim());
                } catch (Exception e) {
                    emitter.send(Map.of("type", "text", "content", "抱歉，我没有理解你的意图，请换个说法试试～"));
                    emitter.send(Map.of("type", "done"));
                    emitter.complete();
                    return;
                }

                String action = intent.getStr("action", "replace");
                String reason = intent.getStr("reason", "");
                log.info("修改操作: action={}, reason={}", action, reason);

                List<Map<String, Object>> updatedPois = new ArrayList<>(currentPois != null ? currentPois : List.of());

                switch (action) {
                    case "remove" -> {
                        int idx = intent.getInt("targetIndex", -1);
                        if (idx >= 1 && idx <= updatedPois.size()) {
                            Map<String, Object> removed = updatedPois.remove(idx - 1);
                            emitter.send(Map.of("type", "text", "content",
                                    "已从计划中移除 " + removed.getOrDefault("name", "该地点") + "～"));
                        } else {
                            emitter.send(Map.of("type", "text", "content", "抱歉，没有找到要移除的地点，请指明是第几个～"));
                            emitter.send(Map.of("type", "done"));
                            emitter.complete();
                            return;
                        }
                    }
                    case "replace" -> {
                        String keyword = intent.getStr("keyword", "");
                        int targetIdx = intent.getInt("targetIndex", -1);
                        if (keyword.isEmpty()) {
                            emitter.send(Map.of("type", "text", "content", "好的！请告诉我你想换成什么类型的地方？"));
                            emitter.send(Map.of("type", "done"));
                            emitter.complete();
                            return;
                        }
                        emitter.send(Map.of("type", "step", "message", "正在搜索 " + keyword + " 替代方案...",
                                "status", "active", "index", 0, "total", 1));

                        List<Map<String, Object>> alts = datePlanTools.searchAlternative(keyword, location);
                        if (!alts.isEmpty()) {
                            Map<String, Object> best = alts.get(0);
                            if (targetIdx >= 1 && targetIdx <= updatedPois.size()) {
                                updatedPois.set(targetIdx - 1, best);
                            } else {
                                updatedPois.add(best);
                            }
                            emitter.send(Map.of("type", "text", "content",
                                    "已将计划更新为 " + best.getOrDefault("name", keyword) + "！"));
                        } else {
                            emitter.send(Map.of("type", "text", "content",
                                    "抱歉，没有找到 " + keyword + " 的替代方案，请换个关键词试试～"));
                            emitter.send(Map.of("type", "done"));
                            emitter.complete();
                            return;
                        }
                    }
                    case "add" -> {
                        String keyword = intent.getStr("keyword", "");
                        if (keyword.isEmpty()) {
                            emitter.send(Map.of("type", "text", "content", "好的！请告诉我想增加什么类型的地方？"));
                            emitter.send(Map.of("type", "done"));
                            emitter.complete();
                            return;
                        }
                        emitter.send(Map.of("type", "step", "message", "正在搜索 " + keyword + "...",
                                "status", "active", "index", 0, "total", 1));

                        List<Map<String, Object>> extras = datePlanTools.searchAlternative(keyword, location);
                        if (!extras.isEmpty()) {
                            updatedPois.add(extras.get(0));
                            emitter.send(Map.of("type", "text", "content",
                                    "已添加 " + extras.get(0).getOrDefault("name", keyword) + " 到计划中！"));
                        } else {
                            emitter.send(Map.of("type", "text", "content",
                                    "抱歉，没有找到 " + keyword + " 相关的地点～"));
                            emitter.send(Map.of("type", "done"));
                            emitter.complete();
                            return;
                        }
                    }
                    case "retry" -> {
                        String feedback = intent.getStr("feedback", message);
                        emitter.send(Map.of("type", "text", "content", "好的，根据你的反馈重新规划约会路线..."));
                        emitter.send(Map.of("type", "step", "message", "重新规划中...",
                                "status", "active", "index", 0, "total", 1));
                        // 走完整 Agent 流程，传入反馈
                        try {
                            loveAgentService.processMessage(convId, "重新规划约会：" + feedback, null, null,
                                    event -> safeSend(emitter, event));
                            emitter.send(Map.of("type", "done"));
                            emitter.complete();
                            return;
                        } catch (Exception e) {
                            log.error("重新规划失败", e);
                            emitter.send(Map.of("type", "error", "message", "重新规划失败: " + e.getMessage()));
                            emitter.completeWithError(e);
                            return;
                        }
                    }
                    // regenerate: 直接用当前 POIs 重新生成地图+PDF
                    default -> emitter.send(Map.of("type", "text", "content",
                            "好的，根据当前计划重新生成路线和 PDF..."));
                }

                // 为非 retry 操作重新生成地图+PDF
                if (!"retry".equals(action) && updatedPois.size() >= 2) {
                    DatePlanTools.RegenerateResult result = datePlanTools.regenerate(
                            updatedPois, location, "", "");
                    if (result.getErrorMessage() == null) {
                        Map<String, Object> mapEvent = new LinkedHashMap<>();
                        mapEvent.put("type", "map");
                        mapEvent.put("pois", result.getSelectedPois());
                        mapEvent.put("location", location);
                        if (result.routeInfo != null) mapEvent.put("routeInfo", result.routeInfo);
                        emitter.send(mapEvent);

                        if (result.pdfUrl != null) {
                            emitter.send(Map.of("type", "pdf", "url", result.getPdfUrl()));
                        } else {
                            emitter.send(Map.of("type", "text", "content", "路线已更新，但 PDF 生成遇到问题～"));
                        }

                        // 保存到数据库
                        if (convId != null && result.getSelectedPois() != null) {
                            Conversation conv = getOrCreateConversation(convId);
                            List<Map<String, Object>> events = new ArrayList<>();
                            Map<String, Object> me = new LinkedHashMap<>();
                            me.put("type", "map");
                            me.put("pois", result.getSelectedPois());
                            me.put("location", location);
                            if (result.routeInfo != null) me.put("routeInfo", result.routeInfo);
                            events.add(me);
                            if (result.pdfUrl != null) events.add(Map.of("type", "pdf", "url", result.getPdfUrl()));
                            saveStructuredMessage(conv, events);
                        }
                    }
                }

                emitter.send(Map.of("type", "done"));
                emitter.complete();

            } catch (Exception e) {
                log.error("AI 修改失败", e);
                try {
                    emitter.send(Map.of("type", "error", "message", "修改出错: " + e.getMessage()));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private void safeSend(SseEmitter emitter, Map<String, Object> event) {
        try { emitter.send(event); } catch (IOException e) { /* ignore */ }
    }

    private String formatAnswersForSave(Map<String, Object> answers) {
        if (answers == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : answers.entrySet()) {
            if (sb.length() > 0) sb.append("；");
            sb.append(entry.getKey()).append("：").append(entry.getValue());
        }
        return sb.toString();
    }

    // ==================== 工具方法 ====================

    private Conversation getOrCreateConversation(String convId) {
        if (convId != null && !convId.isBlank()) {
            return conversationRepository.findByConvId(convId)
                    .orElseGet(() -> {
                        Conversation conv = new Conversation();
                        conv.setConvId(convId);
                        return conversationRepository.save(conv);
                    });
        }
        String newId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Conversation conv = new Conversation();
        conv.setConvId(newId);
        return conversationRepository.save(conv);
    }

    private void saveMessage(Conversation conv, boolean isUser, String content) {
        saveMessage(conv, isUser, "text", content);
    }

    private void saveMessage(Conversation conv, boolean isUser, String type, String content) {
        ChatMessageEntity msg = new ChatMessageEntity();
        msg.setConversation(conv);
        msg.setUserMessage(isUser);
        msg.setType(type);
        msg.setContent(content);
        chatMessageRepository.save(msg);
    }

    /** 将 SSE 事件列表序列化为 JSON 并保存为结构化消息 */
    private void saveStructuredMessage(Conversation conv, List<Map<String, Object>> events) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String json = mapper.writeValueAsString(events);
            saveMessage(conv, false, "sse_events", json);
            log.info("保存结构化 SSE 事件，共 {} 条", events.size());
        } catch (Exception e) {
            log.error("保存结构化消息失败", e);
        }
    }

    private void updateTitle(Conversation conv, String message) {
        if (conv.getTitle().equals("新的对话") && !message.isEmpty()) {
            conv.setTitle(message.substring(0, Math.min(message.length(), 20))
                    .replace("\n", " "));
            conversationRepository.save(conv);
        }
    }

    private String enrichWithRag(String message) {
        try {
            String ragContext = ragService.search(message);
            if (!ragContext.isEmpty()) {
                return ragContext + "\n\n用户问题：" + message;
            }
        } catch (Exception e) {
            log.warn("RAG 查询失败", e);
        }
        return message;
    }

    /**
     * PDF 下载
     */
    @GetMapping("/pdf/{filename}")
    public ResponseEntity<org.springframework.core.io.Resource> downloadPdf(@PathVariable String filename) {
        try {
            java.io.File file = new java.io.File(System.getProperty("user.dir") + "/tmp/" + filename);
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }
            org.springframework.core.io.Resource resource =
                    new org.springframework.core.io.FileSystemResource(file);
            return ResponseEntity.ok()
                    .header("Content-Disposition", "inline; filename=" + filename)
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}

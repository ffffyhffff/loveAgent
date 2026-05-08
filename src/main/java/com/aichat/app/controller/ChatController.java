package com.aichat.app.controller;

import com.aichat.app.model.*;
import com.aichat.app.service.AgentChatService;
import com.aichat.app.service.ChatService;
import com.aichat.app.service.DatePlanService;
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
    private final DatePlanService datePlanService;
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

    // ==================== 约会规划（三步交互） ====================

    /**
     * Step 1: 分析用户意图，提取信息
     * 信息不全 → 返回 needPreferences=true，前端弹窗
     * 信息完整 → 直接返回计划
     */
    @PostMapping("/date-plan/analyze")
    public Map<String, Object> datePlanAnalyze(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        log.info("Step 1: 分析意图 - {}", message);

        DatePlanService.PlanResult result = datePlanService.extractIntent(message);
        return planResultToMap(result);
    }

    /**
     * Step 1b: 用户补充偏好后，生成计划
     */
    @PostMapping("/date-plan/plan")
    public Map<String, Object> datePlanPlan(@RequestBody Map<String, String> request) {
        String location = request.getOrDefault("location", "");
        String budget = request.getOrDefault("budget", "");
        String style = request.getOrDefault("style", "");
        String duration = request.getOrDefault("duration", "");
        String keywords = request.getOrDefault("keywords", "");
        log.info("Step 1b: 生成计划 - location={}", location);

        DatePlanService.PlanResult info = new DatePlanService.PlanResult();
        info.setLocation(location);
        info.setBudget(budget);
        info.setStyle(style);
        info.setDuration(duration);
        info.setKeywords(keywords);

        DatePlanService.PlanResult result = datePlanService.generatePlan(info);
        return planResultToMap(result);
    }

    private Map<String, Object> planResultToMap(DatePlanService.PlanResult result) {
        Map<String, Object> response = new HashMap<>();
        response.put("type", result.getType());

        if ("chat".equals(result.getType())) {
            response.put("content", result.getChatResponse());
        } else if ("plan".equals(result.getType())) {
            response.put("planDescription", result.getPlanDescription());
            response.put("location", result.getLocation());
            response.put("budget", result.getBudget());
            response.put("style", result.getStyle());
            response.put("duration", result.getDuration());
            response.put("keywords", result.getKeywords());
            response.put("occasion", result.getOccasion());
            response.put("activity", result.getActivity());
            response.put("needPreferences", result.isNeedPreferences());
        } else {
            response.put("message", result.getErrorMessage());
        }

        return response;
    }

    /**
     * Phase 2: 确认后执行约会规划
     * 搜索 POI → 选点 → 路线 → PDF，通过 SSE 逐步推送结果
     */
    @GetMapping(value = "/date-plan/execute", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter datePlanExecute(
            @RequestParam String location,
            @RequestParam String budget,
            @RequestParam String style,
            @RequestParam(required = false, defaultValue = "") String occasion,
            @RequestParam(required = false, defaultValue = "") String activity,
            @RequestParam String convId) {

        SseEmitter emitter = new SseEmitter(300000L);

        CompletableFuture.runAsync(() -> {
            try {
                // Step 1: 搜索 POI
                emitter.send(Map.of("type", "step", "message", "正在搜索 " + location + " 附近的地点..."));

                DatePlanService.ExecuteResult result = datePlanService.executeApproved(location, budget, style, occasion, activity);

                if (result.getErrorMessage() != null) {
                    emitter.send(Map.of("type", "error", "message", result.getErrorMessage()));
                    emitter.complete();
                    return;
                }

                // 推送 POI 列表
                if (result.getPois() != null && !result.getPois().isEmpty()) {
                    emitter.send(Map.of("type", "pois", "items", result.getPois()));
                }

                // 推送选中的 POI + 地图数据
                if (result.getSelectedPois() != null && result.getSelectedPois().size() >= 2) {
                    Map<String, Object> mapEvent = new HashMap<>();
                    mapEvent.put("type", "map");
                    mapEvent.put("pois", result.getSelectedPois());
                    if (result.getRouteInfo() != null) {
                        mapEvent.put("routeInfo", result.getRouteInfo());
                    }
                    emitter.send(mapEvent);
                }

                // 推送 PDF 下载链接
                if (result.getPdfUrl() != null) {
                    emitter.send(Map.of("type", "pdf", "url", result.getPdfUrl()));
                }

                // 保存对话
                Conversation conv = getOrCreateConversation(convId);
                String summary = buildPlanSummary(location, budget, style, result);
                saveMessage(conv, false, summary);

                emitter.send(Map.of("type", "done"));
                emitter.complete();

            } catch (Exception e) {
                log.error("Phase 2 执行失败", e);
                try {
                    emitter.send(Map.of("type", "error", "message", "执行出错: " + e.getMessage()));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });

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
                if (result.getSelectedPois() != null && result.getSelectedPois().size() >= 2) {
                    Map<String, Object> mapEvent = new LinkedHashMap<>();
                    mapEvent.put("type", "map");
                    mapEvent.put("pois", result.getSelectedPois());
                    mapEvent.put("location", location);
                    mapEvent.put("budget", budget);
                    mapEvent.put("style", style);
                    if (result.getRouteInfo() != null) mapEvent.put("routeInfo", result.getRouteInfo());
                    emitter.send(mapEvent);
                }

                // 推送新 PDF
                if (result.getPdfUrl() != null) {
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
                        if (result.getRouteInfo() != null) mapEvent.put("routeInfo", result.getRouteInfo());
                        events.add(mapEvent);
                    }
                    if (result.getPdfUrl() != null) {
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
     * AI 对话式修改约会计划（SSE 流）
     */
    @PostMapping(value = "/modify-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter modifyPlan(@RequestBody Map<String, String> request) {
        SseEmitter emitter = new SseEmitter(300000L);

        String message = request.get("message");
        String location = request.get("location");

        CompletableFuture.runAsync(() -> {
            try {
                // 用 LLM 解析用户意图
                String parsePrompt = "用户想修改约会计划。用户说：" + message +
                        "\n\n请以 JSON 格式回复修改意图，格式：{\"keyword\":\"搜索关键词\",\"category\":\"要替换的类别名称\"}" +
                        "\n例如用户说\"换成茶馆\" → {\"keyword\":\"茶馆\",\"category\":\"下午茶\"}" +
                        "\n例如用户说\"晚餐想去火锅\" → {\"keyword\":\"火锅\",\"category\":\"晚餐\"}" +
                        "\n只回复 JSON，不要其他内容。";

                dev.langchain4j.model.chat.ChatLanguageModel model =
                        ((LoveAgentService) loveAgentService).getChatModel();
                String llmReply = model.chat(parsePrompt);
                log.info("AI 修改意图解析: {}", llmReply);

                // 解析 JSON
                String keyword = null;
                String category = null;
                try {
                    String cleaned = llmReply.replaceAll("```json|```", "").trim();
                    cn.hutool.json.JSONObject json = cn.hutool.json.JSONUtil.parseObj(cleaned);
                    keyword = json.getStr("keyword");
                    category = json.getStr("category");
                } catch (Exception e) {
                    log.warn("解析意图失败: {}", llmReply);
                }

                if (keyword == null || keyword.isEmpty()) {
                    emitter.send(Map.of("type", "text", "content", "抱歉，我没有理解你的修改意图，请再试一次～"));
                    emitter.send(Map.of("type", "done"));
                    emitter.complete();
                    return;
                }

                emitter.send(Map.of("type", "step", "message", "正在搜索 " + keyword + "..."));

                // 搜索替代 POI
                List<Map<String, Object>> alternatives = datePlanTools.searchAlternative(keyword, location);

                if (alternatives.isEmpty()) {
                    emitter.send(Map.of("type", "text", "content", "抱歉，没有找到相关的 " + keyword + "，请换个关键词试试～"));
                    emitter.send(Map.of("type", "done"));
                    emitter.complete();
                    return;
                }

                // 推送替代 POI 列表
                Map<String, Object> poisEvent = new LinkedHashMap<>();
                poisEvent.put("type", "pois");
                poisEvent.put("categories", List.of(
                        Map.of("label", category != null ? category : keyword, "key", "modified", "items", alternatives)
                ));
                poisEvent.put("selected", Map.of("modified", 0));
                emitter.send(poisEvent);

                emitter.send(Map.of("type", "text", "content",
                        "找到了 " + alternatives.size() + " 个" + keyword + "！请选择喜欢的，然后点击「确认修改」重新生成路线～"));

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

    private String buildPlanSummary(String location, String budget, String style,
                                     DatePlanService.ExecuteResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("约会规划完成\n");
        sb.append("📍 ").append(location).append("\n");
        sb.append("💰 ").append(budget).append("\n");
        sb.append("✨ ").append(style).append("\n");
        if (result.getSelectedPois() != null) {
            for (Map<String, Object> poi : result.getSelectedPois()) {
                sb.append("• ").append(poi.get("name")).append("\n");
            }
        }
        if (result.getPdfUrl() != null) {
            sb.append("📄 PDF: ").append(result.getPdfUrl());
        }
        return sb.toString();
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
                    .header("Content-Disposition", "attachment; filename=" + filename)
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}

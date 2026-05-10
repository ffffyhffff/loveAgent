package com.aichat.app.service;

import com.aichat.app.graph.PlanExecuteState;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@Service
@Slf4j
public class LoveAgentService {

    private final ChatLanguageModel chatModel;
    private final PlanExecuteRunner planExecuteRunner;
    private final Map<String, Map<String, String>> conversationPrefs = new ConcurrentHashMap<>();
    private final AtomicLong eventIds = new AtomicLong();

    public LoveAgentService(ChatLanguageModel chatModel, PlanExecuteRunner planExecuteRunner) {
        this.chatModel = chatModel;
        this.planExecuteRunner = planExecuteRunner;
    }

    public ChatLanguageModel getChatModel() {
        return chatModel;
    }

    public String classifyIntent(String message) {
        String text = message == null ? "" : message;
        // 仅明确约会规划类关键词才走 plan
        boolean isPlan = text.contains("约会") || text.contains("行程安排") || text.contains("攻略")
                || text.contains("约会计划") || text.contains("出行计划") || text.contains("旅行计划")
                || text.contains("帮我规划") || text.contains("路线规划") || text.contains("去哪")
                || text.contains("想去") || text.contains("帮我推荐") || text.contains("附近");
        if (isPlan) {
            return "plan";
        }
        return "chat";
    }

    public void processMessage(String convId, String userMessage,
                               SseEmitter emitter, Consumer<String> onReply,
                               Consumer<Map<String, Object>> onEvent) {
        Map<String, String> prefs = conversationPrefs.computeIfAbsent(convId, k -> new LinkedHashMap<>());
        mergeExtractedPrefs(prefs, userMessage);

        if (!hasRequiredPrefs(prefs)) {
            String reply = "我先补齐几个关键信息，再开始生成约会计划。";
            sendText(emitter, reply, onReply, onEvent);
            safeSend(emitter, Map.of("type", "form", "formSpec", buildDynamicForm(prefs)));
            safeSend(emitter, Map.of("type", "done"));
            complete(emitter);
            return;
        }

        executeDatePlan(prefs, emitter, onEvent);
    }

    public void processFormResume(String convId, String formId,
                                  Map<String, Object> answers,
                                  SseEmitter emitter, Consumer<String> onReply,
                                  Consumer<Map<String, Object>> onEvent) {
        Map<String, String> prefs = conversationPrefs.computeIfAbsent(convId, k -> new LinkedHashMap<>());
        if (answers != null) {
            answers.forEach((key, value) -> {
                if (value != null && !String.valueOf(value).isBlank()) {
                    prefs.put(key, String.valueOf(value));
                }
            });
        }

        if (!hasRequiredPrefs(prefs)) {
            String reply = "还差一点信息，补齐后我就开始规划。";
            sendText(emitter, reply, onReply, onEvent);
            safeSend(emitter, Map.of("type", "form", "formSpec", buildDynamicForm(prefs)));
            safeSend(emitter, Map.of("type", "done"));
            complete(emitter);
            return;
        }

        executeDatePlan(prefs, emitter, onEvent);
    }

    private void executeDatePlan(Map<String, String> prefs,
                                 SseEmitter emitter,
                                 Consumer<Map<String, Object>> onEvent) {
        try {
            String location = prefs.getOrDefault("location", "");
            String budget = prefs.getOrDefault("budget", "");
            String style = prefs.getOrDefault("style", "浪漫");
            String occasion = prefs.getOrDefault("occasion", "");
            String activity = prefs.getOrDefault("activity", "");

            Consumer<Map<String, Object>> eventSink = event -> {
                safeSend(emitter, event);
                if (onEvent != null) {
                    onEvent.accept(event);
                }
            };

            PlanExecuteState state = planExecuteRunner.run(location, budget, style, occasion, activity, eventSink);
            String finalAnswer = state.getFinalAnswer();
            if (finalAnswer == null || finalAnswer.isBlank()) {
                finalAnswer = buildFallbackFinalAnswer(location);
            }

            sendText(emitter, finalAnswer, null, onEvent);
            safeSend(emitter, Map.of("type", "done"));
            complete(emitter);
        } catch (Exception e) {
            log.error("Plan-execute failed", e);
            safeSend(emitter, Map.of("type", "error", "message", "执行出错：" + e.getMessage()));
            complete(emitter);
        }
    }

    private void mergeExtractedPrefs(Map<String, String> prefs, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        if (!prefs.containsKey("location")) {
            String location = extractLocation(message);
            if (!location.isBlank()) {
                prefs.put("location", location);
            }
        }
    }

    private String extractLocation(String message) {
        String text = message
                .replace("我想", "")
                .replace("想去", "")
                .replace("约会", "")
                .replace("帮我", "")
                .replace("规划", "")
                .replace("带我去", "")
                .trim();
        return text.length() > 20 ? "" : text;
    }

    private boolean hasRequiredPrefs(Map<String, String> prefs) {
        return hasValue(prefs, "location") && hasValue(prefs, "budget") && hasValue(prefs, "style");
    }

    private boolean hasValue(Map<String, String> prefs, String key) {
        return prefs.containsKey(key) && prefs.get(key) != null && !prefs.get(key).isBlank();
    }

    private String buildDynamicForm(Map<String, String> prefs) {
        List<String> fields = new ArrayList<>();
        if (!hasValue(prefs, "location")) {
            fields.add("{\"key\":\"location\",\"label\":\"约会地点\",\"type\":\"text\",\"placeholder\":\"如：杭州西湖\",\"required\":true}");
        }
        if (!hasValue(prefs, "budget")) {
            fields.add("{\"key\":\"budget\",\"label\":\"预算范围\",\"type\":\"radio\",\"options\":[\"200以内\",\"200-500\",\"500-1000\",\"1000以上\"],\"required\":true}");
        }
        if (!hasValue(prefs, "style")) {
            fields.add("{\"key\":\"style\",\"label\":\"喜欢的风格\",\"type\":\"checkbox\",\"options\":[\"浪漫\",\"休闲\",\"冒险\",\"文艺\",\"美食\"],\"required\":true}");
        }
        if (!hasValue(prefs, "occasion")) {
            fields.add("{\"key\":\"occasion\",\"label\":\"约会场景（可选）\",\"type\":\"radio\",\"options\":[\"普通约会\",\"纪念日\",\"生日\",\"求婚\",\"第一次约会\"]}");
        }
        if (!hasValue(prefs, "activity")) {
            fields.add("{\"key\":\"activity\",\"label\":\"活动偏好（可选）\",\"type\":\"radio\",\"options\":[\"放松休闲\",\"动感体验\",\"文艺探索\",\"纯吃为主\"]}");
        }
        return "{\"id\":\"form_" + UUID.randomUUID().toString().substring(0, 8)
                + "\",\"title\":\"补充约会信息\",\"fields\":[" + String.join(",", fields) + "]}";
    }

    private String buildFallbackFinalAnswer(String location) {
        return "计划已经准备好了。地图路线和 PDF 都已生成，可以按上面的行程点出发。祝你们在"
                + location + "约会顺利，玩得开心。";
    }

    private void sendText(SseEmitter emitter, String content, Consumer<String> onReply,
                          Consumer<Map<String, Object>> onEvent) {
        Map<String, Object> event = Map.of("type", "text", "content", content);
        safeSend(emitter, event);
        if (onReply != null) {
            onReply.accept(content);
        }
        if (onEvent != null) {
            onEvent.accept(event);
        }
    }

    private void safeSend(SseEmitter emitter, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .id(String.valueOf(eventIds.incrementAndGet()))
                    .name("message")
                    .data(data));
        } catch (IOException e) {
            log.warn("SSE send failed", e);
        }
    }

    private void complete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
        }
    }
}

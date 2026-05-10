package com.aichat.app.graph.nodes;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aichat.app.graph.PlanExecuteState;
import com.aichat.app.tools.AmapTools;
import com.aichat.app.tools.AgentToolRegistry;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Component
@Slf4j
public class ExecutorNode implements NodeAction {

    private static final int MAX_CANDIDATES_PER_CATEGORY = 5;
    private static final int MAX_ITINERARY_POIS = 3;

    private final ChatLanguageModel chatModel;
    private final AgentToolRegistry toolRegistry;
    private final List<ToolSpecification> toolSpecs;
    private final List<Map<String, Object>> selectedPois = new ArrayList<>();
    private final Map<String, List<Map<String, Object>>> candidatesByKeyword = new LinkedHashMap<>();
    private Consumer<Map<String, Object>> onEvent;

    public ExecutorNode(ChatLanguageModel chatModel, AgentToolRegistry toolRegistry) {
        this.chatModel = chatModel;
        this.toolRegistry = toolRegistry;
        this.toolSpecs = toolRegistry.getToolSpecifications();
    }

    public void setOnEvent(Consumer<Map<String, Object>> onEvent) {
        this.onEvent = onEvent;
        this.selectedPois.clear();
        this.candidatesByKeyword.clear();
    }

    @Override
    public Map<String, Object> apply(org.bsc.langgraph4j.state.AgentState agentState) {
        PlanExecuteState state = (PlanExecuteState) agentState;
        List<String> steps = state.getPlanSteps();
        int currentIndex = state.getCurrentStepIndex();

        if (currentIndex >= steps.size()) {
            return Map.of(PlanExecuteState.ACTION, "done");
        }

        String currentStep = steps.get(currentIndex);
        String location = state.value(PlanExecuteState.DATE_LOCATION).map(Object::toString).orElse("");
        String accumulatedContext = state.getAccumulatedContext();

        log.info("Executor step {}/{}: {}", currentIndex + 1, steps.size(), currentStep);
        long stepStartTime = System.currentTimeMillis();
        emit(Map.of("type", "step", "message", currentStep,
                "status", "active", "index", currentIndex, "total", steps.size()));
        sleep(120);

        String stepResult = executeStepDirectly(currentStep, location);
        if (stepResult == null || stepResult.isBlank()) {
            stepResult = executeStepWithLLM(currentStep, location, accumulatedContext);
        }

        double stepDuration = (System.currentTimeMillis() - stepStartTime) / 1000.0;

        Map<String, String> results = new LinkedHashMap<>(state.getStepResults());
        results.put(String.valueOf(currentIndex), stepResult);
        String newContext = accumulatedContext + "\nStep " + (currentIndex + 1) + ": " + stepResult;

        emit(Map.of("type", "step", "message", "完成：" + currentStep,
                "status", "done", "index", currentIndex, "total", steps.size(),
                "duration", stepDuration));

        Map<String, Object> update = new HashMap<>();
        update.put(PlanExecuteState.STEP_RESULTS, results);
        update.put(PlanExecuteState.ACCUMULATED_CONTEXT, newContext);
        update.put(PlanExecuteState.CURRENT_STEP_INDEX, String.valueOf(currentIndex + 1));
        update.put(PlanExecuteState.ACTION, "continue");
        if (!candidatesByKeyword.isEmpty()) {
            update.put(PlanExecuteState.CANDIDATE_POIS, copyCandidates());
        }
        if (!selectedPois.isEmpty()) {
            update.put(PlanExecuteState.SELECTED_POIS, getSelectedPois());
            update.put(PlanExecuteState.ITINERARY_ENRICHED, true);
        }
        return update;
    }

    private String executeStepDirectly(String step, String location) {
        if (isFinalizeStep(step)) {
            return finalizeItineraryDetails();
        }

        String keyword = extractKeyword(step);
        if (keyword == null) {
            return null;
        }

        String city = AmapTools.extractCity(location);
        String geoResult = toolRegistry.amapMcpTools().mapsGeo(JSONUtil.createObj()
                .set("address", location)
                .set("city", city)
                .toString());
        double[] coords = parseMcpGeo(geoResult);
        if (coords == null) {
            return "无法通过高德 MCP 定位：" + location;
        }

        String aroundResult = toolRegistry.amapMcpTools().mapsAroundSearch(JSONUtil.createObj()
                .set("keywords", keyword)
                .set("location", coords[0] + "," + coords[1])
                .set("radius", "3000")
                .toString());
        List<Map<String, Object>> pois = parseMcpPois(aroundResult);
        if (pois.isEmpty()) {
            return keyword + "：高德 MCP 未找到候选地点";
        }

        candidatesByKeyword.put(keyword, pois);
        emitPoiSection(keyword, pois);
        return keyword + "：找到 " + pois.size() + " 个候选地点，稍后统一选定最终行程点";
    }

    private boolean isFinalizeStep(String step) {
        if (step == null) {
            return false;
        }
        String lower = step.toLowerCase();
        return step.contains("详情")
                || step.contains("璇︽儏")
                || step.contains("总结")
                || step.contains("鎬荤粨")
                || step.contains("生成")
                || step.contains("鐢熸垚")
                || lower.contains("pdf");
    }

    private String finalizeItineraryDetails() {
        if (!selectedPois.isEmpty()) {
            return "已确定 " + selectedPois.size() + " 个行程点，并完成详情和图片补充";
        }

        List<Map.Entry<String, Map<String, Object>>> itinerary = selectItineraryCandidates();
        if (itinerary.isEmpty()) {
            return null;
        }

        int count = 0;
        for (Map.Entry<String, Map<String, Object>> entry : itinerary) {
            Map<String, Object> poi = entry.getValue();
            String detail = "";
            String id = String.valueOf(poi.getOrDefault("id", ""));
            if (!id.isBlank()) {
                detail = toolRegistry.amapMcpTools().mapsSearchDetail(JSONUtil.createObj().set("id", id).toString());
                enrichPoiFromDetail(poi, detail);
            }
            addSelectedPoi(poi);
            emitPlaceDetail(entry.getKey(), poi, detail);
            count++;
        }

        return "已从候选结果中确定 " + count + " 个最终行程点，并统一补充详情和图片";
    }

    private List<Map.Entry<String, Map<String, Object>>> selectItineraryCandidates() {
        List<Map.Entry<String, Map<String, Object>>> itinerary = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (Map.Entry<String, List<Map<String, Object>>> group : candidatesByKeyword.entrySet()) {
            for (Map<String, Object> poi : group.getValue()) {
                String key = uniquePoiKey(poi);
                if (!seen.add(key)) {
                    continue;
                }
                itinerary.add(Map.entry(group.getKey(), new LinkedHashMap<>(poi)));
                break;
            }
            if (itinerary.size() == MAX_ITINERARY_POIS) {
                break;
            }
        }

        return itinerary;
    }

    private String executeStepWithLLM(String step, String location, String accumulatedContext) {
        String prompt = """
                You are executing one step of a date planning agent.
                Use AMap MCP tools for geocoding, POI search, POI detail, and route data.
                Use web search or scraping only when MCP data is insufficient.

                Step: %s
                Location: %s
                Previous context:
                %s

                Return a concise Chinese summary under 200 Chinese characters.
                """.formatted(step, location,
                accumulatedContext.length() > 800 ? accumulatedContext.substring(0, 800) : accumulatedContext);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(prompt));
        messages.add(new UserMessage("执行这个步骤"));

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            ChatRequest request = ChatRequest.builder()
                    .messages(messages)
                    .toolSpecifications(toolSpecs)
                    .build();
            ChatResponse response = chatModel.chat(request);
            AiMessage aiMessage = response.aiMessage();

            if (aiMessage.hasToolExecutionRequests()) {
                messages.add(aiMessage);
                for (ToolExecutionRequest toolReq : aiMessage.toolExecutionRequests()) {
                    String toolResult = toolRegistry.execute(toolReq.name(), toolReq.arguments());
                    messages.add(new ToolExecutionResultMessage(toolReq.id(), toolReq.name(), toolResult));
                }
            } else {
                result.append(aiMessage.text());
                break;
            }
        }
        return result.toString();
    }

    private void emitPoiSection(String keyword, List<Map<String, Object>> pois) {
        List<Map<String, Object>> items = new ArrayList<>();
        int limit = Math.min(pois.size(), MAX_CANDIDATES_PER_CATEGORY);
        for (Map<String, Object> poi : pois.subList(0, limit)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", poi.get("id"));
            item.put("name", poi.get("name"));
            item.put("address", poi.get("address"));
            item.put("distance", poi.get("distance"));
            items.add(item);
        }
        emit(Map.of("type", "section", "title", keyword, "icon", getCatIcon(keyword), "items", items));
    }

    private void emitPlaceDetail(String keyword, Map<String, Object> poi, String detail) {
        List<String> images = parseMcpPhotos(detail);
        Object image = poi.get("image");
        if (images.isEmpty() && image != null && !String.valueOf(image).isBlank()) {
            images = List.of(String.valueOf(image));
        }

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "placeDetail");
        event.put("keyword", keyword);
        event.put("placeName", poi.getOrDefault("name", ""));
        event.put("address", poi.getOrDefault("address", ""));
        event.put("rating", poi.getOrDefault("rating", ""));
        event.put("cost", poi.getOrDefault("cost", ""));
        event.put("openTime", poi.getOrDefault("open_time", poi.getOrDefault("opentime2", "")));
        event.put("images", images);
        emit(event);
    }

    private void enrichPoiFromDetail(Map<String, Object> poi, String detail) {
        List<Map<String, Object>> detailPois = parseMcpPois(detail);
        if (detailPois.isEmpty()) {
            return;
        }
        Map<String, Object> detailed = detailPois.get(0);
        detailed.forEach((key, value) -> {
            if (value != null && !String.valueOf(value).isBlank()) {
                poi.putIfAbsent(key, value);
                if ("longitude".equals(key) || "latitude".equals(key) || "image".equals(key)) {
                    poi.put(key, value);
                }
            }
        });
    }

    static String extractKeyword(String step) {
        if (step == null) {
            return null;
        }
        Map<String, String> keywords = new LinkedHashMap<>();
        keywords.put("鍜栧暋鍘?", "咖啡厅");
        keywords.put("咖啡厅", "咖啡厅");
        keywords.put("茶馆", "茶馆");
        keywords.put("公园", "公园");
        keywords.put("景点", "景点");
        keywords.put("观景台", "观景台");
        keywords.put("西餐厅", "西餐厅");
        keywords.put("餐厅", "餐厅");
        keywords.put("甜品店", "甜品店");
        keywords.put("花店", "花店");
        keywords.put("酒吧", "酒吧");
        keywords.put("书店", "书店");
        keywords.put("火锅", "火锅");
        keywords.put("鑼堕", "茶馆");
        keywords.put("鍏囯", "公园");
        keywords.put("鏅偣", "景点");
        keywords.put("瑙櫙鍙?", "观景台");
        keywords.put("瑗块鍘?", "西餐厅");
        keywords.put("椁巺", "餐厅");
        keywords.put("鐢滃搧搴?", "甜品店");
        keywords.put("鑺卞簵", "花店");
        keywords.put("閰掑惂", "酒吧");
        keywords.put("涔﹀簵", "书店");
        keywords.put("鐏攨", "火锅");
        keywords.put("KTV", "KTV");
        keywords.put("VR", "VR");

        for (Map.Entry<String, String> entry : keywords.entrySet()) {
            if (step.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String getCatIcon(String label) {
        if (label == null) return "📍";
        if (label.contains("花") || label.contains("鑺?")) return "💐";
        if (label.contains("咖啡") || label.contains("茶") || label.contains("鍜栧暋") || label.contains("鑼?")) return "☕";
        if (label.contains("公园") || label.contains("景点") || label.contains("观景") || label.contains("鍏洯") || label.contains("鏅偣") || label.contains("瑙傛櫙")) return "🌿";
        if (label.contains("餐") || label.contains("火锅") || label.contains("椁?") || label.contains("鐏攨")) return "🍽️";
        if (label.contains("甜品") || label.contains("鐢滃搧")) return "🍰";
        return "📍";
    }

    private void addSelectedPoi(Map<String, Object> poi) {
        double lon = toDouble(poi.get("longitude"));
        double lat = toDouble(poi.get("latitude"));
        if (lon == 0 || lat == 0) {
            return;
        }
        String key = uniquePoiKey(poi);
        for (Map<String, Object> existing : selectedPois) {
            if (uniquePoiKey(existing).equals(key)) {
                return;
            }
        }

        Map<String, Object> selected = new LinkedHashMap<>();
        selected.put("id", poi.get("id"));
        selected.put("name", poi.get("name"));
        selected.put("address", poi.get("address"));
        selected.put("longitude", lon);
        selected.put("latitude", lat);
        copyIfPresent(selected, poi, "rating");
        copyIfPresent(selected, poi, "cost");
        copyIfPresent(selected, poi, "open_time");
        copyIfPresent(selected, poi, "opentime2");
        copyIfPresent(selected, poi, "image");
        copyIfPresent(selected, poi, "type");
        selectedPois.add(selected);
    }

    private static void copyIfPresent(Map<String, Object> target, Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value != null && !String.valueOf(value).isBlank()) {
            target.put(key, value);
        }
    }

    public List<Map<String, Object>> getSelectedPois() {
        return selectedPois.isEmpty() ? null : new ArrayList<>(selectedPois);
    }

    private Map<String, List<Map<String, Object>>> copyCandidates() {
        Map<String, List<Map<String, Object>>> copy = new LinkedHashMap<>();
        candidatesByKeyword.forEach((keyword, pois) -> {
            List<Map<String, Object>> items = new ArrayList<>();
            for (Map<String, Object> poi : pois) {
                items.add(new LinkedHashMap<>(poi));
            }
            copy.put(keyword, items);
        });
        return copy;
    }

    private static String uniquePoiKey(Map<String, Object> poi) {
        String id = String.valueOf(poi.getOrDefault("id", ""));
        if (!id.isBlank()) {
            return id;
        }
        return poi.getOrDefault("name", "") + "|"
                + poi.getOrDefault("longitude", "") + "|"
                + poi.getOrDefault("latitude", "");
    }

    static double[] parseMcpGeo(String result) {
        try {
            Object parsed = JSONUtil.parse(result);
            if (parsed instanceof cn.hutool.json.JSON json) {
                String location = findLocation(json);
                if (location != null) {
                    return parseLocation(location);
                }
            }
        } catch (Exception ignored) {
            String location = findLocationInText(result);
            if (location != null) {
                return parseLocation(location);
            }
        }
        return null;
    }

    static List<Map<String, Object>> parseMcpPois(String result) {
        List<Map<String, Object>> pois = new ArrayList<>();
        try {
            Object parsed = JSONUtil.parse(result);
            collectPois(parsed, pois);
        } catch (Exception ignored) {
        }
        return pois;
    }

    private static void collectPois(Object value, List<Map<String, Object>> pois) {
        if (value instanceof JSONArray arr) {
            for (Object item : arr) {
                collectPois(item, pois);
            }
            return;
        }
        if (!(value instanceof JSONObject obj)) {
            return;
        }

        if (obj.containsKey("id") || obj.containsKey("poiid") || obj.containsKey("name")) {
            Map<String, Object> poi = new LinkedHashMap<>();
            poi.put("id", obj.getStr("id", obj.getStr("poiid", "")));
            poi.put("name", obj.getStr("name", ""));
            poi.put("address", obj.getStr("address", ""));
            poi.put("distance", obj.getStr("distance", ""));
            copyJsonField(obj, poi, "rating");
            copyJsonField(obj, poi, "cost");
            copyJsonField(obj, poi, "open_time");
            copyJsonField(obj, poi, "opentime2");
            copyJsonField(obj, poi, "type");

            List<String> photos = collectPhotoUrls(value);
            if (!photos.isEmpty()) {
                poi.put("image", photos.get(0));
            }

            double[] coords = parseLocation(obj.getStr("location", ""));
            if (coords == null) {
                coords = parseLonLatFields(obj);
            }
            if (coords != null) {
                poi.put("longitude", coords[0]);
                poi.put("latitude", coords[1]);
            }
            if (!String.valueOf(poi.getOrDefault("id", "")).isBlank()
                    || !String.valueOf(poi.getOrDefault("name", "")).isBlank()) {
                pois.add(poi);
            }
        }

        for (String key : obj.keySet()) {
            collectPois(obj.get(key), pois);
        }
    }

    private static void copyJsonField(JSONObject source, Map<String, Object> target, String key) {
        if (!source.containsKey(key)) {
            return;
        }
        Object value = source.get(key);
        if (value != null && !String.valueOf(value).isBlank()) {
            target.put(key, value);
        }
    }

    private static String findLocation(cn.hutool.json.JSON json) {
        if (json instanceof JSONObject obj) {
            if (obj.containsKey("location")) {
                return obj.getStr("location");
            }
            for (String key : obj.keySet()) {
                Object value = obj.get(key);
                if (value instanceof cn.hutool.json.JSON nested) {
                    String found = findLocation(nested);
                    if (found != null) return found;
                }
            }
        }
        if (json instanceof JSONArray arr) {
            for (Object item : arr) {
                if (item instanceof cn.hutool.json.JSON nested) {
                    String found = findLocation(nested);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    private static String findLocationInText(String text) {
        if (text == null) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\d{2,3}\\.\\d+),(\\d{1,2}\\.\\d+)")
                .matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    private static double[] parseLocation(String location) {
        if (location == null || !location.contains(",")) return null;
        String[] parts = location.split(",");
        try {
            return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double[] parseLonLatFields(JSONObject obj) {
        String[][] fieldPairs = {
                {"longitude", "latitude"},
                {"lng", "lat"},
                {"lon", "lat"}
        };
        for (String[] pair : fieldPairs) {
            if (obj.containsKey(pair[0]) && obj.containsKey(pair[1])) {
                double lon = parseDouble(obj.get(pair[0]));
                double lat = parseDouble(obj.get(pair[1]));
                if (lon != 0 && lat != 0) {
                    return new double[]{lon, lat};
                }
            }
        }

        Object location = obj.get("location");
        if (location instanceof JSONObject nested) {
            double lon = parseDouble(nested.get("longitude"));
            double lat = parseDouble(nested.get("latitude"));
            if (lon == 0 || lat == 0) {
                lon = parseDouble(nested.get("lng"));
                lat = parseDouble(nested.get("lat"));
            }
            if (lon != 0 && lat != 0) {
                return new double[]{lon, lat};
            }
        }
        return null;
    }

    private static double parseDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private List<String> parseMcpPhotos(String detail) {
        if (detail == null || detail.isBlank()) {
            return List.of();
        }
        try {
            return collectPhotoUrls(JSONUtil.parse(detail));
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static List<String> collectPhotoUrls(Object value) {
        List<String> photos = new ArrayList<>();
        collectPhotoUrls(value, photos);
        return photos;
    }

    private static void collectPhotoUrls(Object value, List<String> photos) {
        if (photos.size() >= 5 || value == null) return;
        if (value instanceof JSONArray arr) {
            for (Object item : arr) collectPhotoUrls(item, photos);
            return;
        }
        if (value instanceof JSONObject obj) {
            if (obj.containsKey("url")) {
                String url = obj.getStr("url", "");
                if (url.startsWith("http")) photos.add(url);
            }
            for (String key : obj.keySet()) collectPhotoUrls(obj.get(key), photos);
        }
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        if (value == null) return 0;
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void emit(Map<String, Object> event) {
        if (onEvent != null) {
            onEvent.accept(event);
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}

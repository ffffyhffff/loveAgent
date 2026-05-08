package com.aichat.app.graph.nodes;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aichat.app.graph.DatePlanState;
import com.aichat.app.tools.McpMapTools;
import com.aichat.app.tools.WebSearchTool;
import com.aichat.app.tools.WebScrapingTool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
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
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Consumer;

/**
 * 搜索 POI 节点（MCP + AI 自主决策 + 内容可视化）
 *
 * LLM 通过 function calling 自主决定搜索什么、搜几类、搜评价。
 * 每个工具调用结果实时推送到前端展示。
 */
@Component
@Slf4j
public class SearchPoiNode implements NodeAction {

    private final ChatLanguageModel chatModel;
    private final McpMapTools mcpMapTools;
    private final WebSearchTool webSearchTool;
    private final WebScrapingTool webScrapingTool;
    private final List<ToolSpecification> allToolSpecs;

    public SearchPoiNode(ChatLanguageModel chatModel, McpMapTools mcpMapTools) {
        this.chatModel = chatModel;
        this.mcpMapTools = mcpMapTools;
        this.webSearchTool = new WebSearchTool();
        this.webScrapingTool = new WebScrapingTool();

        // 合并所有工具规格
        this.allToolSpecs = new ArrayList<>();
        this.allToolSpecs.addAll(mcpMapTools.getToolSpecifications());
        this.allToolSpecs.addAll(ToolSpecifications.toolSpecificationsFrom(webSearchTool));
        this.allToolSpecs.addAll(ToolSpecifications.toolSpecificationsFrom(webScrapingTool));
        log.info("注册工具总数: {} (地图:{} + 搜索:{} + 抓取:{})",
                allToolSpecs.size(),
                mcpMapTools.getToolSpecifications().size(),
                ToolSpecifications.toolSpecificationsFrom(webSearchTool).size(),
                ToolSpecifications.toolSpecificationsFrom(webScrapingTool).size());
    }

    @Override
    public Map<String, Object> apply(AgentState agentState) throws Exception {
        return applyWithCallback(agentState, null);
    }

    /**
     * 带回调的执行方法，每个工具调用结果实时推送到前端
     */
    public Map<String, Object> applyWithCallback(AgentState agentState,
                                                  Consumer<Map<String, Object>> onEvent) throws Exception {
        DatePlanState state = (DatePlanState) agentState;
        String location = state.getDateLocation();
        String budget = state.getDateBudget();
        String style = state.getDateStyle();
        String activity = state.getDateActivity();
        String occasion = state.getDateOccasion();

        log.info("搜索 POI 节点（MCP+可视化）：location={}, style={}, activity={}, occasion={}",
                location, style, activity, occasion);

        Map<String, Object> update = new HashMap<>();

        String systemPrompt = """
                你是一个约会规划助手，负责搜索约会目的地和获取评价信息。

                你的任务：
                1. 根据用户的约会偏好，搜索 3-5 类不同的约会地点
                2. 对推荐的重点地点，搜索网上的评价和评分
                3. 最后给出一段温馨的推荐总结

                工作流程：
                1. 先调用 geocode 获取约会地点的经纬度
                2. 调用 aroundSearch 搜索各类地点（每类 3-5 个）
                3. 对搜到的推荐地点，调用 searchReviews 搜索评价
                4. 最后用纯文本写一段推荐总结

                规则：
                - 先搜坐标，再搜附近地点
                - 搜索关键词要根据约会场景选择（求婚→花店+观景台+西餐厅；休闲→咖啡厅+公园+餐厅）
                - 每类搜 3-5 个
                - 对最好的 1-2 个地点搜索评价
                - 最终总结要温馨，包含推荐路线
                """;

        String userPrefs = String.format(
                "约会地点：%s\n预算：%s\n风格：%s\n活动偏好：%s\n场景：%s",
                location,
                budget != null ? budget : "不限",
                style != null ? style : "浪漫",
                activity != null ? activity : "不限",
                occasion != null ? occasion : "普通约会"
        );

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(userPrefs));

        // 收集工具调用结果
        Map<String, String> toolResultsByKeyword = new LinkedHashMap<>();

        int maxIterations = 12;
        for (int i = 0; i < maxIterations; i++) {
            log.info("工具调用循环，第 {} 轮", i + 1);

            ChatRequest request = ChatRequest.builder()
                    .messages(messages)
                    .toolSpecifications(allToolSpecs)
                    .build();
            ChatResponse response = chatModel.chat(request);
            AiMessage aiMessage = response.aiMessage();

            if (aiMessage.hasToolExecutionRequests()) {
                messages.add(aiMessage);

                for (ToolExecutionRequest toolRequest : aiMessage.toolExecutionRequests()) {
                    String toolName = toolRequest.name();
                    String args = toolRequest.arguments();

                    // 解析关键词用于事件推送
                    JSONObject jsonArgs = safeParseJson(args);
                    String keyword = extractKeyword(toolName, jsonArgs);

                    // 推送 step: 开始执行
                    if (onEvent != null && keyword != null) {
                        onEvent.accept(Map.of(
                                "type", "step",
                                "message", "正在搜索" + keyword + "...",
                                "status", "active"
                        ));
                    }

                    log.info("  工具调用: {}({})", toolName, args);
                    String result = executeTool(toolName, args);
                    log.info("  结果: {}", result.length() > 100 ? result.substring(0, 100) + "..." : result);

                    // 推送内容事件
                    if (onEvent != null) {
                        pushContentEvent(onEvent, toolName, jsonArgs, result, keyword);
                    }

                    // 收集搜索结果
                    if (("aroundSearch".equals(toolName) || "searchPoi".equals(toolName))
                            && result.startsWith("[") && keyword != null) {
                        toolResultsByKeyword.put(keyword, result);
                    }

                    messages.add(new ToolExecutionResultMessage(toolRequest.id(), toolName, result));
                }
            } else {
                String finalResult = aiMessage.text();
                log.info("LLM 最终结果:\n{}", finalResult);

                // 推送攻略总结
                if (onEvent != null && finalResult != null && !finalResult.isEmpty()) {
                    onEvent.accept(Map.of("type", "text", "content", finalResult));
                }

                // 构建 categories
                if (!toolResultsByKeyword.isEmpty()) {
                    buildCategoriesFromToolResults(toolResultsByKeyword, update);
                }
                break;
            }
        }

        return update;
    }

    /**
     * 根据工具类型推送内容事件
     */
    private void pushContentEvent(Consumer<Map<String, Object>> onEvent,
                                   String toolName, JSONObject args,
                                   String result, String keyword) {
        switch (toolName) {
            case "aroundSearch", "searchPoi" -> {
                // 推送 section：POI 卡片列表
                if (result.startsWith("[")) {
                    List<Map<String, Object>> items = parsePoiItems(result);
                    if (!items.isEmpty()) {
                        onEvent.accept(Map.of(
                                "type", "step",
                                "message", "搜索" + keyword + "完成，找到 " + items.size() + " 个",
                                "status", "done"
                        ));
                        onEvent.accept(Map.of(
                                "type", "section",
                                "title", keyword,
                                "icon", getCatIcon(keyword),
                                "items", items
                        ));
                    }
                }
            }
            case "searchReviews" -> {
                // 推送评价
                String placeName = args != null ? args.getStr("placeName", "") : "";
                if (!result.isEmpty() && !result.contains("失败")) {
                    onEvent.accept(Map.of(
                            "type", "step",
                            "message", "搜索评价完成",
                            "status", "done"
                    ));
                    onEvent.accept(Map.of(
                            "type", "review",
                            "placeName", placeName,
                            "content", result.length() > 300 ? result.substring(0, 300) + "..." : result
                    ));
                }
            }
            case "geocode" -> {
                if (onEvent != null) {
                    onEvent.accept(Map.of(
                            "type", "step",
                            "message", "定位完成",
                            "status", "done"
                    ));
                }
            }
        }
    }

    private String extractKeyword(String toolName, JSONObject args) {
        if (args == null) return null;
        return switch (toolName) {
            case "aroundSearch", "searchPoi" -> args.getStr("keyword", "地点");
            case "searchReviews" -> args.getStr("placeName", "") + " 的评价";
            case "geocode" -> null;
            default -> toolName;
        };
    }

    private JSONObject safeParseJson(String json) {
        try { return JSONUtil.parseObj(json); } catch (Exception e) { return null; }
    }

    private List<Map<String, Object>> parsePoiItems(String jsonStr) {
        List<Map<String, Object>> items = new ArrayList<>();
        try {
            JSONArray arr = JSONUtil.parseArray(jsonStr);
            for (int i = 0; i < arr.size(); i++) {
                JSONObject poi = arr.getJSONObject(i);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", poi.getStr("name"));
                m.put("address", poi.getStr("address"));
                m.put("distance", poi.getStr("distance"));
                m.put("longitude", poi.getDouble("longitude"));
                m.put("latitude", poi.getDouble("latitude"));
                items.add(m);
            }
        } catch (Exception ignored) {}
        return items;
    }

    private String getCatIcon(String label) {
        if (label == null) return "📍";
        if (label.contains("花") || label.contains("浪漫准备")) return "💐";
        if (label.contains("茶") || label.contains("咖啡") || label.contains("休闲")) return "☕";
        if (label.contains("景点") || label.contains("公园") || label.contains("观景") || label.contains("湖")) return "🌸";
        if (label.contains("餐") || label.contains("火锅") || label.contains("美食") || label.contains("晚")) return "🍽️";
        if (label.contains("甜品") || label.contains("甜蜜")) return "🍰";
        if (label.contains("书店") || label.contains("文艺") || label.contains("展览")) return "📚";
        if (label.contains("酒吧") || label.contains("微醺")) return "🍷";
        if (label.contains("小吃")) return "🍜";
        if (label.contains("评")) return "💬";
        return "📍";
    }

    private String executeTool(String toolName, String args) {
        try {
            JSONObject json = JSONUtil.parseObj(args);
            return switch (toolName) {
                case "geocode" -> mcpMapTools.geocode(json.getStr("address", ""), json.getStr("city", null));
                case "searchPoi" -> mcpMapTools.searchPoi(json.getStr("keyword", ""), json.getStr("city", ""), json.getInt("pageSize", 5));
                case "aroundSearch" -> mcpMapTools.aroundSearch(json.getDouble("longitude", 0.0), json.getDouble("latitude", 0.0), json.getStr("keyword", ""), json.getInt("radius", 3000));
                case "walkingRoute" -> mcpMapTools.walkingRoute(json.getDouble("originLon", 0.0), json.getDouble("originLat", 0.0), json.getDouble("destLon", 0.0), json.getDouble("destLat", 0.0));
                case "searchWeb" -> webSearchTool.searchWeb(json.getStr("query", ""));
                case "searchReviews" -> webSearchTool.searchReviews(json.getStr("placeName", ""));
                case "scrapeWebPage" -> webScrapingTool.scrapeWebPage(json.getStr("url", ""));
                default -> "未知工具: " + toolName;
            };
        } catch (Exception e) {
            log.error("工具执行失败: {}({})", toolName, args, e);
            return "工具执行失败: " + e.getMessage();
        }
    }

    private void buildCategoriesFromToolResults(Map<String, String> toolResults, Map<String, Object> update) {
        List<Map<String, Object>> categoryList = new ArrayList<>();
        Map<String, Integer> selectedMap = new LinkedHashMap<>();
        int catIndex = 0;

        for (Map.Entry<String, String> entry : toolResults.entrySet()) {
            String keyword = entry.getKey();
            String jsonStr = entry.getValue();
            try {
                JSONArray arr = JSONUtil.parseArray(jsonStr);
                List<Map<String, Object>> pois = new ArrayList<>();
                for (int i = 0; i < arr.size(); i++) {
                    JSONObject poi = arr.getJSONObject(i);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", poi.getStr("name"));
                    m.put("address", poi.getStr("address"));
                    m.put("distance", poi.getStr("distance"));
                    m.put("longitude", poi.getDouble("longitude"));
                    m.put("latitude", poi.getDouble("latitude"));
                    pois.add(m);
                }
                if (!pois.isEmpty()) {
                    String key = "cat_" + catIndex;
                    Map<String, Object> catData = new LinkedHashMap<>();
                    catData.put("label", keyword);
                    catData.put("key", key);
                    catData.put("items", pois);
                    categoryList.add(catData);
                    selectedMap.put(key, 0);
                    catIndex++;
                }
            } catch (Exception e) {
                log.warn("解析工具结果失败: keyword={}", keyword, e);
            }
        }

        update.put("poiCategories", categoryList);
        update.put("poiSelected", selectedMap);

        if (categoryList.size() >= 1) {
            List<Map<String, Object>> items0 = (List<Map<String, Object>>) categoryList.get(0).get("items");
            if (!items0.isEmpty()) update.put(DatePlanState.SELECTED_CAFE, items0.get(0));
        }
        if (categoryList.size() >= 2) {
            List<Map<String, Object>> items1 = (List<Map<String, Object>>) categoryList.get(1).get("items");
            if (!items1.isEmpty()) update.put(DatePlanState.SELECTED_SPOT, items1.get(0));
        }
        if (categoryList.size() >= 3) {
            List<Map<String, Object>> items2 = (List<Map<String, Object>>) categoryList.get(2).get("items");
            if (!items2.isEmpty()) update.put(DatePlanState.SELECTED_RESTAURANT, items2.get(0));
        }
    }
}

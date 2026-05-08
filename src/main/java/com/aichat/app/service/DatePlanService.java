package com.aichat.app.service;

import com.aichat.app.graph.DatePlanState;
import com.aichat.app.graph.nodes.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Consumer;

/**
 * 约会规划服务 - 分阶段执行
 *
 * Phase 1: extractAndPlan() → AgentNode + PlanNode → 返回计划供用户确认
 * Phase 2: executeApproved() → SearchPoiNode → RouteNode → PdfNode → 返回 POI/地图/PDF
 */
@Service
@Slf4j
public class DatePlanService {

    // 直接注入各个节点，分阶段调用
    private final AgentNode agentNode;
    private final PlanNode planNode;
    private final SearchPoiNode searchPoiNode;
    private final ChoosePoiNode choosePoiNode;
    private final RouteNode routeNode;
    private final PdfNode pdfNode;

    public DatePlanService(AgentNode agentNode, PlanNode planNode,
                           SearchPoiNode searchPoiNode, ChoosePoiNode choosePoiNode,
                           RouteNode routeNode, PdfNode pdfNode) {
        this.agentNode = agentNode;
        this.planNode = planNode;
        this.searchPoiNode = searchPoiNode;
        this.choosePoiNode = choosePoiNode;
        this.routeNode = routeNode;
        this.pdfNode = pdfNode;
    }

    /**
     * 判断是否是约会规划任务
     */
    public boolean isComplexTask(String message) {
        List<String> keywords = List.of(
                "规划", "计划", "安排", "路线", "行程",
                "约会", "旅行", "推荐路线", "推荐地点",
                "帮我找", "去哪里", "怎么去"
        );
        String lower = message.toLowerCase();
        return keywords.stream().anyMatch(lower::contains);
    }

    /**
     * Phase 1: 提取用户意图
     * 只调用 AgentNode 提取信息，不生成计划
     * 用户消息不够具体时弹窗收集偏好
     */
    public PlanResult extractIntent(String userMessage) {
        try {
            DatePlanState state = DatePlanState.fromMessage(userMessage);
            Map<String, Object> agentUpdate = agentNode.apply(state);
            state = mergeState(state, agentUpdate);

            String action = state.getAction();
            if ("chat".equals(action)) {
                return PlanResult.chat(state.getAiResponse());
            }

            PlanResult result = new PlanResult();
            result.type = "plan";
            result.location = state.getDateLocation();
            result.budget = state.getDateBudget();
            result.style = state.getDateStyle();
            result.duration = state.getDateDuration();
            result.keywords = state.getDateKeywords();
            result.occasion = state.getDateOccasion();
            result.activity = state.getDateActivity();

            // 判断用户是否明确提供了足够信息
            boolean hasExplicitLocation = containsLocationKeyword(userMessage);
            boolean hasExplicitBudget = userMessage.contains("元") || userMessage.contains("块")
                    || userMessage.contains("预算") || userMessage.contains("budget")
                    || userMessage.matches(".*\\d{2,}.*");
            boolean hasExplicitStyle = userMessage.contains("浪漫") || userMessage.contains("休闲")
                    || userMessage.contains("冒险") || userMessage.contains("文艺")
                    || userMessage.contains("美食") || userMessage.contains("安静")
                    || userMessage.contains("刺激") || userMessage.contains("户外");
            // 场景和活动偏好：不是每次都必须，但没提到就问
            boolean hasExplicitOccasion = userMessage.contains("纪念日") || userMessage.contains("生日")
                    || userMessage.contains("求婚") || userMessage.contains("第一次约会")
                    || userMessage.contains("初次约会");
            boolean hasExplicitActivity = userMessage.contains("放松") || userMessage.contains("散步")
                    || userMessage.contains("运动") || userMessage.contains("户外")
                    || userMessage.contains("展览") || userMessage.contains("书店")
                    || userMessage.contains("探店") || userMessage.contains("密室")
                    || userMessage.contains("咖啡") || userMessage.contains("酒吧");

            // 只要缺任何一项，就弹窗收集
            if (!hasExplicitLocation || !hasExplicitBudget || !hasExplicitStyle
                    || !hasExplicitOccasion || !hasExplicitActivity) {
                result.needPreferences = true;
                log.info("Phase 1: 信息不全，弹窗收集 (loc={}, budget={}, style={}, occasion={}, activity={})",
                        hasExplicitLocation, hasExplicitBudget, hasExplicitStyle,
                        hasExplicitOccasion, hasExplicitActivity);
                return result;
            }

            // 信息完整 → 直接执行
            log.info("Phase 1: 信息完整，执行计划 - {}", result.location);
            return result;

        } catch (Exception e) {
            log.error("Phase 1 失败", e);
            return PlanResult.error(e.getMessage());
        }
    }

    /**
     * 检查用户消息是否包含地点关键词
     */
    private boolean containsLocationKeyword(String msg) {
        String[] locationHints = {
            "北京", "上海", "广州", "深圳", "杭州", "成都", "重庆", "武汉", "南京", "西安",
            "苏州", "天津", "长沙", "郑州", "青岛", "厦门", "昆明", "合肥", "福州", "济南",
            "西湖", "外滩", "故宫", "长城", "浦东", "朝阳", "海淀", "徐汇", "天河", "南山",
            "区", "市", "路", "街", "广场", "商圈", "大学城", "景区", "公园"
        };
        for (String hint : locationHints) {
            if (msg.contains(hint)) return true;
        }
        return false;
    }

    /**
     * Phase 1b: 用完整信息生成计划
     */
    public PlanResult generatePlan(PlanResult info) {
        try {
            DatePlanState state = DatePlanState.fromMessage("");
            Map<String, Object> init = new HashMap<>();
            init.put(DatePlanState.DATE_LOCATION, info.location);
            init.put(DatePlanState.DATE_BUDGET, info.budget);
            init.put(DatePlanState.DATE_STYLE, info.style);
            init.put(DatePlanState.DATE_DURATION, info.duration);
            init.put(DatePlanState.DATE_KEYWORDS, info.keywords);
            init.put(DatePlanState.ACTION, "plan");
            state = DatePlanState.create(init);

            Map<String, Object> planUpdate = planNode.apply(state);
            state = mergeState(state, planUpdate);

            PlanResult result = new PlanResult();
            result.type = "plan";
            result.planDescription = state.getPlanDescription();
            result.location = info.location;
            result.budget = info.budget;
            result.style = info.style;
            result.duration = info.duration;
            result.keywords = info.keywords;
            result.needPreferences = false;

            log.info("计划生成完成");
            return result;

        } catch (Exception e) {
            log.error("计划生成失败", e);
            return PlanResult.error(e.getMessage());
        }
    }

    /**
     * Phase 2: 确认后执行 - 搜索 POI → 选点 → 路线 → PDF
     */
    public ExecuteResult executeApproved(String location, String budget, String style,
                                          String occasion, String activity) {
        return executeWithCallback(location, budget, style, occasion, activity, null);
    }

    /**
     * Phase 2（带回调）：搜索 POI → 选点 → 路线 → PDF
     * 每个步骤的中间结果通过 onEvent 回调实时推送到前端
     */
    public ExecuteResult executeWithCallback(String location, String budget, String style,
                                              String occasion, String activity,
                                              Consumer<Map<String, Object>> onEvent) {
        try {
            // 构造初始状态
            DatePlanState state = DatePlanState.fromMessage("");
            Map<String, Object> init = new HashMap<>();
            init.put(DatePlanState.DATE_LOCATION, location);
            init.put(DatePlanState.DATE_BUDGET, budget);
            init.put(DatePlanState.DATE_STYLE, style);
            if (occasion != null && !occasion.isBlank()) init.put(DatePlanState.DATE_OCCASION, occasion);
            if (activity != null && !activity.isBlank()) init.put(DatePlanState.DATE_ACTIVITY, activity);
            init.put(DatePlanState.ACTION, "plan");
            state = DatePlanState.create(init);

            ExecuteResult result = new ExecuteResult();

            // Step 1: 搜索 POI（带回调实时推送）
            log.info("Phase 2: 搜索 POI - {}", location);
            Map<String, Object> searchUpdate = onEvent != null
                    ? searchPoiNode.applyWithCallback(state, onEvent)
                    : searchPoiNode.apply(state);
            state = mergeState(state, searchUpdate);

            // 收集动态分类 POI
            if (state.value("poiCategories").isPresent()) {
                result.poiCategories = (List<Map<String, Object>>) state.value("poiCategories").get();
            }
            if (state.value("poiSelected").isPresent()) {
                result.poiSelected = (Map<String, Integer>) state.value("poiSelected").get();
            }

            // 向后兼容：收集扁平 POI 列表
            List<Map<String, Object>> allPois = new ArrayList<>();
            state.value(DatePlanState.CAFES).ifPresent(v -> allPois.addAll((List<Map<String, Object>>) v));
            state.value(DatePlanState.SPOTS).ifPresent(v -> allPois.addAll((List<Map<String, Object>>) v));
            state.value(DatePlanState.RESTAURANTS).ifPresent(v -> allPois.addAll((List<Map<String, Object>>) v));
            result.pois = allPois;

            // Step 2: 自动选点
            Map<String, Object> chooseUpdate = choosePoiNode.apply(state);
            state = mergeState(state, chooseUpdate);

            // 收集选中的 POI
            List<Map<String, Object>> selectedPois = new ArrayList<>();
            state.value(DatePlanState.SELECTED_CAFE).ifPresent(v -> selectedPois.add((Map<String, Object>) v));
            state.value(DatePlanState.SELECTED_SPOT).ifPresent(v -> selectedPois.add((Map<String, Object>) v));
            state.value(DatePlanState.SELECTED_RESTAURANT).ifPresent(v -> selectedPois.add((Map<String, Object>) v));
            result.selectedPois = selectedPois;

            // Step 3: 路线规划
            if (selectedPois.size() >= 2) {
                log.info("Phase 2: 路线规划");
                Map<String, Object> routeUpdate = routeNode.apply(state);
                state = mergeState(state, routeUpdate);

                String distance = state.value(DatePlanState.ROUTE_DISTANCE).map(Object::toString).orElse(null);
                String duration = state.value(DatePlanState.ROUTE_DURATION).map(Object::toString).orElse(null);
                if (distance != null || duration != null) {
                    result.routeInfo = new HashMap<>();
                    if (distance != null) result.routeInfo.put("distance", distance);
                    if (duration != null) result.routeInfo.put("duration", duration);
                }
            }

            // Step 4: 生成 PDF（先补充 planDescription）
            log.info("Phase 2: 生成 PDF");
            if (state.getPlanDescription() == null || state.getPlanDescription().isEmpty()) {
                String desc = buildDefaultPlanDescription(state, location, budget, style);
                Map<String, Object> descUpdate = new HashMap<>();
                descUpdate.put(DatePlanState.PLAN_DESC, desc);
                state = mergeState(state, descUpdate);
            }
            String pdfPath = pdfNode.generatePdf(state);
            if (pdfPath != null) {
                // 返回文件名（前端通过 /api/chat/pdf/{filename} 下载）
                String filename = pdfPath.substring(pdfPath.lastIndexOf("/") + 1);
                if (filename.isEmpty()) filename = pdfPath.substring(pdfPath.lastIndexOf("\\") + 1);
                result.pdfUrl = "/api/chat/pdf/" + filename;
            }

            log.info("Phase 2 完成：POI={}个，选中={}个", result.pois.size(), result.selectedPois.size());
            return result;

        } catch (Exception e) {
            log.error("Phase 2 失败", e);
            return ExecuteResult.error(e.getMessage());
        }
    }

    /**
     * 合并节点返回的更新到状态
     */
    private DatePlanState mergeState(DatePlanState state, Map<String, Object> update) {
        Map<String, Object> newData = new HashMap<>(state.data());
        newData.putAll(update);
        return DatePlanState.create(newData);
    }

    /**
     * 从 POI 数据构建默认行程描述（用于 PDF 生成）
     */
    @SuppressWarnings("unchecked")
    private String buildDefaultPlanDescription(DatePlanState state, String location,
                                                String budget, String style) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 约会行程安排\n\n");

        Map<String, Object> cafe = (Map<String, Object>) state.value(DatePlanState.SELECTED_CAFE).orElse(null);
        Map<String, Object> spot = (Map<String, Object>) state.value(DatePlanState.SELECTED_SPOT).orElse(null);
        Map<String, Object> restaurant = (Map<String, Object>) state.value(DatePlanState.SELECTED_RESTAURANT).orElse(null);

        if (cafe != null) {
            sb.append("### 第一站：下午茶\n");
            sb.append("- **").append(cafe.get("name")).append("**\n");
            sb.append("- 地址：").append(cafe.getOrDefault("address", "暂无")).append("\n");
            sb.append("- 享受悠闲的下午时光，品尝特色饮品和甜点\n\n");
        }

        if (spot != null) {
            sb.append("### 第二站：游览景点\n");
            sb.append("- **").append(spot.get("name")).append("**\n");
            sb.append("- 地址：").append(spot.getOrDefault("address", "暂无")).append("\n");
            sb.append("- 漫步欣赏风景，留下美好回忆\n\n");
        }

        if (restaurant != null) {
            sb.append("### 第三站：浪漫晚餐\n");
            sb.append("- **").append(restaurant.get("name")).append("**\n");
            sb.append("- 地址：").append(restaurant.getOrDefault("address", "暂无")).append("\n");
            sb.append("- 享用美味晚餐，共度美好时光\n\n");
        }

        if (cafe == null && spot == null && restaurant == null) {
            sb.append("暂无具体行程安排\n");
        }

        return sb.toString();
    }

    // === 结果类 ===

    @Data
    public static class PlanResult {
        public String type; // "chat" 或 "plan" 或 "error"
        public String chatResponse;
        public String planDescription;
        public String location;
        public String budget;
        public String style;
        public String duration;
        public String keywords;
        public String occasion;
        public String activity;
        public boolean needPreferences;
        public String errorMessage;

        public static PlanResult chat(String response) {
            PlanResult r = new PlanResult();
            r.type = "chat";
            r.chatResponse = response;
            return r;
        }

        public static PlanResult error(String msg) {
            PlanResult r = new PlanResult();
            r.type = "error";
            r.errorMessage = msg;
            return r;
        }
    }

    @Data
    public static class ExecuteResult {
        public List<Map<String, Object>> pois;                    // 向后兼容：扁平 POI 列表
        public List<Map<String, Object>> selectedPois;            // 选中的 POI
        public List<Map<String, Object>> poiCategories;           // 动态分类 POI 列表
        public Map<String, Integer> poiSelected;                  // 每个分类的选中索引
        public Map<String, Object> routeInfo;
        public String pdfUrl;
        public String errorMessage;

        public static ExecuteResult error(String msg) {
            ExecuteResult r = new ExecuteResult();
            r.errorMessage = msg;
            return r;
        }
    }
}

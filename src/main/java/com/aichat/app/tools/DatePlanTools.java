package com.aichat.app.tools;

import com.aichat.app.graph.DatePlanState;
import com.aichat.app.graph.nodes.RouteNode;
import com.aichat.app.graph.nodes.PdfNode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 约会规划修改工具
 * 提供给 AI 和前端调用：搜索替代 POI、重新生成路线+PDF
 */
@Component
@Slf4j
public class DatePlanTools {

    private final AmapTools amapTools;
    private final RouteNode routeNode;
    private final PdfNode pdfNode;

    public DatePlanTools(AmapTools amapTools, RouteNode routeNode, PdfNode pdfNode) {
        this.amapTools = amapTools;
        this.routeNode = routeNode;
        this.pdfNode = pdfNode;
    }

    /**
     * 搜索替代 POI
     * @param keyword 搜索关键词（如"茶馆"）
     * @param location 地点（如"杭州西湖"）
     * @return 搜索结果列表
     */
    public List<Map<String, Object>> searchAlternative(String keyword, String location) {
        String city = AmapTools.extractCity(location);
        double[] coords = amapTools.geocode(location, city);
        if (coords == null) {
            log.warn("无法解析地址: {}", location);
            return Collections.emptyList();
        }
        List<AmapTools.PoiResult> results = amapTools.aroundSearch(coords[0], coords[1], keyword, 5000);
        List<Map<String, Object>> pois = new ArrayList<>();
        for (AmapTools.PoiResult poi : results) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", poi.getName());
            m.put("address", poi.getAddress());
            m.put("distance", poi.getDistance());
            m.put("longitude", poi.getLongitude());
            m.put("latitude", poi.getLatitude());
            pois.add(m);
        }
        log.info("搜索替代 [{}] 在 [{}] → {} 个", keyword, location, pois.size());
        return pois;
    }

    /**
     * 重新生成路线和 PDF
     * @param selectedPois 用户选中的 POI 列表（至少 2 个）
     * @param location 地点
     * @param budget 预算
     * @param style 风格
     * @return 重新生成的结果
     */
    public RegenerateResult regenerate(List<Map<String, Object>> selectedPois,
                                        String location, String budget, String style) {
        RegenerateResult result = new RegenerateResult();
        try {
            if (selectedPois == null || selectedPois.size() < 2) {
                result.errorMessage = "至少需要 2 个目的地";
                return result;
            }

            // 构造状态
            Map<String, Object> init = new LinkedHashMap<>();
            init.put(DatePlanState.DATE_LOCATION, location);
            init.put(DatePlanState.DATE_BUDGET, budget);
            init.put(DatePlanState.DATE_STYLE, style);
            init.put(DatePlanState.ACTION, "plan");

            // 设置选中的 POI（兼容老字段）
            if (selectedPois.size() >= 1) init.put(DatePlanState.SELECTED_CAFE, selectedPois.get(0));
            if (selectedPois.size() >= 2) init.put(DatePlanState.SELECTED_SPOT, selectedPois.get(1));
            if (selectedPois.size() >= 3) init.put(DatePlanState.SELECTED_RESTAURANT, selectedPois.get(2));

            DatePlanState state = DatePlanState.create(init);

            // 路线规划
            Map<String, Object> routeUpdate = routeNode.apply(state);
            Map<String, Object> merged = new HashMap<>(state.data());
            merged.putAll(routeUpdate);
            state = DatePlanState.create(merged);

            String distance = state.value(DatePlanState.ROUTE_DISTANCE).map(Object::toString).orElse(null);
            String duration = state.value(DatePlanState.ROUTE_DURATION).map(Object::toString).orElse(null);
            result.routeInfo = new LinkedHashMap<>();
            if (distance != null) result.routeInfo.put("distance", distance);
            if (duration != null) result.routeInfo.put("duration", duration);

            // 补充 planDescription
            if (state.getPlanDescription() == null || state.getPlanDescription().isEmpty()) {
                StringBuilder desc = new StringBuilder("## 约会行程安排\n\n");
                String[] icons = {"☕", "🌸", "🍽️", "🎭", "🎵"};
                for (int i = 0; i < selectedPois.size(); i++) {
                    Map<String, Object> poi = selectedPois.get(i);
                    String icon = i < icons.length ? icons[i] : "•";
                    desc.append("### ").append(icon).append(" 第").append(i + 1).append("站\n");
                    desc.append("- **").append(poi.get("name")).append("**\n");
                    desc.append("- 地址：").append(poi.getOrDefault("address", "暂无")).append("\n\n");
                }
                Map<String, Object> descUpdate = new HashMap<>();
                descUpdate.put(DatePlanState.PLAN_DESC, desc.toString());
                merged = new HashMap<>(state.data());
                merged.putAll(descUpdate);
                state = DatePlanState.create(merged);
            }

            // 生成 PDF
            String pdfPath = pdfNode.generatePdf(state);
            if (pdfPath != null) {
                String filename = pdfPath.substring(pdfPath.lastIndexOf("/") + 1);
                if (filename.isEmpty()) filename = pdfPath.substring(pdfPath.lastIndexOf("\\") + 1);
                result.pdfUrl = "/api/chat/pdf/" + filename;
            }

            result.selectedPois = selectedPois;
            log.info("重新生成完成：{}个POI，PDF={}", selectedPois.size(), result.pdfUrl);
            return result;

        } catch (Exception e) {
            log.error("重新生成失败", e);
            result.errorMessage = "重新生成失败: " + e.getMessage();
            return result;
        }
    }

    @Data
    public static class RegenerateResult {
        public List<Map<String, Object>> selectedPois;
        public Map<String, Object> routeInfo;
        public String pdfUrl;
        public String errorMessage;
    }
}

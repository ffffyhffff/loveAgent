package com.aichat.app.tools;
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
    private final PdfGenerationTool pdfGenerationTool;

    public DatePlanTools(AmapTools amapTools, PdfGenerationTool pdfGenerationTool) {
        this.amapTools = amapTools;
        this.pdfGenerationTool = pdfGenerationTool;
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

            result.routeInfo = new LinkedHashMap<>();
            if (selectedPois.size() >= 2) {
                AmapTools.RouteResult route = routeBetween(selectedPois.get(0), selectedPois.get(1));
                if (route != null) {
                    if (route.getDistance() != null) result.routeInfo.put("distance", route.getDistance());
                    if (route.getDuration() != null) result.routeInfo.put("duration", route.getDuration());
                }
            }

            // 生成 PDF
            String pdfPath = pdfGenerationTool.generate(
                    location, budget, style, "", "",
                    selectedPois, buildPlanDescription(selectedPois));
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

    private AmapTools.RouteResult routeBetween(Map<String, Object> from, Map<String, Object> to) {
        try {
            double[] origin = {toDouble(from.get("longitude")), toDouble(from.get("latitude"))};
            double[] destination = {toDouble(to.get("longitude")), toDouble(to.get("latitude"))};
            if (origin[0] == 0 || origin[1] == 0 || destination[0] == 0 || destination[1] == 0) {
                return null;
            }
            return amapTools.walkingRoute(origin, destination);
        } catch (Exception e) {
            return null;
        }
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        if (value == null) return 0;
        try { return Double.parseDouble(value.toString()); } catch (NumberFormatException e) { return 0; }
    }

    private String buildPlanDescription(List<Map<String, Object>> selectedPois) {
        StringBuilder desc = new StringBuilder("## 约会行程安排\n\n");
        String[] icons = {"☕", "🌸", "🍽️", "🎭", "🎵"};
        for (int i = 0; i < selectedPois.size(); i++) {
            Map<String, Object> poi = selectedPois.get(i);
            String icon = i < icons.length ? icons[i] : "•";
            desc.append("### ").append(icon).append(" 第").append(i + 1).append("站\n");
            desc.append("- **").append(poi.getOrDefault("name", "未知地点")).append("**\n");
            desc.append("- 地址：").append(poi.getOrDefault("address", "暂无")).append("\n\n");
        }
        return desc.toString();
    }

    @Data
    public static class RegenerateResult {
        public List<Map<String, Object>> selectedPois;
        public Map<String, Object> routeInfo;
        public String pdfUrl;
        public String errorMessage;
    }
}

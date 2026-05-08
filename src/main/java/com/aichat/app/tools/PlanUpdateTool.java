package com.aichat.app.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 约会计划更新工具
 *
 * Agent 工具，用于修改约会目的地并重新生成路线和 PDF。
 * 与高德地图工具（McpMapTools）配合使用：先用高德搜新地点，再用此工具更新计划。
 */
@Component
@Slf4j
public class PlanUpdateTool {

    private final DatePlanTools datePlanTools;

    // 当前计划信息（由调用方设置）
    private String currentLocation = "";
    private String currentBudget = "";
    private String currentStyle = "";
    private DatePlanTools.RegenerateResult lastResult;

    public PlanUpdateTool(DatePlanTools datePlanTools) {
        this.datePlanTools = datePlanTools;
    }

    public void setCurrentPlan(String location, String budget, String style) {
        this.currentLocation = location;
        this.currentBudget = budget;
        this.currentStyle = style;
    }

    public DatePlanTools.RegenerateResult getLastResult() {
        return lastResult;
    }

    @Tool("更新约会计划的目的地。当用户想换一个目的地（比如换咖啡厅、换餐厅）时，先用高德工具搜索新地点，然后调用此工具传入完整的新目的地列表来更新计划。会自动重新计算步行路线和生成新的PDF。")
    public String updatePlan(
            @dev.langchain4j.agent.tool.P("新的目的地列表，JSON数组，每个需要name,longitude,latitude。例如：[{\"name\":\"某某咖啡厅\",\"longitude\":120.13,\"latitude\":30.26},{\"name\":\"西湖公园\",\"longitude\":120.14,\"latitude\":30.27},{\"name\":\"某某餐厅\",\"longitude\":120.15,\"latitude\":30.28}]") String poisJson
    ) {
        log.info("[PlanUpdateTool] updatePlan: {}", poisJson.length() > 200 ? poisJson.substring(0, 200) + "..." : poisJson);
        try {
            cn.hutool.json.JSONArray arr = cn.hutool.json.JSONUtil.parseArray(poisJson);
            if (arr.size() < 2) return "至少需要 2 个目的地才能生成路线";

            List<Map<String, Object>> selectedPois = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                cn.hutool.json.JSONObject poi = arr.getJSONObject(i);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", poi.getStr("name"));
                m.put("longitude", poi.getDouble("longitude"));
                m.put("latitude", poi.getDouble("latitude"));
                if (poi.getStr("address") != null) m.put("address", poi.getStr("address"));
                selectedPois.add(m);
            }

            DatePlanTools.RegenerateResult result = datePlanTools.regenerate(
                    selectedPois, currentLocation, currentBudget, currentStyle);

            if (result.getErrorMessage() != null) {
                return "更新失败: " + result.getErrorMessage();
            }

            lastResult = result;

            StringBuilder sb = new StringBuilder();
            sb.append("计划已更新！\n");
            for (Map<String, Object> poi : selectedPois) {
                sb.append("- ").append(poi.get("name")).append("\n");
            }
            if (result.getRouteInfo() != null) {
                Object dist = result.getRouteInfo().get("distance");
                Object dur = result.getRouteInfo().get("duration");
                if (dist != null) sb.append("步行距离：").append(dist).append("\n");
                if (dur != null) sb.append("步行时间：").append(dur).append("\n");
            }
            if (result.getPdfUrl() != null) {
                sb.append("PDF已重新生成：").append(result.getPdfUrl());
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("updatePlan 失败", e);
            return "更新失败: " + e.getMessage();
        }
    }
}

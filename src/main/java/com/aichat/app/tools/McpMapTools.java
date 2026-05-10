package com.aichat.app.tools;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * MCP 地图工具集
 *
 * 通过 @Tool 注解定义工具，LLM 自主决定调用哪个工具。
 * 工具执行实际调用 AmapTools 的 REST API。
 */
@Component
@Slf4j
public class McpMapTools {

    private final AmapTools amapTools;

    public McpMapTools(AmapTools amapTools) {
        this.amapTools = amapTools;
    }

    /** 获取所有工具的 ToolSpecification */
    public List<ToolSpecification> getToolSpecifications() {
        return ToolSpecifications.toolSpecificationsFrom(this);
    }

    // ========== 工具定义 ==========

    @Tool("地址转经纬度坐标。输入地址返回经度和纬度。")
    public String geocode(
            @dev.langchain4j.agent.tool.P("地址，如：杭州西湖") String address,
            @dev.langchain4j.agent.tool.P("城市名，可选，提高精度，如：杭州") String city
    ) {
        log.info("[MCP Tool] geocode: address={}, city={}", address, city);
        double[] coords = amapTools.geocode(address, city != null ? city : "");
        if (coords == null) {
            return "地理编码失败，无法解析地址: " + address;
        }
        return String.format("{\"longitude\":%.6f,\"latitude\":%.6f}", coords[0], coords[1]);
    }

    @Tool("关键词搜索兴趣点。在指定城市搜索关键词相关的地点，返回最多 page_size 个结果，包含名称、地址、经纬度。")
    public String searchPoi(
            @dev.langchain4j.agent.tool.P("搜索关键词，如：咖啡厅、书店、花店") String keyword,
            @dev.langchain4j.agent.tool.P("城市名，如：杭州") String city,
            @dev.langchain4j.agent.tool.P("返回数量，默认5") int pageSize
    ) {
        log.info("[MCP Tool] searchPoi: keyword={}, city={}, pageSize={}", keyword, city, pageSize);
        List<AmapTools.PoiResult> results = amapTools.textSearch(keyword, city);
        return poiResultsToJson(results, pageSize > 0 ? pageSize : 5);
    }

    @Tool("附近搜索兴趣点。以指定经纬度为中心，搜索半径范围内的地点。")
    public String aroundSearch(
            @dev.langchain4j.agent.tool.P("中心点经度") double longitude,
            @dev.langchain4j.agent.tool.P("中心点纬度") double latitude,
            @dev.langchain4j.agent.tool.P("搜索关键词，如：餐厅、公园") String keyword,
            @dev.langchain4j.agent.tool.P("搜索半径（米），默认3000") int radius
    ) {
        log.info("[MCP Tool] aroundSearch: lon={}, lat={}, keyword={}, radius={}", longitude, latitude, keyword, radius);
        List<AmapTools.PoiResult> results = amapTools.aroundSearch(longitude, latitude, keyword, radius > 0 ? radius : 3000);
        return poiResultsToJson(results, 5);
    }

    @Tool("步行路线规划。计算两个地点之间的步行距离和时间。")
    public String walkingRoute(
            @dev.langchain4j.agent.tool.P("起点经度") double originLon,
            @dev.langchain4j.agent.tool.P("起点纬度") double originLat,
            @dev.langchain4j.agent.tool.P("终点经度") double destLon,
            @dev.langchain4j.agent.tool.P("终点纬度") double destLat
    ) {
        log.info("[MCP Tool] walkingRoute: ({},{}) → ({},{})", originLon, originLat, destLon, destLat);
        AmapTools.RouteResult result = amapTools.walkingRoute(
                new double[]{originLon, originLat},
                new double[]{destLon, destLat}
        );
        if (result.getDistance() != null) {
            return String.format("{\"distance\":\"%s\",\"duration\":\"%s\"}", result.getDistance(), result.getDuration());
        }
        return "路线规划失败";
    }

    // ========== 辅助方法 ==========

    private String poiResultsToJson(List<AmapTools.PoiResult> results, int limit) {
        if (results == null || results.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        int count = Math.min(results.size(), limit);
        for (int i = 0; i < count; i++) {
            AmapTools.PoiResult poi = results.get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format(
                    "{\"id\":\"%s\",\"name\":\"%s\",\"address\":\"%s\",\"distance\":\"%s\",\"longitude\":%.6f,\"latitude\":%.6f}",
                    poi.getId() != null ? poi.getId() : "",
                    escapeJson(poi.getName()),
                    escapeJson(poi.getAddress()),
                    poi.getDistance() != null ? poi.getDistance() : "",
                    poi.getLongitude(),
                    poi.getLatitude()
            ));
        }
        sb.append("]");
        return sb.toString();
    }

    @Tool("查询地点详情（评分、人均消费、照片、营业时间）。传入地点的 id（从搜索结果中获取）。")
    public String placeDetail(
            @dev.langchain4j.agent.tool.P("地点的 id（高德 POI id）") String poiId
    ) {
        log.info("[MCP Tool] placeDetail: id={}", poiId);
        AmapTools.PoiDetail detail = amapTools.placeDetail(poiId);
        StringBuilder sb = new StringBuilder();
        sb.append("名称：").append(detail.getName()).append("\n");
        if (detail.getRating() != null && !detail.getRating().isEmpty()) {
            sb.append("评分：").append(detail.getRating()).append("分\n");
        }
        if (detail.getCost() != null && !detail.getCost().isEmpty()) {
            sb.append("人均：").append(detail.getCost()).append("元\n");
        }
        if (detail.getBusinessHours() != null && !detail.getBusinessHours().isEmpty()) {
            sb.append("营业时间：").append(detail.getBusinessHours()).append("\n");
        }
        if (!detail.getReviews().isEmpty()) {
            sb.append("评价：\n");
            for (String review : detail.getReviews()) {
                sb.append("  - ").append(review).append("\n");
            }
        }
        if (!detail.getPhotos().isEmpty()) {
            sb.append("照片：\n");
            for (String photo : detail.getPhotos()) {
                sb.append("  ").append(photo).append("\n");
            }
        }
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}

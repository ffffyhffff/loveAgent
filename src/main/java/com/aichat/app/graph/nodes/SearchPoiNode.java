package com.aichat.app.graph.nodes;

import com.aichat.app.graph.DatePlanState;
import com.aichat.app.tools.AmapTools;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 搜索 POI 节点
 * 调用高德 API 搜索约会相关地点
 */
@Component
@Slf4j
public class SearchPoiNode implements NodeAction {

    private final AmapTools amapTools;

    public SearchPoiNode(AmapTools amapTools) {
        this.amapTools = amapTools;
    }

    @Override
    public Map<String, Object> apply(AgentState agentState) throws Exception {
        DatePlanState state = (DatePlanState) agentState;
        String location = state.getDateLocation();
        log.info("搜索 POI 节点：搜索 {}", location);

        Map<String, Object> update = new HashMap<>();

        // 地址转经纬度
        double[] coords = amapTools.geocode(location);
        if (coords == null) {
            log.warn("无法解析地址: {}", location);
            return update;
        }

        // 搜索附近 POI
        List<Map<String, Object>> cafes = amapTools.aroundSearch(coords[0], coords[1], "咖啡厅", 3000);
        List<Map<String, Object>> spots = amapTools.aroundSearch(coords[0], coords[1], "景点", 5000);
        List<Map<String, Object>> restaurants = amapTools.aroundSearch(coords[0], coords[1], "餐厅", 3000);

        update.put(DatePlanState.CAFES, cafes);
        update.put(DatePlanState.SPOTS, spots);
        update.put(DatePlanState.RESTAURANTS, restaurants);

        log.info("搜索完成：咖啡厅{}个，景点{}个，餐厅{}个",
                cafes.size(), spots.size(), restaurants.size());
        return update;
    }
}

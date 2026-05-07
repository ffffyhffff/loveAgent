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
 * 调用高德地图 API 搜索约会相关地点
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

        log.info("坐标：{}, {}", coords[0], coords[1]);

        // 搜索附近 POI
        List<Map<String, Object>> cafes = toMapList(amapTools.aroundSearch(coords[0], coords[1], "咖啡厅", 3000));
        List<Map<String, Object>> spots = toMapList(amapTools.aroundSearch(coords[0], coords[1], "景点", 5000));
        List<Map<String, Object>> restaurants = toMapList(amapTools.aroundSearch(coords[0], coords[1], "餐厅", 3000));

        log.info("搜索结果：咖啡厅{}个，景点{}个，餐厅{}个", cafes.size(), spots.size(), restaurants.size());

        update.put(DatePlanState.CAFES, cafes);
        update.put(DatePlanState.SPOTS, spots);
        update.put(DatePlanState.RESTAURANTS, restaurants);

        // 自动选择第一个
        if (!cafes.isEmpty()) update.put(DatePlanState.SELECTED_CAFE, cafes.get(0));
        if (!spots.isEmpty()) update.put(DatePlanState.SELECTED_SPOT, spots.get(0));
        if (!restaurants.isEmpty()) update.put(DatePlanState.SELECTED_RESTAURANT, restaurants.get(0));

        return update;
    }

    private List<Map<String, Object>> toMapList(List<AmapTools.PoiResult> pois) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (AmapTools.PoiResult poi : pois) {
            Map<String, Object> m = new HashMap<>();
            m.put("name", poi.getName());
            m.put("address", poi.getAddress());
            m.put("distance", poi.getDistance());
            m.put("longitude", poi.getLongitude());
            m.put("latitude", poi.getLatitude());
            result.add(m);
        }
        return result;
    }
}

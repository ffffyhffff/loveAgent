package com.aichat.app.graph.nodes;

import com.aichat.app.graph.DatePlanState;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 选择 POI 节点（Human-in-the-Loop）
 * 展示搜索结果，让用户选择
 */
@Component
@Slf4j
public class ChoosePoiNode implements NodeAction {

    @Override
    public Map<String, Object> apply(AgentState agentState) throws Exception {
        DatePlanState state = (DatePlanState) agentState;
        log.info("Choose POI 节点：展示搜索结果，等待用户选择");

        // 如果已选择（从 interrupt 恢复），直接返回
        if (state.value(DatePlanState.SELECTED_CAFE).isPresent()) {
            log.info("用户已完成 POI 选择");
            return Map.of();
        }

        // 默认选择第一个（后续通过 SSE 让用户选择）
        Map<String, Object> update = new HashMap<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cafes = (List<Map<String, Object>>) state.value(DatePlanState.CAFES).orElse(List.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> spots = (List<Map<String, Object>>) state.value(DatePlanState.SPOTS).orElse(List.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> restaurants = (List<Map<String, Object>>) state.value(DatePlanState.RESTAURANTS).orElse(List.of());

        if (!cafes.isEmpty()) update.put(DatePlanState.SELECTED_CAFE, cafes.get(0));
        if (!spots.isEmpty()) update.put(DatePlanState.SELECTED_SPOT, spots.get(0));
        if (!restaurants.isEmpty()) update.put(DatePlanState.SELECTED_RESTAURANT, restaurants.get(0));

        log.info("默认选择：咖啡厅={}，景点={}，餐厅={}",
                cafes.isEmpty() ? "无" : cafes.get(0).get("name"),
                spots.isEmpty() ? "无" : spots.get(0).get("name"),
                restaurants.isEmpty() ? "无" : restaurants.get(0).get("name"));

        return update;
    }
}

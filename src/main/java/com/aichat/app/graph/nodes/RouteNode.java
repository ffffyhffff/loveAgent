package com.aichat.app.graph.nodes;

import com.aichat.app.graph.DatePlanState;
import com.aichat.app.tools.AmapTools;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 路线规划节点
 * 串联选中的 POI，调用高德 API 规划路线
 */
@Component
@Slf4j
public class RouteNode implements NodeAction {

    private final AmapTools amapTools;

    public RouteNode(AmapTools amapTools) {
        this.amapTools = amapTools;
    }

    @Override
    public Map<String, Object> apply(AgentState agentState) throws Exception {
        DatePlanState state = (DatePlanState) agentState;
        log.info("路线规划节点");

        Map<String, Object> update = new HashMap<>();

        @SuppressWarnings("unchecked")
        Map<String, Object> cafe = (Map<String, Object>) state.value(DatePlanState.SELECTED_CAFE).orElse(null);
        @SuppressWarnings("unchecked")
        Map<String, Object> spot = (Map<String, Object>) state.value(DatePlanState.SELECTED_SPOT).orElse(null);
        @SuppressWarnings("unchecked")
        Map<String, Object> restaurant = (Map<String, Object>) state.value(DatePlanState.SELECTED_RESTAURANT).orElse(null);

        if (cafe == null) {
            log.warn("未选择 POI，跳过路线规划");
            return update;
        }

        // 规划步行路线
        try {
            double[] cafeCoord = {(double) cafe.get("longitude"), (double) cafe.get("latitude")};
            double[] destCoord;
            String destName;

            if (spot != null) {
                destCoord = new double[]{(double) spot.get("longitude"), (double) spot.get("latitude")};
                destName = (String) spot.get("name");
            } else if (restaurant != null) {
                destCoord = new double[]{(double) restaurant.get("longitude"), (double) restaurant.get("latitude")};
                destName = (String) restaurant.get("name");
            } else {
                destCoord = cafeCoord;
                destName = "同上";
            }

            AmapTools.RouteResult route = amapTools.walkingRoute(cafeCoord, destCoord);
            update.put(DatePlanState.ROUTE_DISTANCE, route.getDistance());
            update.put(DatePlanState.ROUTE_DURATION, route.getDuration());

            log.info("路线规划完成：{} → {}，{}，{}", cafe.get("name"), destName, route.getDistance(), route.getDuration());
        } catch (Exception e) {
            log.error("路线规划失败", e);
        }

        return update;
    }
}

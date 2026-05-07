package com.aichat.app.graph.nodes;

import com.aichat.app.graph.DatePlanState;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * PDF 生成节点
 * 生成约会计划书
 */
@Component
@Slf4j
public class PdfNode implements NodeAction {

    @Override
    public Map<String, Object> apply(AgentState agentState) throws Exception {
        DatePlanState state = (DatePlanState) agentState;
        log.info("PDF 节点：生成约会计划书");

        Map<String, Object> update = new HashMap<>();

        // 构建最终回复
        StringBuilder reply = new StringBuilder();
        reply.append("你的约会计划已生成！\n\n");
        reply.append("📍 地点：").append(state.getDateLocation()).append("\n");
        reply.append("💰 预算：").append(state.getDateBudget()).append("\n");
        reply.append("✨ 风格：").append(state.getDateStyle()).append("\n\n");

        @SuppressWarnings("unchecked")
        Map<String, Object> cafe = (Map<String, Object>) state.value(DatePlanState.SELECTED_CAFE).orElse(null);
        @SuppressWarnings("unchecked")
        Map<String, Object> spot = (Map<String, Object>) state.value(DatePlanState.SELECTED_SPOT).orElse(null);
        @SuppressWarnings("unchecked")
        Map<String, Object> restaurant = (Map<String, Object>) state.value(DatePlanState.SELECTED_RESTAURANT).orElse(null);

        reply.append("行程安排：\n");
        if (cafe != null) reply.append("☕ 下午茶：").append(cafe.get("name")).append("\n");
        if (spot != null) reply.append("🌸 景点：").append(spot.get("name")).append("\n");
        if (restaurant != null) reply.append("🍽️ 晚餐：").append(restaurant.get("name")).append("\n");

        String distance = state.value(DatePlanState.ROUTE_DISTANCE).map(Object::toString).orElse("");
        String duration = state.value(DatePlanState.ROUTE_DURATION).map(Object::toString).orElse("");
        if (!distance.isEmpty()) {
            reply.append("\n🗺️ 路线：").append(distance).append("，约步行").append(duration).append("\n");
        }

        update.put(DatePlanState.AI_RESPONSE, reply.toString());
        update.put(DatePlanState.PDF_URL, "/api/pdf/wip");

        log.info("PDF 节点完成");
        return update;
    }
}

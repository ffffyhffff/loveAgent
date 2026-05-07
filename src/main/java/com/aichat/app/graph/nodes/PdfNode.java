package com.aichat.app.graph.nodes;

import com.aichat.app.graph.DatePlanState;
import com.aichat.app.tools.PdfGenerationTool;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * PDF 生成节点
 */
@Component
@Slf4j
public class PdfNode implements NodeAction {

    private final PdfGenerationTool pdfTool;

    public PdfNode(PdfGenerationTool pdfTool) {
        this.pdfTool = pdfTool;
    }

    @Override
    public Map<String, Object> apply(AgentState agentState) throws Exception {
        DatePlanState state = (DatePlanState) agentState;
        log.info("PDF 节点：生成约会计划书");

        Map<String, Object> update = new HashMap<>();

        // 生成 PDF
        String pdfPath = pdfTool.generate(state);

        // 构建回复
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

        if (pdfPath != null) {
            reply.append("\n📄 PDF 已生成：").append(pdfPath);
        }

        update.put(DatePlanState.AI_RESPONSE, reply.toString());
        update.put(DatePlanState.PDF_URL, pdfPath != null ? pdfPath : "");

        log.info("PDF 节点完成");
        return update;
    }
}

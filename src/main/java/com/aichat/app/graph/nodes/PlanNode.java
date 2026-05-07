package com.aichat.app.graph.nodes;

import com.aichat.app.graph.DatePlanState;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Plan 节点：展示计划，等待用户确认（Human-in-the-Loop）
 *
 * 在 LangGraph4j 中，interrupt 暂停需要 checkpointer 支持。
 * 当前实现：通过 SSE 推送计划给前端，前端返回确认后继续。
 */
@Component
@Slf4j
public class PlanNode implements NodeAction {

    @Override
    public Map<String, Object> apply(AgentState agentState) throws Exception {
        DatePlanState state = (DatePlanState) agentState;
        log.info("Plan 节点：计划已生成，等待用户确认");

        // 如果已有用户选择（从 interrupt 恢复），直接返回
        String userChoice = state.getUserChoice();
        if (userChoice != null) {
            log.info("Plan 节点：用户选择 = {}", userChoice);
            return Map.of();
        }

        // 构建计划展示（通过 SSE 推送给前端）
        log.info("计划内容：{}", state.getPlanDescription());
        log.info("地点：{}，预算：{}，风格：{}",
                state.getDateLocation(), state.getDateBudget(), state.getDateStyle());

        // 用户选择通过 SSE 前端返回，设置到 state 后恢复执行
        // 当前：设置默认值，后续通过 ChatController 集成 SSE 时改造
        Map<String, Object> update = new HashMap<>();
        update.put(DatePlanState.USER_CHOICE, "pending");
        return update;
    }
}

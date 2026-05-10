package com.aichat.app.graph.nodes;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aichat.app.graph.PlanExecuteState;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Planner node: creates a visible plan for the plan-and-execute date agent.
 */
@Component
@Slf4j
public class PlannerNode implements NodeAction {

    private final ChatLanguageModel chatModel;
    private java.util.function.Consumer<Map<String, Object>> onEvent;

    public PlannerNode(ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
    }

    public void setOnEvent(java.util.function.Consumer<Map<String, Object>> onEvent) {
        this.onEvent = onEvent;
    }

    @Override
    public Map<String, Object> apply(org.bsc.langgraph4j.state.AgentState agentState) {
        PlanExecuteState state = (PlanExecuteState) agentState;
        String goal = state.getUserGoal();
        String location = state.value(PlanExecuteState.DATE_LOCATION).map(Object::toString).orElse("");
        String budget = state.value(PlanExecuteState.DATE_BUDGET).map(Object::toString).orElse("");
        String style = state.value(PlanExecuteState.DATE_STYLE).map(Object::toString).orElse("");
        String occasion = state.value(PlanExecuteState.DATE_OCCASION).map(Object::toString).orElse("");
        String activity = state.value(PlanExecuteState.DATE_ACTIVITY).map(Object::toString).orElse("");

        log.info("Planner: location={}, style={}", location, style);

        String prompt = """
                你是约会规划助手。请根据用户需求制定一个可执行计划。

                用户目标：%s
                约会地点：%s
                预算：%s
                风格：%s
                场景：%s
                活动偏好：%s

                只输出 JSON，不要输出 markdown 或解释。格式：
                {"steps":["搜索{地点}附近的咖啡厅","搜索{地点}附近的景点","搜索{地点}附近的餐厅","搜索{地点}附近的甜品店","获取最终行程点的详情和图片","生成约会推荐总结"]}

                规则：
                - 4 到 6 步。
                - 前几步必须是搜索候选地点，关键词只能从咖啡厅、茶馆、公园、景点、观景台、西餐厅、餐厅、甜品店、花店、酒吧、书店、火锅中选择。
                - 倒数第二步必须是“获取最终行程点的详情和图片”。
                - 最后一步必须是“生成约会推荐总结”。
                """.formatted(goal, location, budget, style, occasion, activity);

        List<String> steps;
        try {
            ChatResponse response = chatModel.chat(List.of(
                    new SystemMessage(prompt),
                    new UserMessage("请制定执行计划")));
            String output = response.aiMessage().text();
            log.info("Planner output: {}", output);
            steps = parseSteps(output);
            if (steps.isEmpty()) {
                steps = getDefaultPlan(occasion, activity, location);
            }
        } catch (Exception e) {
            log.error("Planner failed, use default plan", e);
            steps = getDefaultPlan(occasion, activity, location);
        }

        emitPlan(steps);

        Map<String, Object> update = new HashMap<>();
        update.put(PlanExecuteState.PLAN_STEPS, steps);
        update.put(PlanExecuteState.CURRENT_STEP_INDEX, "0");
        update.put(PlanExecuteState.STEP_RESULTS, new LinkedHashMap<String, String>());
        return update;
    }

    private List<String> parseSteps(String output) {
        List<String> steps = new ArrayList<>();
        if (output == null || output.isBlank()) {
            return steps;
        }
        String cleaned = output.replaceAll("```json|```", "").trim();
        JSONObject json = JSONUtil.parseObj(cleaned);
        JSONArray stepsArr = json.getJSONArray("steps");
        if (stepsArr != null) {
            for (int i = 0; i < stepsArr.size(); i++) {
                String step = stepsArr.getStr(i);
                if (step != null && !step.isBlank()) {
                    steps.add(step);
                }
            }
        }
        return steps;
    }

    private void emitPlan(List<String> steps) {
        if (onEvent == null) {
            return;
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            items.add(Map.of(
                    "index", i,
                    "message", steps.get(i),
                    "status", "pending"
            ));
        }
        onEvent.accept(Map.of("type", "plan", "steps", items));
    }

    private List<String> getDefaultPlan(String occasion, String activity, String location) {
        String loc = location == null || location.isBlank() ? "目的地" : location;
        if (occasion != null && occasion.contains("求婚")) {
            return List.of(
                    "搜索" + loc + "附近的花店",
                    "搜索" + loc + "附近的观景台",
                    "搜索" + loc + "附近的西餐厅",
                    "获取最终行程点的详情和图片",
                    "生成约会推荐总结"
            );
        }
        if (activity != null && activity.contains("纯吃")) {
            return List.of(
                    "搜索" + loc + "附近的餐厅",
                    "搜索" + loc + "附近的甜品店",
                    "搜索" + loc + "附近的咖啡厅",
                    "获取最终行程点的详情和图片",
                    "生成约会推荐总结"
            );
        }
        return List.of(
                "搜索" + loc + "附近的咖啡厅",
                "搜索" + loc + "附近的公园和景点",
                "搜索" + loc + "附近的餐厅",
                "搜索" + loc + "附近的甜品店",
                "获取最终行程点的详情和图片",
                "生成约会推荐总结"
        );
    }
}

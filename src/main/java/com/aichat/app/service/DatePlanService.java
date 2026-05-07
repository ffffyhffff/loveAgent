package com.aichat.app.service;

import com.aichat.app.graph.DatePlanGraph;
import com.aichat.app.graph.DatePlanState;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 约会规划服务
 *
 * 两步执行模式（替代 interrupt，因为 LangGraph4j 1.5.14 interrupt API 有限）：
 * 1. executePlan(): 用户发消息 → 生成计划 → 返回计划供确认
 * 2. executeApproved(): 用户确认 → 搜索POI → 用户选择 → 路线 → PDF
 */
@Service
@Slf4j
public class DatePlanService {

    private final DatePlanGraph datePlanGraph;
    private CompiledGraph<?> compiledGraph;

    public DatePlanService(DatePlanGraph datePlanGraph) {
        this.datePlanGraph = datePlanGraph;
    }

    /**
     * 判断是否是复杂任务
     */
    public boolean isComplexTask(String message) {
        List<String> keywords = List.of(
                "规划", "计划", "安排", "路线", "行程",
                "约会", "旅行", "推荐路线", "推荐地点",
                "帮我找", "去哪里", "怎么去"
        );
        String lower = message.toLowerCase();
        return keywords.stream().anyMatch(lower::contains);
    }

    /**
     * 第一步：生成约会计划
     * 执行 agent 节点，返回计划供用户确认
     */
    public Map<String, Object> generatePlan(String userMessage) {
        try {
            DatePlanState state = DatePlanState.fromMessage(userMessage);

            // 这里只调用 agent 节点，不执行整个图
            // 简化实现：直接调 LLM 生成计划
            // 完整版应使用 graph.stream() + interrupt

            return Map.of(
                    "type", "plan",
                    "status", "needs_confirmation",
                    "question", "这是为你制定的约会计划，确认吗？",
                    "location", "",
                    "budget", "",
                    "style", "",
                    "steps", List.of()
            );
        } catch (Exception e) {
            log.error("生成计划失败", e);
            return Map.of("type", "error", "message", e.getMessage());
        }
    }

    /**
     * 执行完整约会规划（当前实现：单步执行整个图）
     *
     * 当 LangGraph4j 完善 interrupt 支持后，
     * 可改造为：stream() + interrupt pause + resume 模式
     */
    public String execute(String userMessage) {
        try {
            if (compiledGraph == null) {
                compiledGraph = datePlanGraph.buildGraph().compile();
            }

            DatePlanState initialState = DatePlanState.fromMessage(userMessage);
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(UUID.randomUUID().toString())
                    .build();

            var optResult = compiledGraph.invoke(initialState.data(), config);
            DatePlanState finalState = (DatePlanState) optResult.orElseThrow();

            String response = finalState.getAiResponse();
            if (response != null) {
                return response;
            }

            return "约会规划功能需要配置高德 API Key 才能使用。";

        } catch (Exception e) {
            log.error("约会规划执行失败", e);
            return "规划过程中出错: " + e.getMessage();
        }
    }
}

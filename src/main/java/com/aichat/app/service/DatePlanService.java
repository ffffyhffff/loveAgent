package com.aichat.app.service;

import com.aichat.app.graph.DatePlanGraph;
import com.aichat.app.graph.DatePlanState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 约会规划服务
 * 封装 LangGraph4j 图执行
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DatePlanService {

    private final DatePlanGraph datePlanGraph;
    private CompiledGraph<?> compiledGraph;

    /**
     * 判断是否是复杂任务（需要 Plan-and-Execute）
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
     * 执行约会规划
     * 返回最终结果
     */
    public String execute(String userMessage) {
        try {
            // 缓存编译后的图
            if (compiledGraph == null) {
                compiledGraph = datePlanGraph.buildGraph().compile();
            }

            DatePlanState initialState = DatePlanState.fromMessage(userMessage);
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(UUID.randomUUID().toString())
                    .build();

            // invoke 返回 Optional<AgentState>
            var optResult = compiledGraph.invoke(initialState.data(), config);
            DatePlanState finalState = (DatePlanState) optResult.orElseThrow();

            // 构建回复
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

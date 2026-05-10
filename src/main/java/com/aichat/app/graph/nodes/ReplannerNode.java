package com.aichat.app.graph.nodes;

import com.aichat.app.graph.PlanExecuteState;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Replanner 节点：判断继续/完成（不调 LLM，直接判断）
 */
@Component
@Slf4j
public class ReplannerNode implements NodeAction {

    private final ChatLanguageModel chatModel;

    public ReplannerNode(ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public Map<String, Object> apply(org.bsc.langgraph4j.state.AgentState agentState) throws Exception {
        PlanExecuteState state = (PlanExecuteState) agentState;
        List<String> steps = state.getPlanSteps();
        int currentIndex = state.getCurrentStepIndex();

        log.info("Replanner：{}/{} 步完成", currentIndex, steps.size());

        Map<String, Object> update = new HashMap<>();

        if (currentIndex >= steps.size()) {
            String summary = generateFinalSummary(state);
            update.put(PlanExecuteState.ACTION, "done");
            update.put(PlanExecuteState.FINAL_ANSWER, summary);
            log.info("所有步骤已完成");
        } else {
            update.put(PlanExecuteState.ACTION, "continue");
        }

        return update;
    }

    private String generateFinalSummary(PlanExecuteState state) {
        try {
            String location = state.value(PlanExecuteState.DATE_LOCATION).map(Object::toString).orElse("");
            String budget = state.value(PlanExecuteState.DATE_BUDGET).map(Object::toString).orElse("");
            String context = state.getAccumulatedContext();

            String prompt = """
                    根据搜索结果，写一段温馨的约会推荐总结（80字以内，不要markdown）：

                    地点：%s  预算：%s

                    %s

                    写出推荐路线（第一站→第二站→第三站）和推荐理由，语气温暖。
                    """.formatted(location, budget,
                            context.length() > 600 ? context.substring(0, 600) : context);

            ChatResponse response = chatModel.chat(List.of(
                    new SystemMessage(prompt),
                    new UserMessage("请生成推荐总结")));
            return response.aiMessage().text();
        } catch (Exception e) {
            return "约会规划完成！祝你们约会愉快～💕";
        }
    }
}

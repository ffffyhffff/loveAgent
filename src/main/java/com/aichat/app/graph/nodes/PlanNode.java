package com.aichat.app.graph.nodes;

import com.aichat.app.graph.DatePlanState;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Plan 节点：调用 LLM 生成详细的约会计划
 *
 * 从 AgentNode 提取的结构化信息（地点/预算/风格/时长/关键词），
 * 生成一份 Markdown 格式的详细约会方案。
 */
@Component
@Slf4j
public class PlanNode implements NodeAction {

    private final ChatLanguageModel chatModel;

    private static final String PLAN_PROMPT = """
            你是一位专业的约会策划师。请根据以下信息，生成一份详细的约会计划。

            要求：
            1. 按时间线排列，每个时间段包含：时间、活动、地点、预计花费
            2. 推荐具体的餐厅/场所类型（不需要真实店名，给出类型和选择标准）
            3. 每个活动附带简短的推荐理由
            4. 末尾给出总预算汇总
            5. 用 Markdown 格式输出，使用标题、表格、列表

            输出格式示例：
            ## 约会行程

            ### 12:00 - 13:30 午餐
            - **地点**：xx商圈附近，推荐选择环境优雅的西餐厅或日料
            - **预算**：约150-200元
            - **理由**：适合两人安静交谈，氛围感好

            ### 14:00 - 16:00 ...
            ...

            ## 预算汇总
            | 项目 | 预算 |
            |------|------|
            | 午餐 | 150-200元 |
            | ... | ... |
            | **合计** | **xxx元** |

            ## 贴心提示
            - 根据具体情况给出1-2条实用建议
            """;

    public PlanNode(ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public Map<String, Object> apply(AgentState agentState) throws Exception {
        DatePlanState state = (DatePlanState) agentState;
        log.info("Plan 节点：开始生成详细计划");

        // 如果已有用户选择（从 interrupt 恢复），直接返回
        String userChoice = state.getUserChoice();
        if (userChoice != null && !"pending".equals(userChoice)) {
            log.info("Plan 节点：用户选择 = {}", userChoice);
            return Map.of();
        }

        // 构建 LLM prompt
        String userRequest = String.format("""
                用户约会需求：
                - 地点：%s
                - 预算：%s
                - 风格：%s
                - 时长：%s
                - 特殊要求：%s
                - 用户原始消息：%s
                """,
                state.getDateLocation(),
                state.getDateBudget(),
                state.getDateStyle(),
                state.getDateDuration(),
                state.getDateKeywords(),
                state.getUserMessage());

        String fullPrompt = PLAN_PROMPT + "\n\n" + userRequest;

        log.info("Plan 节点：调用 LLM 生成计划...");
        String planResult = chatModel.chat(fullPrompt);
        log.info("Plan 节点：计划生成完成，长度={}字", planResult.length());

        Map<String, Object> update = new HashMap<>();
        update.put(DatePlanState.PLAN_DESC, planResult);
        update.put(DatePlanState.USER_CHOICE, "pending");
        return update;
    }
}

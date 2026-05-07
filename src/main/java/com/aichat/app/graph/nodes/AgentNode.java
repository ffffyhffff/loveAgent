package com.aichat.app.graph.nodes;

import com.aichat.app.graph.DatePlanState;
import com.aichat.app.service.RagService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent 统一入口节点
 * LLM 接收用户消息，判断走简单对话还是约会规划
 */
@Component
@Slf4j
public class AgentNode implements NodeAction {

    private final ChatLanguageModel chatModel;
    private final RagService ragService;

    private static final String SYSTEM_PROMPT = """
            你是 AI 恋爱大师。

            对于简单的情感咨询，参考知识库内容直接回答。

            当用户请求涉及约会规划、行程安排、推荐地点时，
            请用以下格式回复：
            [ACTION:plan]
            地点：xxx
            预算：xxx
            风格：浪漫/休闲/冒险
            流程：
            1. 时间段 - 活动类型
            2. 时间段 - 活动类型

            其他普通对话回复：
            [ACTION:chat]
            你的回答...
            """;

    public AgentNode(ChatLanguageModel chatModel, RagService ragService) {
        this.chatModel = chatModel;
        this.ragService = ragService;
    }

    @Override
    public Map<String, Object> apply(AgentState agentState) throws Exception {
        DatePlanState state = (DatePlanState) agentState;
        log.info("Agent 节点：处理用户消息");

        String userMessage = state.getUserMessage();

        // 注入 RAG 知识库上下文
        String ragContext = ragService.search(userMessage);
        String prompt = SYSTEM_PROMPT + "\n\n知识库参考：\n" + ragContext + "\n\n用户：" + userMessage;

        String response = chatModel.chat(prompt);

        Map<String, Object> update = new HashMap<>();

        if (response.contains("[ACTION:plan]")) {
            update.put(DatePlanState.ACTION, "plan");
            String planContent = response.replace("[ACTION:plan]", "").trim();
            update.put(DatePlanState.PLAN_DESC, planContent);
            for (String line : planContent.split("\n")) {
                if (line.startsWith("地点："))
                    update.put(DatePlanState.DATE_LOCATION, line.replace("地点：", "").trim());
                if (line.startsWith("预算："))
                    update.put(DatePlanState.DATE_BUDGET, line.replace("预算：", "").trim());
                if (line.startsWith("风格："))
                    update.put(DatePlanState.DATE_STYLE, line.replace("风格：", "").trim());
            }
            log.info("Agent 节点：识别为约会规划任务");
        } else {
            update.put(DatePlanState.ACTION, "chat");
            update.put(DatePlanState.AI_RESPONSE, response.replace("[ACTION:chat]", "").trim());
            log.info("Agent 节点：识别为普通对话");
        }

        return update;
    }
}

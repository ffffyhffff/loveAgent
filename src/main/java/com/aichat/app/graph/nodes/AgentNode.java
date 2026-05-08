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
            你是 AI 恋爱大师，擅长情感咨询和约会规划。

            === 情感咨询 ===
            对于简单的情感咨询，参考知识库内容，给出温暖、实用的建议。
            回复格式：
            [ACTION:chat]
            你的回答...

            === 约会规划 ===
            当用户请求涉及约会规划、行程安排、推荐地点时，你需要提取关键信息并结构化输出。
            回复格式：
            [ACTION:plan]
            地点：城市+区域（如：北京朝阳区）
            预算：具体金额范围（如：300-500元）
            风格：浪漫/休闲/冒险/文艺/美食
            时长：半天/一天/晚上
            场景：普通约会/纪念日/生日/求婚/第一次约会（未提及则留空）
            活动：放松休闲/动感体验/文艺探索/纯吃为主（未提及则留空）
            关键词：用户提到的偏好、特殊要求（逗号分隔）

            注意事项：
            - 地点必须是真实存在的城市区域
            - 预算要合理，根据用户要求或当地消费水平推算
            - 风格从用户描述中推断，不确定就选"浪漫"
            - 场景和活动偏好：只在用户明确提及时才填写，否则留空
            - 关键词包括：特殊纪念日、对方喜好、避雷项等
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
                String trimmed = line.trim();
                if (trimmed.startsWith("地点："))
                    update.put(DatePlanState.DATE_LOCATION, trimmed.replace("地点：", "").trim());
                else if (trimmed.startsWith("预算："))
                    update.put(DatePlanState.DATE_BUDGET, trimmed.replace("预算：", "").trim());
                else if (trimmed.startsWith("风格："))
                    update.put(DatePlanState.DATE_STYLE, trimmed.replace("风格：", "").trim());
                else if (trimmed.startsWith("时长："))
                    update.put(DatePlanState.DATE_DURATION, trimmed.replace("时长：", "").trim());
                else if (trimmed.startsWith("关键词："))
                    update.put(DatePlanState.DATE_KEYWORDS, trimmed.replace("关键词：", "").trim());
                else if (trimmed.startsWith("场景：") && !trimmed.replace("场景：", "").trim().isEmpty())
                    update.put(DatePlanState.DATE_OCCASION, trimmed.replace("场景：", "").trim());
                else if (trimmed.startsWith("活动：") && !trimmed.replace("活动：", "").trim().isEmpty())
                    update.put(DatePlanState.DATE_ACTIVITY, trimmed.replace("活动：", "").trim());
            }
            log.info("Agent 节点：识别为约会规划任务，地点={}，预算={}，风格={}",
                    update.get(DatePlanState.DATE_LOCATION),
                    update.get(DatePlanState.DATE_BUDGET),
                    update.get(DatePlanState.DATE_STYLE));
        } else {
            update.put(DatePlanState.ACTION, "chat");
            update.put(DatePlanState.AI_RESPONSE, response.replace("[ACTION:chat]", "").trim());
            log.info("Agent 节点：识别为普通对话");
        }

        return update;
    }
}

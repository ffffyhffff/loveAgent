package com.aichat.app.agent;

import com.aichat.app.tools.ToolProvider;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * AI 智能体
 */
@Slf4j
public class AiAssistant {

    private static final String SYSTEM_PROMPT = """
            你是一个全能的 AI 智能体，擅长回答各种问题。
            请用中文回答，保持友好、专业。
            """;

    private final ChatLanguageModel chatModel;
    private final StreamingChatLanguageModel streamingChatModel;

    public AiAssistant(ChatLanguageModel chatModel,
                       StreamingChatLanguageModel streamingChatModel,
                       ToolProvider toolProvider) {
        this.chatModel = chatModel;
        this.streamingChatModel = streamingChatModel;
    }

    /**
     * 普通对话
     */
    public String chat(String userMessage) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        messages.add(new UserMessage(userMessage));
        return chatModel.chat(messages).aiMessage().text();
    }

    /**
     * 流式对话
     */
    public void chatStream(String userMessage, Consumer<String> tokenConsumer, Runnable onComplete) {
        try {
            String result = chat(userMessage);
            String[] parts = result.split("(?<=[。！？\\n])");
            for (String part : parts) {
                if (!part.isEmpty()) {
                    tokenConsumer.accept(part);
                    try { Thread.sleep(30); } catch (InterruptedException ignored) {}
                }
            }
        } catch (Exception e) {
            tokenConsumer.accept("错误: " + e.getMessage());
        }
        onComplete.run();
    }
}

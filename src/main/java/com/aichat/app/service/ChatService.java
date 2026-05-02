package com.aichat.app.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 对话服务
 */
@Service
@Slf4j
public class ChatService {

    private static final String SYSTEM_PROMPT = """
            你是一个全能的 AI 助手，擅长回答各种问题。请用中文回答，保持友好、专业。
            """;

    private final ChatLanguageModel chatModel;

    public ChatService(ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 普通对话
     */
    public String chat(String message) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        messages.add(new UserMessage(message));
        return chatModel.chat(messages).aiMessage().text();
    }
}

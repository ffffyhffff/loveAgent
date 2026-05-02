package com.aichat.app.service;

import com.aichat.app.agent.AiAssistant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Agent 对话服务（支持工具调用的智能体）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentChatService {

    private final AiAssistant aiAssistant;

    /**
     * 普通对话（支持工具调用）
     */
    public String chat(String message) {
        return aiAssistant.chat(message);
    }

    /**
     * 流式对话（支持工具调用）
     */
    public void chatStream(String message, java.util.function.Consumer<String> tokenConsumer,
                           Runnable onComplete) {
        aiAssistant.chatStream(message, tokenConsumer, onComplete);
    }
}

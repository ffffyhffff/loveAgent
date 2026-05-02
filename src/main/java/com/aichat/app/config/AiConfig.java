package com.aichat.app.config;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import com.aichat.app.tools.ToolProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j AI 配置
 */
@Configuration
@Slf4j
public class AiConfig {

    @Value("${ai.dashscope.api-key}")
    private String dashScopeApiKey;

    @Value("${ai.dashscope.model:qwen-plus}")
    private String modelName;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return QwenChatModel.builder()
                .apiKey(dashScopeApiKey)
                .modelName(modelName)
                .build();
    }

    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        return QwenStreamingChatModel.builder()
                .apiKey(dashScopeApiKey)
                .modelName(modelName)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return QwenEmbeddingModel.builder()
                .apiKey(dashScopeApiKey)
                .build();
    }

    @Bean
    public ToolProvider toolProvider() {
        return new ToolProvider();
    }

    @Bean
    public com.aichat.app.agent.AiAssistant aiAssistant(
            ChatLanguageModel chatModel,
            StreamingChatLanguageModel streamingChatModel,
            ToolProvider toolProvider) {
        return new com.aichat.app.agent.AiAssistant(chatModel, streamingChatModel, toolProvider);
    }
}

package com.aichat.app.model;

import lombok.Data;

/**
 * 聊天请求 DTO
 */
@Data
public class ChatRequest {

    private String message;

    private String chatId;

    /**
     * 是否使用智能体模式（支持工具调用）
     */
    private boolean agentMode = false;
}

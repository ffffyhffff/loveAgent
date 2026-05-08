package com.aichat.app.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * LoveAgent 流式请求体
 */
@Data
public class LoveStreamRequest {
    private String message;
    private String convId;
    private String formId;
    private Map<String, Object> answers;
    private List<Map<String, String>> context;
}

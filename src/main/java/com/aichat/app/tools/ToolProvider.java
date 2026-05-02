package com.aichat.app.tools;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.ToolExecutor;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.*;

/**
 * 工具提供者（集中管理所有工具）
 */
@Slf4j
public class ToolProvider {

    private final List<ToolSpecification> toolSpecifications = new ArrayList<>();
    private final Map<String, MethodInfo> methodMap = new LinkedHashMap<>();

    public ToolProvider() {
        register(new WebSearchTool());
        register(new WebScrapingTool());
        register(new FileOperationTool());
        register(new TerminalTool());
    }

    private void register(Object toolInstance) {
        try {
            List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(toolInstance);
            toolSpecifications.addAll(specs);

            // 建立 toolName -> (instance, method) 的映射
            for (Method method : toolInstance.getClass().getDeclaredMethods()) {
                if (method.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class)) {
                    methodMap.put(method.getName(), new MethodInfo(toolInstance, method));
                }
            }
            log.info("已注册工具: {}", toolInstance.getClass().getSimpleName());
        } catch (Exception e) {
            log.warn("注册工具失败: {}", toolInstance.getClass().getSimpleName(), e);
        }
    }

    public Collection<ToolSpecification> getToolSpecifications() {
        return toolSpecifications;
    }

    public Map<String, ToolExecutor> getToolExecutors() {
        Map<String, ToolExecutor> executors = new HashMap<>();
        for (var entry : methodMap.entrySet()) {
            String toolName = entry.getKey();
            MethodInfo mi = entry.getValue();
            executors.put(toolName, (request, memoryId) -> {
                try {
                    String args = request.arguments();
                    // 简单处理：如果只有一个参数，直接传字符串
                    Method method = mi.method;
                    int paramCount = method.getParameterCount();
                    if (paramCount == 0) {
                        return (String) method.invoke(mi.instance);
                    } else if (paramCount == 1) {
                        // 尝试解析 JSON 参数
                        String argValue = extractSingleArg(args);
                        return (String) method.invoke(mi.instance, argValue);
                    } else {
                        return "暂不支持多参数工具";
                    }
                } catch (Exception e) {
                    log.error("工具执行失败: {}", toolName, e);
                    return "工具执行失败: " + e.getMessage();
                }
            });
        }
        return executors;
    }

    private String extractSingleArg(String jsonArgs) {
        // 尝试从 JSON 中提取第一个参数的值
        // 格式通常为 {"argName": "value"} 或 {"argName": {"key": "val"}}
        try {
            // 简单的 JSON 解析
            int colonIdx = jsonArgs.indexOf(':');
            if (colonIdx == -1) return jsonArgs;

            String value = jsonArgs.substring(colonIdx + 1).trim();
            // 去掉外层大括号和引号
            if (value.endsWith("}")) value = value.substring(0, value.length() - 1).trim();
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            return value;
        } catch (Exception e) {
            return jsonArgs;
        }
    }

    private record MethodInfo(Object instance, Method method) {}
}

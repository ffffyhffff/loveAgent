package com.aichat.app.tools;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Stdio client for the official AMap MCP server: @amap/amap-maps-mcp-server.
 */
@Component
@Slf4j
public class AmapMcpClient {

    private final String apiKey;
    private final ReentrantLock lock = new ReentrantLock();
    private McpSyncClient client;

    public AmapMcpClient(@Value("${amap.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    public String callTool(String toolName, Map<String, Object> arguments) {
        lock.lock();
        try {
            McpSyncClient mcp = client();
            McpSchema.CallToolResult result =
                    mcp.callTool(new McpSchema.CallToolRequest(toolName, arguments));
            if (Boolean.TRUE.equals(result.isError())) {
                return "MCP tool error: " + extractContentText(result.content());
            }
            return extractContentText(result.content());
        } catch (Exception e) {
            log.error("AMap MCP tool call failed: {}", toolName, e);
            return "AMap MCP call failed: " + e.getMessage();
        } finally {
            lock.unlock();
        }
    }

    public List<String> listToolNames() {
        lock.lock();
        try {
            return client().listTools().tools().stream()
                    .map(McpSchema.Tool::name)
                    .collect(Collectors.toList());
        } finally {
            lock.unlock();
        }
    }

    private McpSyncClient client() {
        if (client != null && client.isInitialized()) {
            return client;
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("amap.api-key is required for AMap MCP server");
        }

        ServerParameters params = ServerParameters.builder(npxCommand())
                .args("-y", "@amap/amap-maps-mcp-server")
                .addEnvVar("AMAP_MAPS_API_KEY", apiKey)
                .build();
        StdioClientTransport transport = new StdioClientTransport(params);
        transport.setStdErrorHandler(line -> log.warn("[amap-mcp] {}", line));

        client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(30))
                .initializationTimeout(Duration.ofSeconds(30))
                .clientInfo(new McpSchema.Implementation("yu-ai-agent", "1.0.0"))
                .build();
        client.initialize();
        log.info("AMap MCP server initialized with tools: {}", listToolNamesUnsafe(client));
        return client;
    }

    private String npxCommand() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win") ? "npx.cmd" : "npx";
    }

    private List<String> listToolNamesUnsafe(McpSyncClient mcp) {
        return mcp.listTools().tools().stream()
                .map(McpSchema.Tool::name)
                .toList();
    }

    @PreDestroy
    public void close() {
        if (client != null) {
            client.closeGracefully();
        }
    }

    static String extractContentText(List<?> content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        return content.stream()
                .map(AmapMcpClient::contentToText)
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining("\n"));
    }

    private static String contentToText(Object item) {
        if (item == null) {
            return "";
        }
        try {
            Method text = item.getClass().getMethod("text");
            Object value = text.invoke(item);
            return value == null ? "" : String.valueOf(value);
        } catch (Exception ignored) {
            return String.valueOf(item);
        }
    }
}

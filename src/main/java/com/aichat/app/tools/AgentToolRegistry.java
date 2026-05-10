package com.aichat.app.tools;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Tool registry used by the plan-execute agent.
 */
@Component
public class AgentToolRegistry {

    private final AmapMcpTools amapMcpTools;
    private final WebSearchTool webSearchTool;
    private final WebScrapingTool webScrapingTool;
    private final List<ToolSpecification> toolSpecifications;

    public AgentToolRegistry(AmapMcpTools amapMcpTools,
                             WebSearchTool webSearchTool,
                             WebScrapingTool webScrapingTool) {
        this.amapMcpTools = amapMcpTools;
        this.webSearchTool = webSearchTool;
        this.webScrapingTool = webScrapingTool;
        this.toolSpecifications = buildToolSpecifications();
    }

    public List<ToolSpecification> getToolSpecifications() {
        return toolSpecifications;
    }

    public String execute(String toolName, String arguments) {
        return switch (toolName) {
            case "mapsGeo" -> amapMcpTools.mapsGeo(arguments);
            case "mapsTextSearch" -> amapMcpTools.mapsTextSearch(arguments);
            case "mapsAroundSearch" -> amapMcpTools.mapsAroundSearch(arguments);
            case "mapsSearchDetail" -> amapMcpTools.mapsSearchDetail(arguments);
            case "mapsDirectionWalking" -> amapMcpTools.mapsDirectionWalking(arguments);
            case "searchWeb" -> webSearchTool.searchWeb(arguments);
            case "searchReviews" -> webSearchTool.searchReviews(arguments);
            case "scrapeWebPage" -> webScrapingTool.scrapeWebPage(arguments);
            default -> "Unknown tool: " + toolName;
        };
    }

    public AmapMcpTools amapMcpTools() {
        return amapMcpTools;
    }

    public WebSearchTool webSearchTool() {
        return webSearchTool;
    }

    public WebScrapingTool webScrapingTool() {
        return webScrapingTool;
    }

    private List<ToolSpecification> buildToolSpecifications() {
        List<ToolSpecification> specs = new ArrayList<>();
        specs.addAll(ToolSpecifications.toolSpecificationsFrom(amapMcpTools));
        specs.addAll(ToolSpecifications.toolSpecificationsFrom(webSearchTool));
        specs.addAll(ToolSpecifications.toolSpecificationsFrom(webScrapingTool));
        return specs;
    }
}

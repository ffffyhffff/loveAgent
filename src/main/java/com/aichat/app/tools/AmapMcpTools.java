package com.aichat.app.tools;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LangChain4j tool facade backed by the official AMap MCP server.
 */
@Component
public class AmapMcpTools {

    private final AmapMcpClient amapMcpClient;

    public AmapMcpTools(AmapMcpClient amapMcpClient) {
        this.amapMcpClient = amapMcpClient;
    }

    @Tool("AMap MCP geocoding. Input JSON: {\"address\":\"杭州西湖\",\"city\":\"杭州\"}. Returns official AMap MCP maps_geo result.")
    public String mapsGeo(@P("JSON arguments with address and optional city") String jsonArgs) {
        JSONObject json = parse(jsonArgs);
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("address", json.getStr("address", ""));
        if (json.containsKey("city")) {
            args.put("city", json.getStr("city", ""));
        }
        return amapMcpClient.callTool("maps_geo", args);
    }

    @Tool("AMap MCP text POI search. Input JSON: {\"keywords\":\"咖啡厅\",\"city\":\"杭州\",\"citylimit\":\"true\"}.")
    public String mapsTextSearch(@P("JSON arguments with keywords, city, optional citylimit") String jsonArgs) {
        JSONObject json = parse(jsonArgs);
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("keywords", json.getStr("keywords", ""));
        args.put("city", json.getStr("city", ""));
        args.put("citylimit", json.getStr("citylimit", "true"));
        if (json.containsKey("types")) {
            args.put("types", json.getStr("types", ""));
        }
        return amapMcpClient.callTool("maps_text_search", args);
    }

    @Tool("AMap MCP around POI search. Input JSON: {\"keywords\":\"餐厅\",\"location\":\"120.1,30.2\",\"radius\":\"3000\"}.")
    public String mapsAroundSearch(@P("JSON arguments with keywords, location lon,lat, and radius") String jsonArgs) {
        JSONObject json = parse(jsonArgs);
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("keywords", json.getStr("keywords", ""));
        args.put("location", json.getStr("location", ""));
        args.put("radius", json.getStr("radius", "3000"));
        return amapMcpClient.callTool("maps_around_search", args);
    }

    @Tool("AMap MCP POI detail. Input JSON: {\"id\":\"B0...\"}. Returns rating, cost, photos and other official detail data when available.")
    public String mapsSearchDetail(@P("JSON arguments with POI id") String jsonArgs) {
        JSONObject json = parse(jsonArgs);
        return amapMcpClient.callTool("maps_search_detail", Map.of("id", json.getStr("id", "")));
    }

    @Tool("AMap MCP walking route. Input JSON: {\"origin\":\"120.1,30.2\",\"destination\":\"120.3,30.4\"}.")
    public String mapsDirectionWalking(@P("JSON arguments with origin and destination lon,lat") String jsonArgs) {
        JSONObject json = parse(jsonArgs);
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("origin", json.getStr("origin", ""));
        args.put("destination", json.getStr("destination", ""));
        return amapMcpClient.callTool("maps_direction_walking", args);
    }

    private JSONObject parse(String jsonArgs) {
        if (jsonArgs == null || jsonArgs.isBlank()) {
            return new JSONObject();
        }
        try {
            return JSONUtil.parseObj(jsonArgs);
        } catch (Exception ignored) {
            JSONObject json = new JSONObject();
            json.set("keywords", jsonArgs);
            json.set("address", jsonArgs);
            return json;
        }
    }
}

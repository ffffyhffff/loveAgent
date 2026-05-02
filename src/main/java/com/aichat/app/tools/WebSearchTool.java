package com.aichat.app.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 网页搜索工具
 */
@Slf4j
public class WebSearchTool {

    private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search";
    private static final String API_KEY = System.getenv().getOrDefault("SEARCH_API_KEY", "");

    @Tool("从搜索引擎搜索信息，输入搜索关键词")
    public String searchWeb(String query) {
        if (API_KEY.isEmpty()) {
            return "搜索功能未配置 API Key，无法使用。";
        }
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("q", query);
        paramMap.put("api_key", API_KEY);
        paramMap.put("engine", "baidu");
        try {
            String response = HttpUtil.get(SEARCH_API_URL, paramMap);
            JSONObject jsonObject = JSONUtil.parseObj(response);
            JSONArray organicResults = jsonObject.getJSONArray("organic_results");
            if (organicResults == null || organicResults.isEmpty()) {
                return "未找到搜索结果";
            }
            List<Object> objects = organicResults.subList(0, Math.min(5, organicResults.size()));
            return objects.stream().map(obj -> {
                JSONObject item = (JSONObject) obj;
                return item.getStr("title", "") + " - " + item.getStr("link", "");
            }).collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.error("搜索失败", e);
            return "搜索失败: " + e.getMessage();
        }
    }
}

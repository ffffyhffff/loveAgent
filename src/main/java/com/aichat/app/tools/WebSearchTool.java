package com.aichat.app.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 网页搜索工具（直接抓取百度，不依赖第三方 API）
 */
@Slf4j
public class WebSearchTool {

    @Tool("在百度上搜索信息。输入搜索关键词，返回搜索结果（标题、链接、摘要）。可以搜任何内容：新闻、景点介绍、餐厅评价等。")
    public String searchWeb(
            @dev.langchain4j.agent.tool.P("搜索关键词") String query
    ) {
        log.info("[WebSearchTool] 搜索: {}", query);
        try {
            Document doc = Jsoup.connect("https://www.baidu.com/s")
                    .data("wd", query)
                    .data("pn", "0")
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .timeout(10000)
                    .get();

            List<Map<String, String>> results = new ArrayList<>();

            // 提取搜索结果
            Elements items = doc.select("div.result, div.c-container");
            for (Element item : items.subList(0, Math.min(items.size(), 8))) {
                Map<String, String> result = new LinkedHashMap<>();

                // 标题
                Element titleEl = item.selectFirst("h3 a, .t a");
                if (titleEl == null) continue;
                result.put("title", titleEl.text().trim());
                result.put("url", titleEl.attr("href"));

                // 摘要
                Element abstractEl = item.selectFirst(".c-abstract, .c-span-last .content-right_8Zs40, .c-font-normal");
                result.put("snippet", abstractEl != null ? abstractEl.text().trim() : "");

                // 图片（如果有）
                Element imgEl = item.selectFirst("img");
                if (imgEl != null && imgEl.hasAttr("src")) {
                    result.put("image", imgEl.attr("src"));
                }

                results.add(result);
            }

            if (results.isEmpty()) {
                return "未找到搜索结果";
            }

            // 格式化输出
            StringBuilder sb = new StringBuilder();
            sb.append("搜索「").append(query).append("」找到 ").append(results.size()).append(" 条结果：\n\n");
            for (int i = 0; i < results.size(); i++) {
                Map<String, String> r = results.get(i);
                sb.append(i + 1).append(". ").append(r.get("title")).append("\n");
                if (!r.get("snippet").isEmpty()) {
                    sb.append("   ").append(r.get("snippet")).append("\n");
                }
                sb.append("   来源：").append(r.get("url")).append("\n");
                if (r.containsKey("image")) {
                    sb.append("   图片：").append(r.get("image")).append("\n");
                }
                sb.append("\n");
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("百度搜索失败: {}", query, e);
            return "搜索失败: " + e.getMessage();
        }
    }

    @Tool("搜索地点的评价和评分。输入地点名称，从百度搜索该地点的用户评价、评分、人均消费等信息。")
    public String searchReviews(
            @dev.langchain4j.agent.tool.P("地点名称，如：楼外楼孤山店") String placeName
    ) {
        log.info("[WebSearchTool] 搜索评价: {}", placeName);
        try {
            // 搜索"地点名 评价 人均"
            String query = placeName + " 评价 人均";
            Document doc = Jsoup.connect("https://www.baidu.com/s")
                    .data("wd", query)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .timeout(10000)
                    .get();

            StringBuilder sb = new StringBuilder();
            sb.append(placeName).append(" 的评价信息：\n\n");

            // 提取前 5 条结果
            Elements items = doc.select("div.result, div.c-container");
            int count = 0;
            for (Element item : items) {
                if (count >= 5) break;

                Element titleEl = item.selectFirst("h3 a, .t a");
                if (titleEl == null) continue;

                String title = titleEl.text().trim();
                String url = titleEl.attr("href");

                // 提取摘要中的评分、人均等关键词
                String text = item.text();

                sb.append("- ").append(title).append("\n");

                // 尝试提取评分
                if (text.contains("分") || text.contains("星")) {
                    for (String part : text.split("[，。,\\s]")) {
                        if (part.contains("分") || part.contains("星") || part.contains("人均") || part.contains("评分")) {
                            sb.append("  ").append(part.trim()).append("\n");
                        }
                    }
                }

                sb.append("  来源：").append(url).append("\n\n");
                count++;
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("搜索评价失败: {}", placeName, e);
            return "搜索评价失败: " + e.getMessage();
        }
    }
}

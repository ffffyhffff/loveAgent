package com.aichat.app.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 网页搜索工具（Bing 搜索，支持评价、图片、来源）
 */
@Slf4j
@Component
public class WebSearchTool {

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";

    @Tool("搜索信息。输入关键词，返回搜索结果（标题、摘要、来源链接）。")
    public String searchWeb(
            @dev.langchain4j.agent.tool.P("搜索关键词") String query
    ) {
        log.info("[searchWeb] 搜索: {}", query);
        try {
            String encodedQ = URLEncoder.encode(query, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect("https://cn.bing.com/search?q=" + encodedQ)
                    .userAgent(UA)
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .timeout(10000)
                    .get();

            StringBuilder sb = new StringBuilder();
            Elements results = doc.select("li.b_algo");
            int count = 0;
            for (Element item : results) {
                if (count >= 5) break;
                Element titleEl = item.selectFirst("h2 a");
                if (titleEl == null) continue;

                String title = titleEl.text().trim();
                String url = titleEl.attr("href");
                Element snippetEl = item.selectFirst(".b_caption p, .b_algoSlug");
                String snippet = snippetEl != null ? snippetEl.text().trim() : "";

                sb.append(count + 1).append(". ").append(title).append("\n");
                if (!snippet.isEmpty()) sb.append("   ").append(snippet).append("\n");
                sb.append("   来源：").append(url).append("\n\n");
                count++;
            }

            return count > 0 ? sb.toString() : "未找到搜索结果";
        } catch (Exception e) {
            log.error("搜索失败: {}", query, e);
            return "搜索失败: " + e.getMessage();
        }
    }

    @Tool("搜索地点的图片。输入地点名称，返回相关图片链接。")
    public String searchReviews(
            @dev.langchain4j.agent.tool.P("地点名称，如：楼外楼孤山店") String placeName
    ) {
        log.info("[searchReviews] 搜索: {}", placeName);
        StringBuilder result = new StringBuilder();

        // 搜索图片
        try {
            List<String> imageUrls = searchImages(placeName);
            if (!imageUrls.isEmpty()) {
                result.append("📸 ").append(placeName).append(" 的图片：\n");
                for (int i = 0; i < Math.min(imageUrls.size(), 3); i++) {
                    result.append("  ").append(imageUrls.get(i)).append("\n");
                }
            } else {
                result.append("未找到 ").append(placeName).append(" 的图片");
            }
        } catch (Exception e) {
            log.error("搜索图片失败: {}", placeName, e);
            return "搜索失败: " + e.getMessage();
        }

        return result.toString();
    }

    private Document fetchBing(String encodedQuery) throws Exception {
        return Jsoup.connect("https://cn.bing.com/search?q=" + encodedQuery)
                .userAgent(UA)
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Accept", "text/html,application/xhtml+xml")
                .timeout(10000)
                .get();
    }

    /**
     * 搜索图片（Bing 图片搜索）
     */
    public List<String> searchImages(String query) {
        List<String> imageUrls = new ArrayList<>();
        try {
            String encodedQ = URLEncoder.encode(query, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect("https://cn.bing.com/images/search?q=" + encodedQ + "&first=1")
                    .userAgent(UA)
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .timeout(10000)
                    .get();

            // 方法1：从 img 标签提取
            Elements imgs = doc.select("img.mimg, a.iusc img, div.img_cont img");
            for (Element img : imgs) {
                String src = img.attr("src");
                String m = img.attr("m"); // Bing 的 m 属性包含真实 URL
                String realUrl = extractImageUrl(m, src);
                if (realUrl != null && realUrl.startsWith("http") && !imageUrls.contains(realUrl)) {
                    imageUrls.add(realUrl);
                }
                if (imageUrls.size() >= 3) break;
            }

            // 方法2：从 data-src 提取
            if (imageUrls.isEmpty()) {
                Elements lazyImgs = doc.select("img[data-src]");
                for (Element img : lazyImgs) {
                    String src = img.attr("data-src");
                    if (src.startsWith("http") && !imageUrls.contains(src)) {
                        imageUrls.add(src);
                    }
                    if (imageUrls.size() >= 3) break;
                }
            }

            // 方法3：从 a.iusc 的 href/m 属性提取
            if (imageUrls.isEmpty()) {
                Elements links = doc.select("a.iusc");
                for (Element link : links) {
                    String m = link.attr("m");
                    String url = extractImageUrlFromM(m);
                    if (url != null && !imageUrls.contains(url)) {
                        imageUrls.add(url);
                    }
                    if (imageUrls.size() >= 3) break;
                }
            }

            log.info("图片搜索 [{}] → {} 张", query, imageUrls.size());
        } catch (Exception e) {
            log.error("图片搜索失败: {}", query, e);
        }
        return imageUrls;
    }

    private String extractImageUrl(String m, String fallbackSrc) {
        if (m != null && !m.isEmpty()) {
            String url = extractImageUrlFromM(m);
            if (url != null) return url;
        }
        if (fallbackSrc != null && fallbackSrc.startsWith("http")) {
            return fallbackSrc;
        }
        return null;
    }

    private String extractImageUrlFromM(String m) {
        try {
            // m 属性是 JSON 格式，包含 "murl" 字段
            if (m.contains("\"murl\"")) {
                int start = m.indexOf("\"murl\"") + 7;
                int end = m.indexOf("\"", start);
                if (end > start) {
                    return m.substring(start, end).replace("\\/", "/");
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}

package com.aichat.app.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * 网页抓取工具
 */
@Slf4j
public class WebScrapingTool {

    @Tool("抓取网页内容，输入网页 URL")
    public String scrapeWebPage(String url) {
        try {
            Document document = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .timeout(10000)
                    .get();
            return document.body().text();
        } catch (Exception e) {
            log.error("抓取网页失败: {}", url, e);
            return "抓取网页失败: " + e.getMessage();
        }
    }
}

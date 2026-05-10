package com.aichat.app.tools;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebToolsIntegrationTest {

    @Test
    void searchWebReturnsLiveSearchResults() {
        WebSearchTool tool = new WebSearchTool();

        String result = tool.searchWeb("杭州西湖 咖啡厅 评价");

        assertThat(result)
                .doesNotContain("搜索失败")
                .doesNotContain("未找到搜索结果");
        assertThat(result.length()).isGreaterThan(50);
    }

    @Test
    void scrapeWebPageReturnsPageText() {
        WebScrapingTool tool = new WebScrapingTool();

        String result = tool.scrapeWebPage("https://example.com/");

        assertThat(result)
                .doesNotContain("抓取网页失败")
                .contains("Example Domain");
    }
}

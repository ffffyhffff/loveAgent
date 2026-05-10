package com.aichat.app.tools;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AmapMcpClientIntegrationTest {

    @Value("${amap.api-key:}")
    private String apiKey;

    @Test
    void listsOfficialAmapMcpToolsWhenApiKeyConfigured() {
        org.junit.jupiter.api.Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
                "amap.api-key is not configured");

        AmapMcpClient client = new AmapMcpClient(apiKey);
        try {
            List<String> tools = client.listToolNames();

            assertThat(tools)
                    .contains("maps_geo", "maps_around_search", "maps_search_detail", "maps_direction_walking");
        } finally {
            client.close();
        }
    }
}

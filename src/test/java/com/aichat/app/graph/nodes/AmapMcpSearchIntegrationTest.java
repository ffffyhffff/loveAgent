package com.aichat.app.graph.nodes;

import com.aichat.app.tools.AmapMcpTools;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AmapMcpSearchIntegrationTest {

    @Autowired
    private AmapMcpTools amapMcpTools;

    @Test
    void searchesPoisAroundWestLakeThroughOfficialMcp() {
        String geo = amapMcpTools.mapsGeo("""
                {"address":"杭州西湖","city":"杭州"}
                """);

        double[] coords = ExecutorNode.parseMcpGeo(geo);
        assertThat(coords).isNotNull();

        String around = amapMcpTools.mapsAroundSearch("""
                {"keywords":"咖啡厅","location":"%s,%s","radius":"3000"}
                """.formatted(coords[0], coords[1]));
        List<Map<String, Object>> pois = ExecutorNode.parseMcpPois(around);
        assertThat(pois).isNotEmpty();
        assertThat(pois.get(0)).containsKeys("id", "name");

        String detail = amapMcpTools.mapsSearchDetail("""
                {"id":"%s"}
                """.formatted(pois.get(0).get("id")));
        List<Map<String, Object>> detailPois = ExecutorNode.parseMcpPois(detail);
        assertThat(detailPois).isNotEmpty();
        assertThat(detailPois.get(0)).containsKeys("longitude", "latitude");
    }
}

package com.aichat.app.graph.nodes;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutorNodeToolParsingTest {

    @Test
    void extractsChinesePoiKeywordsFromPlanSteps() {
        assertThat(ExecutorNode.extractKeyword("搜索杭州西湖附近的西餐厅")).isEqualTo("西餐厅");
        assertThat(ExecutorNode.extractKeyword("搜索杭州西湖附近的茶馆")).isEqualTo("茶馆");
        assertThat(ExecutorNode.extractKeyword("搜索杭州西湖附近的甜品店")).isEqualTo("甜品店");
        assertThat(ExecutorNode.extractKeyword("搜索杭州西湖附近的咖啡厅")).isEqualTo("咖啡厅");
    }

    @Test
    void parsesAmapPoiLocationsFromMcpJson() {
        String json = """
                {
                  "pois": [
                    {"id":"p1","name":"西湖咖啡","address":"杭州西湖","location":"120.1,30.2"},
                    {"id":"p2","name":"湖边公园","address":"杭州","location":"120.3,30.4"}
                  ]
                }
                """;

        List<Map<String, Object>> pois = ExecutorNode.parseMcpPois(json);

        assertThat(pois).hasSize(2);
        assertThat(pois.get(0))
                .containsEntry("id", "p1")
                .containsEntry("name", "西湖咖啡")
                .containsEntry("longitude", 120.1)
                .containsEntry("latitude", 30.2);
    }

    @Test
    void keepsAmapAroundSearchPoisWithoutLocations() {
        String json = """
                {
                  "pois": [
                    {"id":"p1","name":"西湖咖啡","address":"曙光路156号"}
                  ]
                }
                """;

        List<Map<String, Object>> pois = ExecutorNode.parseMcpPois(json);

        assertThat(pois).hasSize(1);
        assertThat(pois.get(0))
                .containsEntry("id", "p1")
                .containsEntry("name", "西湖咖啡")
                .doesNotContainKeys("longitude", "latitude");
    }

    @Test
    void parsesGeoLocationFromMcpJson() {
        String json = """
                {"geocodes":[{"location":"120.1,30.2"}]}
                """;

        double[] coords = ExecutorNode.parseMcpGeo(json);

        assertThat(coords).containsExactly(120.1, 30.2);
    }

    @Test
    void parsesPoiDetailFieldsAndPhotoFromMcpJson() {
        String json = """
                {
                  "id":"p1",
                  "name":"Cafe One",
                  "address":"West Lake",
                  "location":"120.1,30.2",
                  "rating":"4.8",
                  "cost":"86.00",
                  "opentime2":"10:00-22:00",
                  "photos":{"url":"https://img.example/a.jpg"}
                }
                """;

        List<Map<String, Object>> pois = ExecutorNode.parseMcpPois(json);

        assertThat(pois).hasSize(1);
        assertThat(pois.get(0))
                .containsEntry("rating", "4.8")
                .containsEntry("cost", "86.00")
                .containsEntry("opentime2", "10:00-22:00")
                .containsEntry("image", "https://img.example/a.jpg");
    }
}

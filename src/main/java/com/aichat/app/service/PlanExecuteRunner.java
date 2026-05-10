package com.aichat.app.service;

import com.aichat.app.graph.PlanExecuteGraph;
import com.aichat.app.graph.PlanExecuteState;
import com.aichat.app.graph.nodes.ExecutorNode;
import com.aichat.app.graph.nodes.PlannerNode;
import com.aichat.app.tools.AmapTools;
import com.aichat.app.tools.PdfGenerationTool;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Runs the date planning Plan-and-Execute graph and bridges graph events to SSE.
 */
@Service
public class PlanExecuteRunner {

    private final PlanExecuteGraph graph;
    private final PlannerNode plannerNode;
    private final ExecutorNode executorNode;
    private final AmapTools amapTools;
    private final PdfGenerationTool pdfGenerationTool;

    public PlanExecuteRunner(PlanExecuteGraph graph,
                             PlannerNode plannerNode,
                             ExecutorNode executorNode,
                             AmapTools amapTools,
                             PdfGenerationTool pdfGenerationTool) {
        this.graph = graph;
        this.plannerNode = plannerNode;
        this.executorNode = executorNode;
        this.amapTools = amapTools;
        this.pdfGenerationTool = pdfGenerationTool;
    }

    public synchronized PlanExecuteState run(String location, String budget, String style,
                                             String occasion, String activity,
                                             Consumer<Map<String, Object>> onEvent) throws Exception {
        plannerNode.setOnEvent(onEvent);
        executorNode.setOnEvent(event -> {
            onEvent.accept(event);
            sleep(180);
        });

        try {
            PlanExecuteState initial = PlanExecuteState.fromGoal(
                    "规划约会", location, budget, style, occasion, activity);

            Optional<PlanExecuteState> result = graph.getCompiledGraph().invoke(initial.data());
            PlanExecuteState finalState = result.orElse(initial);

            emitMapAndPdf(finalState, location, budget, style, occasion, activity, onEvent);
            return finalState;
        } finally {
            plannerNode.setOnEvent(null);
            executorNode.setOnEvent(null);
        }
    }

    private void emitMapAndPdf(PlanExecuteState state, String location, String budget,
                               String style, String occasion, String activity,
                               Consumer<Map<String, Object>> onEvent) {
        List<Map<String, Object>> selectedPois = itineraryPois(state.getSelectedPois());
        if (selectedPois.size() >= 2) {
            onEvent.accept(Map.of(
                    "type", "pois",
                    "categories", List.of(Map.of(
                            "label", "推荐地点",
                            "key", "selected",
                            "items", selectedPois
                    )),
                    "selected", Map.of("selected", 0)
            ));

            Map<String, Object> mapEvent = new LinkedHashMap<>();
            mapEvent.put("type", "map");
            mapEvent.put("pois", selectedPois);
            mapEvent.put("location", location);
            mapEvent.put("budget", budget);
            mapEvent.put("style", style);
            Map<String, Object> routeInfo = buildRouteInfo(selectedPois);
            if (!routeInfo.isEmpty()) {
                mapEvent.put("routeInfo", routeInfo);
            }
            onEvent.accept(mapEvent);
        }

        try {
            String pdfPath = pdfGenerationTool.generate(
                    location, budget, style, occasion, activity,
                    selectedPois, buildPlanDescription(selectedPois));
            if (pdfPath != null) {
                String filename = pdfPath.substring(pdfPath.lastIndexOf("/") + 1);
                if (filename.isEmpty()) filename = pdfPath.substring(pdfPath.lastIndexOf("\\") + 1);
                onEvent.accept(Map.of("type", "pdf", "url", "/api/chat/pdf/" + filename));
            }
        } catch (Exception ignored) {
            onEvent.accept(Map.of("type", "text", "content", "PDF 生成遇到问题，路线和地点推荐已完成。"));
        }
    }

    private String buildPlanDescription(List<Map<String, Object>> itineraryPois) {
        StringBuilder desc = new StringBuilder("## 约会行程安排\n\n");
        String[] labels = {"第一站", "第二站", "第三站", "第四站", "第五站"};
        for (int i = 0; i < itineraryPois.size(); i++) {
            Map<String, Object> poi = itineraryPois.get(i);
            String label = i < labels.length ? labels[i] : "第" + (i + 1) + "站";
            desc.append("### ").append(label).append("\n");
            desc.append("- **").append(poi.getOrDefault("name", "未知地点")).append("**\n");
            desc.append("- 地址：").append(poi.getOrDefault("address", "暂无")).append("\n");
            appendOptional(desc, "评分", poi.get("rating"));
            appendOptional(desc, "人均", poi.get("cost"));
            Object openTime = poi.getOrDefault("open_time", poi.get("opentime2"));
            appendOptional(desc, "营业时间", openTime);
            appendOptional(desc, "图片", poi.get("image"));
            desc.append("\n");
        }
        return desc.toString();
    }

    private void appendOptional(StringBuilder desc, String label, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            desc.append("- ").append(label).append("：").append(value).append("\n");
        }
    }

    private List<Map<String, Object>> itineraryPois(List<Map<String, Object>> pois) {
        if (pois == null || pois.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> unique = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (Map<String, Object> poi : pois) {
            String key = String.valueOf(poi.getOrDefault("id", ""));
            if (key.isBlank()) {
                key = String.valueOf(poi.getOrDefault("name", ""))
                        + "|" + String.valueOf(poi.getOrDefault("longitude", ""))
                        + "|" + String.valueOf(poi.getOrDefault("latitude", ""));
            }
            if (seen.add(key)) {
                unique.add(poi);
            }
            if (unique.size() == 3) {
                break;
            }
        }
        return unique;
    }

    private Map<String, Object> buildRouteInfo(List<Map<String, Object>> selectedPois) {
        Map<String, Object> routeInfo = new LinkedHashMap<>();
        if (selectedPois == null || selectedPois.size() < 2) {
            return routeInfo;
        }

        try {
            double[] origin = {
                    toDouble(selectedPois.get(0).get("longitude")),
                    toDouble(selectedPois.get(0).get("latitude"))
            };
            double[] destination = {
                    toDouble(selectedPois.get(1).get("longitude")),
                    toDouble(selectedPois.get(1).get("latitude"))
            };
            if (origin[0] == 0 || origin[1] == 0 || destination[0] == 0 || destination[1] == 0) {
                return routeInfo;
            }
            AmapTools.RouteResult route = amapTools.walkingRoute(origin, destination);
            if (route.getDistance() != null) {
                routeInfo.put("distance", route.getDistance());
            }
            if (route.getDuration() != null) {
                routeInfo.put("duration", route.getDuration());
            }
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
        return routeInfo;
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}

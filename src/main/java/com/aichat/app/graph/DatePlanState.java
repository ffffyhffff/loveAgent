package com.aichat.app.graph;

import lombok.Data;
import org.bsc.langgraph4j.state.AgentState;

import java.util.*;

/**
 * 约会规划 LangGraph4j 状态
 *
 * LangGraph4j 的 AgentState 是一个 Map<String,Object> 包装器。
 * 每个节点返回 Map 作为 partial update，合并到 state 中。
 */
@Data
public class DatePlanState extends AgentState {

    // State keys
    public static final String USER_MESSAGE = "userMessage";
    public static final String AI_RESPONSE = "aiResponse";
    public static final String ACTION = "action";           // "chat" 或 "plan"
    public static final String PLAN_DESC = "planDescription";
    public static final String PLAN_STEPS = "planSteps";
    public static final String DATE_LOCATION = "dateLocation";
    public static final String DATE_BUDGET = "dateBudget";
    public static final String DATE_STYLE = "dateStyle";
    public static final String USER_CHOICE = "userChoice";
    public static final String CAFES = "cafes";
    public static final String SPOTS = "spots";
    public static final String RESTAURANTS = "restaurants";
    public static final String SELECTED_CAFE = "selectedCafe";
    public static final String SELECTED_SPOT = "selectedSpot";
    public static final String SELECTED_RESTAURANT = "selectedRestaurant";
    public static final String ROUTE_DISTANCE = "routeDistance";
    public static final String ROUTE_DURATION = "routeDuration";
    public static final String PDF_URL = "pdfUrl";

    /**
     * 工厂方法：创建初始状态
     */
    public static DatePlanState create(String userMessage) {
        Map<String, Object> data = new HashMap<>();
        data.put(USER_MESSAGE, userMessage);
        data.put(ACTION, "");
        data.put(PLAN_STEPS, new ArrayList<String>());
        data.put(CAFES, new ArrayList<Map<String, Object>>());
        data.put(SPOTS, new ArrayList<Map<String, Object>>());
        data.put(RESTAURANTS, new ArrayList<Map<String, Object>>());
        return new DatePlanState(data);
    }

    public DatePlanState(Map<String, Object> data) {
        super(data);
    }

    // === 便捷方法 ===

    public String getUserMessage() {
        return value(USER_MESSAGE).map(Object::toString).orElse("");
    }

    public String getAiResponse() {
        return value(AI_RESPONSE).map(Object::toString).orElse(null);
    }

    public String getAction() {
        return value(ACTION).map(Object::toString).orElse("");
    }

    public String getUserChoice() {
        return value(USER_CHOICE).map(Object::toString).orElse(null);
    }

    public String getDateLocation() {
        return value(DATE_LOCATION).map(Object::toString).orElse("");
    }

    public String getDateBudget() {
        return value(DATE_BUDGET).map(Object::toString).orElse("");
    }

    public String getDateStyle() {
        return value(DATE_STYLE).map(Object::toString).orElse("");
    }

    @SuppressWarnings("unchecked")
    public List<String> getPlanSteps() {
        return value(PLAN_STEPS).map(v -> (List<String>) v).orElse(List.of());
    }

    public String getPlanDescription() {
        return value(PLAN_DESC).map(Object::toString).orElse("");
    }

    public String getPdfUrl() {
        return value(PDF_URL).map(Object::toString).orElse(null);
    }

    /**
     * POI 信息
     */
    @Data
    public static class PoiInfo {
        private String name;
        private String address;
        private double longitude;
        private double latitude;

        public Map<String, Object> toMap() {
            Map<String, Object> m = new HashMap<>();
            m.put("name", name);
            m.put("address", address);
            m.put("longitude", longitude);
            m.put("latitude", latitude);
            return m;
        }

        public static PoiInfo fromMap(Map<String, Object> m) {
            PoiInfo poi = new PoiInfo();
            poi.setName((String) m.get("name"));
            poi.setAddress((String) m.get("address"));
            poi.setLongitude(m.get("longitude") instanceof Number n ? n.doubleValue() : 0);
            poi.setLatitude(m.get("latitude") instanceof Number n ? n.doubleValue() : 0);
            return poi;
        }
    }
}

package com.aichat.app.graph;

import lombok.Data;
import org.bsc.langgraph4j.state.AgentState;

import java.util.*;

/**
 * Plan-and-Execute 状态
 *
 * 存储：用户目标、计划步骤、每步结果、当前进度
 */
@Data
public class PlanExecuteState extends AgentState implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    public static final String USER_GOAL = "userGoal";
    public static final String PLAN_STEPS = "planSteps";
    public static final String CURRENT_STEP_INDEX = "currentStep";
    public static final String STEP_RESULTS = "stepResults";
    public static final String ACCUMULATED_CONTEXT = "accumulatedContext";
    public static final String ACTION = "action";
    public static final String FINAL_ANSWER = "finalAnswer";
    public static final String DATE_LOCATION = "dateLocation";
    public static final String DATE_BUDGET = "dateBudget";
    public static final String DATE_STYLE = "dateStyle";
    public static final String DATE_OCCASION = "dateOccasion";
    public static final String DATE_ACTIVITY = "dateActivity";
    public static final String SELECTED_POIS = "selectedPois";
    public static final String CANDIDATE_POIS = "candidatePois";
    public static final String ITINERARY_ENRICHED = "itineraryEnriched";
    public static final String ROUTE_INFO = "routeInfo";
    public static final String PDF_URL = "pdfUrl";

    public PlanExecuteState(Map<String, Object> data) {
        super(data);
    }

    public static PlanExecuteState create(Map<String, Object> initData) {
        Map<String, Object> data = new HashMap<>();
        for (var e : initData.entrySet()) {
            data.put(String.valueOf(e.getKey()), e.getValue());
        }
        data.putIfAbsent(PLAN_STEPS, new ArrayList<String>());
        data.putIfAbsent(CURRENT_STEP_INDEX, "0");
        data.putIfAbsent(STEP_RESULTS, new LinkedHashMap<String, String>());
        data.putIfAbsent(ACCUMULATED_CONTEXT, "");
        data.putIfAbsent(ACTION, "");
        data.putIfAbsent(FINAL_ANSWER, "");
        return new PlanExecuteState(data);
    }

    public static PlanExecuteState fromGoal(String goal, String location, String budget,
                                             String style, String occasion, String activity) {
        Map<String, Object> data = new HashMap<>();
        data.put(USER_GOAL, goal);
        data.put(DATE_LOCATION, location != null ? location : "");
        data.put(DATE_BUDGET, budget != null ? budget : "");
        data.put(DATE_STYLE, style != null ? style : "");
        data.put(DATE_OCCASION, occasion != null ? occasion : "");
        data.put(DATE_ACTIVITY, activity != null ? activity : "");
        return create(data);
    }

    public String getUserGoal() {
        return value(USER_GOAL).map(Object::toString).orElse("");
    }

    @SuppressWarnings("unchecked")
    public List<String> getPlanSteps() {
        return (List<String>) value(PLAN_STEPS).orElse(new ArrayList<>());
    }

    public int getCurrentStepIndex() {
        return value(CURRENT_STEP_INDEX).map(v -> {
            if (v instanceof Integer) return (int) v;
            return Integer.parseInt(v.toString());
        }).orElse(0);
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getStepResults() {
        Object val = value(STEP_RESULTS).orElse(new LinkedHashMap<String, String>());
        Map<String, String> result = new LinkedHashMap<>();
        if (val instanceof Map) {
            ((Map<?, ?>) val).forEach((k, v) -> result.put(String.valueOf(k), String.valueOf(v)));
        }
        return result;
    }

    public String getAccumulatedContext() {
        return value(ACCUMULATED_CONTEXT).map(Object::toString).orElse("");
    }

    public String getAction() {
        return value(ACTION).map(Object::toString).orElse("");
    }

    public String getFinalAnswer() {
        return value(FINAL_ANSWER).map(Object::toString).orElse("");
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getSelectedPois() {
        Object val = value(SELECTED_POIS).orElse(new ArrayList<Map<String, Object>>());
        if (val instanceof List<?>) {
            return (List<Map<String, Object>>) val;
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getRouteInfo() {
        Object val = value(ROUTE_INFO).orElse(new LinkedHashMap<String, Object>());
        if (val instanceof Map<?, ?>) {
            Map<String, Object> result = new LinkedHashMap<>();
            ((Map<?, ?>) val).forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        return new LinkedHashMap<>();
    }

    public String getPdfUrl() {
        return value(PDF_URL).map(Object::toString).orElse("");
    }
}

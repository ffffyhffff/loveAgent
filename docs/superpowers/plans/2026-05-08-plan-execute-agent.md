# Plan-and-Execute Agent 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 用 LangGraph4j 实现真正的 Plan-and-Execute 智能体——LLM 生成计划、逐步执行、每步完成后自动决定下一步。

**架构：** LangGraph4j StateGraph，3 个节点（planner → executor → replanner）+ 条件边。Planner 用 LLM 生成结构化 JSON 计划。Executor 对每一步单独调 LLM（带工具），执行搜索/详情获取。Replanner 判断继续/重规划/完成。前端用单个执行面板实时展示进度。

**技术栈：** LangGraph4j 1.5.14 / LangChain4j 1.0.0-beta3 / DashScope qwen-plus / Amap REST API / SSE / Vue 3

---

## 文件清单

| 文件 | 职责 | 操作 |
|------|------|------|
| `PlanExecuteState.java` (新) | LangGraph 状态：计划、步骤、结果 | 创建 |
| `PlanExecuteGraph.java` (新) | 构建 StateGraph（planner/executor/replanner） | 创建 |
| `PlannerNode.java` (新) | LLM 生成结构化计划 | 创建 |
| `ExecutorNode.java` (新) | LLM + 工具执行单个步骤 | 创建 |
| `ReplannerNode.java` (新) | LLM 判断继续/重规划/完成 | 创建 |
| `LoveAgentService.java` | 调用 PlanExecuteGraph，推送 SSE | 修改 |
| `SearchPoiNode.java` | 不再使用，逻辑迁移到新架构 | 删除/保留为空 |

---

### 任务 1：PlanExecuteState — LangGraph 状态定义

**文件：**
- 创建：`src/main/java/com/aichat/app/graph/PlanExecuteState.java`

参考现有 `DatePlanState.java` 的模式，创建 Plan-and-Execute 的状态类。

- [ ] **步骤 1：创建状态类**

```java
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
public class PlanExecuteState extends AgentState {

    // State keys
    public static final String USER_GOAL = "userGoal";
    public static final String PLAN_STEPS = "planSteps";           // List<String> 计划步骤
    public static final String CURRENT_STEP_INDEX = "currentStep"; // int 当前步骤索引
    public static final String STEP_RESULTS = "stepResults";       // Map<Integer, String> 每步结果
    public static final String ACCUMULATED_CONTEXT = "accumulatedContext"; // String 累积上下文
    public static final String ACTION = "action";                  // "continue" / "replan" / "done"
    public static final String FINAL_ANSWER = "finalAnswer";       // 最终回复
    public static final String DATE_LOCATION = "dateLocation";
    public static final String DATE_BUDGET = "dateBudget";
    public static final String DATE_STYLE = "dateStyle";
    public static final String DATE_OCCASION = "dateOccasion";
    public static final String DATE_ACTIVITY = "dateActivity";

    public PlanExecuteState(Map<String, Object> initData) {
        super(initData);
    }

    public static PlanExecuteState create(Map<String, Object> initData) {
        Map<String, Object> data = new HashMap<>(initData);
        data.putIfAbsent(PLAN_STEPS, new ArrayList<String>());
        data.putIfAbsent(CURRENT_STEP_INDEX, 0);
        data.putIfAbsent(STEP_RESULTS, new LinkedHashMap<Integer, String>());
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

    // 便捷方法
    public String getUserGoal() {
        return value(USER_GOAL).map(Object::toString).orElse("");
    }

    @SuppressWarnings("unchecked")
    public List<String> getPlanSteps() {
        return (List<String>) value(PLAN_STEPS).orElse(new ArrayList<>());
    }

    public int getCurrentStepIndex() {
        return value(CURRENT_STEP_INDEX).map(v -> (int) v).orElse(0);
    }

    @SuppressWarnings("unchecked")
    public Map<Integer, String> getStepResults() {
        return (Map<Integer, String>) value(STEP_RESULTS).orElse(new LinkedHashMap<>());
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
}
```

- [ ] **步骤 2：编译验证**

运行：`mvn compile -q`
预期：编译通过

---

### 任务 2：PlannerNode — LLM 生成结构化计划

**文件：**
- 创建：`src/main/java/com/aichat/app/graph/nodes/PlannerNode.java`

Planner 节点：接收用户目标，输出结构化步骤列表。每个步骤是一个清晰的描述，Executor 会逐个执行。

- [ ] **步骤 1：创建 PlannerNode**

```java
package com.aichat.app.graph.nodes;

import com.aichat.app.graph.PlanExecuteState;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Planner 节点：LLM 生成结构化执行计划
 *
 * 输入：用户目标 + 约会偏好
 * 输出：步骤列表（JSON 格式）
 */
@Component
@Slf4j
public class PlannerNode implements NodeAction {

    private final ChatLanguageModel chatModel;

    public PlannerNode(ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public Map<String, Object> apply(org.bsc.langgraph4j.state.AgentState agentState) throws Exception {
        PlanExecuteState state = (PlanExecuteState) agentState;
        String goal = state.getUserGoal();
        String location = state.value(PlanExecuteState.DATE_LOCATION).map(Object::toString).orElse("");
        String budget = state.value(PlanExecuteState.DATE_BUDGET).map(Object::toString).orElse("");
        String style = state.value(PlanExecuteState.DATE_STYLE).map(Object::toString).orElse("");
        String occasion = state.value(PlanExecuteState.DATE_OCCASION).map(Object::toString).orElse("");
        String activity = state.value(PlanExecuteState.DATE_ACTIVITY).map(Object::toString).orElse("");

        log.info("Planner：生成计划 - goal={}, location={}", goal, location);

        String prompt = """
                你是一个约会规划助手。根据用户需求，制定搜索和规划的执行计划。

                用户需求：%s
                约会地点：%s
                预算：%s
                风格：%s
                场景：%s
                活动偏好：%s

                可用的操作：
                - search_cafes: 搜索咖啡厅/茶馆
                - search_parks: 搜索公园/景点
                - search_restaurants: 搜索餐厅
                - search_dessert: 搜索甜品店
                - search_flowers: 搜索花店
                - get_poi_detail: 获取地点详情（评分、照片）
                - generate_summary: 生成推荐总结

                请输出 JSON 格式的执行计划，包含 4-6 个步骤：
                {"steps": ["步骤1描述", "步骤2描述", ...]}

                规则：
                - 每步描述要具体，包含搜索关键词
                - 前几步是搜索不同类别的地点
                - 中间步骤获取最佳地点的详情
                - 最后一步是生成推荐总结
                - 只输出 JSON，不要其他内容
                """.formatted(goal, location, budget, style, occasion, activity);

        try {
            List<dev.langchain4j.data.message.ChatMessage> messages = List.of(
                    new SystemMessage(prompt));
            ChatResponse response = chatModel.chat(messages);
            String output = response.aiMessage().text();
            log.info("Planner 输出:\n{}", output);

            // 解析 JSON 步骤
            String cleaned = output.replaceAll("```json|```", "").trim();
            cn.hutool.json.JSONObject json = cn.hutool.json.JSONUtil.parseObj(cleaned);
            cn.hutool.json.JSONArray stepsArr = json.getJSONArray("steps");

            List<String> steps = new ArrayList<>();
            if (stepsArr != null) {
                for (int i = 0; i < stepsArr.size(); i++) {
                    steps.add(stepsArr.getStr(i));
                }
            }

            if (steps.isEmpty()) {
                // Fallback
                steps = getDefaultPlan(occasion, activity);
            }

            log.info("计划已制定，共 {} 步", steps.size());
            for (int i = 0; i < steps.size(); i++) {
                log.info("  {}. {}", i + 1, steps.get(i));
            }

            Map<String, Object> update = new HashMap<>();
            update.put(PlanExecuteState.PLAN_STEPS, steps);
            update.put(PlanExecuteState.CURRENT_STEP_INDEX, 0);
            update.put(PlanExecuteState.STEP_RESULTS, new LinkedHashMap<Integer, String>());
            return update;

        } catch (Exception e) {
            log.error("Planner 失败", e);
            List<String> defaultPlan = getDefaultPlan(occasion, activity);
            Map<String, Object> update = new HashMap<>();
            update.put(PlanExecuteState.PLAN_STEPS, defaultPlan);
            update.put(PlanExecuteState.CURRENT_STEP_INDEX, 0);
            return update;
        }
    }

    private List<String> getDefaultPlan(String occasion, String activity) {
        if (occasion != null && occasion.contains("求婚")) {
            return List.of(
                    "搜索西湖附近的花店",
                    "搜索西湖附近的观景台",
                    "搜索西湖附近的西餐厅",
                    "搜索西湖附近的甜品店",
                    "获取前2个最佳地点的详情（评分和照片）",
                    "生成浪漫求婚约会推荐总结"
            );
        }
        return List.of(
                "搜索西湖附近的咖啡厅",
                "搜索西湖附近的公园和景点",
                "搜索西湖附近的餐厅",
                "搜索西湖附近的甜品店",
                "获取前2个最佳地点的详情（评分和照片）",
                "生成约会推荐总结"
        );
    }
}
```

- [ ] **步骤 2：编译验证**

运行：`mvn compile -q`
预期：编译通过

---

### 任务 3：ExecutorNode — LLM + 工具执行单个步骤

**文件：**
- 创建：`src/main/java/com/aichat/app/graph/nodes/ExecutorNode.java`

Executor 节点：取当前步骤，调 LLM（带工具）执行。LLM 自主决定调用哪个工具来完成这一步。

- [ ] **步骤 1：创建 ExecutorNode**

```java
package com.aichat.app.graph.nodes;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aichat.app.graph.PlanExecuteState;
import com.aichat.app.tools.AmapTools;
import com.aichat.app.tools.McpMapTools;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Consumer;

/**
 * Executor 节点：执行计划中的单个步骤
 *
 * LLM 带工具调用来完成当前步骤
 */
@Component
@Slf4j
public class ExecutorNode implements NodeAction {

    private final ChatLanguageModel chatModel;
    private final McpMapTools mcpMapTools;
    private final AmapTools amapTools;
    private final List<ToolSpecification> toolSpecs;

    // SSE 事件回调（由外部设置）
    private Consumer<Map<String, Object>> onEvent;

    public ExecutorNode(ChatLanguageModel chatModel, McpMapTools mcpMapTools, AmapTools amapTools) {
        this.chatModel = chatModel;
        this.mcpMapTools = mcpMapTools;
        this.amapTools = amapTools;
        this.toolSpecs = mcpMapTools.getToolSpecifications();
    }

    public void setOnEvent(Consumer<Map<String, Object>> onEvent) {
        this.onEvent = onEvent;
    }

    @Override
    public Map<String, Object> apply(org.bsc.langgraph4j.state.AgentState agentState) throws Exception {
        PlanExecuteState state = (PlanExecuteState) agentState;
        List<String> steps = state.getPlanSteps();
        int currentIndex = state.getCurrentStepIndex();

        if (currentIndex >= steps.size()) {
            // 所有步骤已完成
            Map<String, Object> update = new HashMap<>();
            update.put(PlanExecuteState.ACTION, "done");
            return update;
        }

        String currentStep = steps.get(currentIndex);
        String accumulatedContext = state.getAccumulatedContext();

        log.info("Executor：执行步骤 {}/{} - {}", currentIndex + 1, steps.size(), currentStep);

        // 推送步骤开始
        if (onEvent != null) {
            onEvent.accept(Map.of("type", "step",
                    "message", currentStep,
                    "status", "active",
                    "index", currentIndex,
                    "total", steps.size()));
        }

        // 构建执行 prompt
        String location = state.value(PlanExecuteState.DATE_LOCATION).map(Object::toString).orElse("");
        String execPrompt = """
                你是约会规划执行助手。请执行以下步骤：

                步骤：%s
                约会地点：%s

                之前步骤的结果：
                %s

                请使用可用的工具来完成这一步。完成后用纯文本总结结果。
                """.formatted(currentStep, location, accumulatedContext);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(execPrompt));
        messages.add(new UserMessage("请执行上述步骤"));

        // LLM 工具调用循环（执行当前步骤）
        StringBuilder stepResult = new StringBuilder();
        int maxToolCalls = 5;

        for (int i = 0; i < maxToolCalls; i++) {
            ChatRequest request = ChatRequest.builder()
                    .messages(messages)
                    .toolSpecifications(toolSpecs)
                    .build();
            ChatResponse response = chatModel.chat(request);
            AiMessage aiMessage = response.aiMessage();

            if (aiMessage.hasToolExecutionRequests()) {
                messages.add(aiMessage);

                for (ToolExecutionRequest toolReq : aiMessage.toolExecutionRequests()) {
                    String toolName = toolReq.name();
                    String args = toolReq.arguments();

                    log.info("  工具调用: {}({})", toolName,
                            args.length() > 100 ? args.substring(0, 100) + "..." : args);

                    String result = executeTool(toolName, args);

                    // 推送工具结果到前端
                    pushToolResult(toolName, args, result);

                    messages.add(new ToolExecutionResultMessage(toolReq.id(), toolName, result));
                }
            } else {
                // LLM 返回最终结果
                stepResult.append(aiMessage.text());
                break;
            }
        }

        // 更新状态
        String resultText = stepResult.toString();
        Map<Integer, String> results = new LinkedHashMap<>(state.getStepResults());
        results.put(currentIndex, resultText);

        String newContext = accumulatedContext + "\n\n步骤" + (currentIndex + 1) + " (" + currentStep + "):\n" + resultText;

        // 推送步骤完成
        if (onEvent != null) {
            onEvent.accept(Map.of("type", "step",
                    "message", "完成：" + currentStep,
                    "status", "done",
                    "index", currentIndex,
                    "total", steps.size()));
        }

        Map<String, Object> update = new HashMap<>();
        update.put(PlanExecuteState.STEP_RESULTS, results);
        update.put(PlanExecuteState.ACCUMULATED_CONTEXT, newContext);
        update.put(PlanExecuteState.CURRENT_STEP_INDEX, currentIndex + 1);
        update.put(PlanExecuteState.ACTION, "continue");
        return update;
    }

    private String executeTool(String toolName, String args) {
        try {
            JSONObject json = JSONUtil.parseObj(args);
            return switch (toolName) {
                case "geocode" -> mcpMapTools.geocode(json.getStr("address", ""), json.getStr("city", null));
                case "searchPoi" -> mcpMapTools.searchPoi(json.getStr("keyword", ""), json.getStr("city", ""), json.getInt("pageSize", 5));
                case "aroundSearch" -> mcpMapTools.aroundSearch(json.getDouble("longitude", 0.0), json.getDouble("latitude", 0.0), json.getStr("keyword", ""), json.getInt("radius", 3000));
                case "walkingRoute" -> mcpMapTools.walkingRoute(json.getDouble("originLon", 0.0), json.getDouble("originLat", 0.0), json.getDouble("destLon", 0.0), json.getDouble("destLat", 0.0));
                case "placeDetail" -> mcpMapTools.placeDetail(json.getStr("poiId", ""));
                default -> "未知工具: " + toolName;
            };
        } catch (Exception e) {
            log.error("工具执行失败: {}", toolName, e);
            return "工具执行失败: " + e.getMessage();
        }
    }

    private void pushToolResult(String toolName, String args, String result) {
        if (onEvent == null) return;

        JSONObject jsonArgs;
        try { jsonArgs = JSONUtil.parseObj(args); } catch (Exception e) { jsonArgs = null; }

        switch (toolName) {
            case "aroundSearch", "searchPoi" -> {
                if (result.startsWith("[")) {
                    String keyword = jsonArgs != null ? jsonArgs.getStr("keyword", "地点") : "地点";
                    List<Map<String, Object>> items = parsePoiItems(result);
                    if (!items.isEmpty()) {
                        onEvent.accept(Map.of(
                                "type", "section",
                                "title", keyword,
                                "icon", getCatIcon(keyword),
                                "items", items
                        ));
                    }
                }
            }
            case "placeDetail" -> {
                if (!result.contains("失败") && !result.contains("名称：null")) {
                    String placeName = result.contains("名称：") ?
                            result.substring(result.indexOf("名称：") + 3, result.indexOf("\n")).trim() : "";
                    List<String> photoUrls = new ArrayList<>();
                    for (String line : result.split("\n")) {
                        line = line.trim();
                        if (line.startsWith("http")) photoUrls.add(line);
                    }
                    onEvent.accept(Map.of(
                            "type", "review",
                            "placeName", placeName,
                            "content", result,
                            "images", photoUrls
                    ));
                }
            }
        }
    }

    private List<Map<String, Object>> parsePoiItems(String jsonStr) {
        List<Map<String, Object>> items = new ArrayList<>();
        try {
            cn.hutool.json.JSONArray arr = JSONUtil.parseArray(jsonStr);
            for (int i = 0; i < Math.min(arr.size(), 5); i++) {
                JSONObject poi = arr.getJSONObject(i);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", poi.getStr("id"));
                m.put("name", poi.getStr("name"));
                m.put("address", poi.getStr("address"));
                m.put("distance", poi.getStr("distance"));
                items.add(m);
            }
        } catch (Exception ignored) {}
        return items;
    }

    private String getCatIcon(String label) {
        if (label == null) return "📍";
        if (label.contains("花")) return "💐";
        if (label.contains("咖啡") || label.contains("茶")) return "☕";
        if (label.contains("公园") || label.contains("景点") || label.contains("观景")) return "🌸";
        if (label.contains("餐") || label.contains("火锅")) return "🍽️";
        if (label.contains("甜品")) return "🍰";
        if (label.contains("书店") || label.contains("文艺")) return "📚";
        return "📍";
    }
}
```

- [ ] **步骤 2：编译验证**

运行：`mvn compile -q`
预期：编译通过

---

### 任务 4：ReplannerNode — LLM 判断下一步

**文件：**
- 创建：`src/main/java/com/aichat/app/graph/nodes/ReplannerNode.java`

Replanner 节点：每步完成后调用。LLM 看到：原始目标、已完成步骤和结果、剩余步骤。决定 CONTINUE / REPLAN / DONE。

- [ ] **步骤 1：创建 ReplannerNode**

```java
package com.aichat.app.graph.nodes;

import com.aichat.app.graph.PlanExecuteState;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Replanner 节点：判断继续/重规划/完成
 *
 * 每步执行完后调用，LLM 根据当前状态决定下一步
 */
@Component
@Slf4j
public class ReplannerNode implements NodeAction {

    private final ChatLanguageModel chatModel;

    public ReplannerNode(ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public Map<String, Object> apply(org.bsc.langgraph4j.state.AgentState agentState) throws Exception {
        PlanExecuteState state = (PlanExecuteState) agentState;
        String goal = state.getUserGoal();
        List<String> steps = state.getPlanSteps();
        int currentIndex = state.getCurrentStepIndex();
        Map<Integer, String> results = state.getStepResults();
        String context = state.getAccumulatedContext();

        log.info("Replanner：评估进度 - {}/{} 步完成", currentIndex, steps.size());

        // 构建已完成/剩余步骤描述
        StringBuilder completedDesc = new StringBuilder();
        for (int i = 0; i < currentIndex && i < steps.size(); i++) {
            completedDesc.append("步骤").append(i + 1).append(": ").append(steps.get(i));
            String result = results.getOrDefault(i, "");
            if (!result.isEmpty()) {
                String shortResult = result.length() > 200 ? result.substring(0, 200) + "..." : result;
                completedDesc.append(" → 结果: ").append(shortResult);
            }
            completedDesc.append("\n");
        }

        StringBuilder remainingDesc = new StringBuilder();
        for (int i = currentIndex; i < steps.size(); i++) {
            remainingDesc.append("步骤").append(i + 1).append(": ").append(steps.get(i)).append("\n");
        }

        String prompt = """
                你是约会规划的进度评估助手。

                原始目标：%s

                已完成步骤：
                %s

                剩余步骤：
                %s

                累积上下文：
                %s

                请判断下一步行动，只输出以下之一：
                - CONTINUE：继续执行剩余步骤
                - DONE：所有必要信息已收集，可以生成最终推荐

                规则：
                - 如果还有剩余步骤未执行，输出 CONTINUE
                - 如果所有步骤都已完成，输出 DONE
                - 只输出一个词：CONTINUE 或 DONE
                """.formatted(goal, completedDesc.toString(), remainingDesc.toString(),
                        context.length() > 500 ? context.substring(0, 500) + "..." : context);

        try {
            ChatResponse response = chatModel.chat(List.of(
                    new SystemMessage(prompt),
                    new UserMessage("请评估当前进度")));
            String decision = response.aiMessage().text().trim().toUpperCase();
            log.info("Replanner 决定: {}", decision);

            Map<String, Object> update = new HashMap<>();

            if (decision.contains("DONE") || currentIndex >= steps.size()) {
                // 生成最终总结
                String summary = generateFinalSummary(goal, context, state);
                update.put(PlanExecuteState.ACTION, "done");
                update.put(PlanExecuteState.FINAL_ANSWER, summary);
            } else {
                update.put(PlanExecuteState.ACTION, "continue");
            }

            return update;

        } catch (Exception e) {
            log.error("Replanner 失败", e);
            Map<String, Object> update = new HashMap<>();
            if (currentIndex >= steps.size()) {
                update.put(PlanExecuteState.ACTION, "done");
                update.put(PlanExecuteState.FINAL_ANSWER, "约会规划完成！");
            } else {
                update.put(PlanExecuteState.ACTION, "continue");
            }
            return update;
        }
    }

    private String generateFinalSummary(String goal, String context, PlanExecuteState state) {
        try {
            String location = state.value(PlanExecuteState.DATE_LOCATION).map(Object::toString).orElse("");
            String budget = state.value(PlanExecuteState.DATE_BUDGET).map(Object::toString).orElse("");
            String summaryPrompt = """
                    根据以下搜索结果，写一段温馨的约会推荐总结（100字以内，不要用markdown）：

                    目标：%s
                    地点：%s
                    预算：%s

                    搜索结果：
                    %s

                    请写出推荐路线（第一站→第二站→第三站）和推荐理由。
                    """.formatted(goal, location, budget,
                            context.length() > 800 ? context.substring(0, 800) : context);

            ChatResponse response = chatModel.chat(List.of(
                    new SystemMessage(summaryPrompt),
                    new UserMessage("请生成推荐总结")));
            return response.aiMessage().text();
        } catch (Exception e) {
            return "约会规划完成！祝你们约会愉快～💕";
        }
    }
}
```

- [ ] **步骤 2：编译验证**

运行：`mvn compile -q`
预期：编译通过

---

### 任务 5：PlanExecuteGraph — 构建 LangGraph 状态图

**文件：**
- 创建：`src/main/java/com/aichat/app/graph/PlanExecuteGraph.java`

用 LangGraph4j 的 StateGraph 把 3 个节点串起来：planner → executor → replanner（条件边）→ executor 或 END。

- [ ] **步骤 1：创建 PlanExecuteGraph**

```java
package com.aichat.app.graph;

import com.aichat.app.graph.nodes.ExecutorNode;
import com.aichat.app.graph.nodes.PlannerNode;
import com.aichat.app.graph.nodes.ReplannerNode;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * Plan-and-Execute 状态图
 *
 * 节点：planner → executor → replanner
 * 条件边：replanner → executor（继续）或 END（完成）
 */
@Component
@Slf4j
public class PlanExecuteGraph {

    private final PlannerNode plannerNode;
    private final ExecutorNode executorNode;
    private final ReplannerNode replannerNode;
    private CompiledGraph<PlanExecuteState> compiledGraph;

    public PlanExecuteGraph(PlannerNode plannerNode, ExecutorNode executorNode,
                            ReplannerNode replannerNode) {
        this.plannerNode = plannerNode;
        this.executorNode = executorNode;
        this.replannerNode = replannerNode;
    }

    public CompiledGraph<PlanExecuteState> getCompiledGraph() throws GraphStateException {
        if (compiledGraph == null) {
            compiledGraph = buildGraph().compile();
        }
        return compiledGraph;
    }

    private StateGraph<PlanExecuteState> buildGraph() throws GraphStateException {
        StateGraph<PlanExecuteState> graph = new StateGraph<>(PlanExecuteState::create);

        // 添加节点
        graph.addNode("planner", AsyncNodeAction.node_async(plannerNode));
        graph.addNode("executor", AsyncNodeAction.node_async(executorNode));
        graph.addNode("replanner", AsyncNodeAction.node_async(replannerNode));

        // 入口 → planner
        graph.addEdge(START, "planner");

        // planner → executor
        graph.addEdge("planner", "executor");

        // executor → replanner
        graph.addEdge("executor", "replanner");

        // replanner → 条件边
        graph.addConditionalEdges("replanner",
                AsyncEdgeAction.edge_async(state -> {
                    PlanExecuteState s = (PlanExecuteState) state;
                    String action = s.getAction();
                    log.info("Replanner 路由: action={}", action);
                    if ("done".equals(action)) return END;
                    return "executor";  // continue → 再执行一步
                }),
                Map.of("executor", "executor", END, END));

        return graph;
    }
}
```

- [ ] **步骤 2：编译验证**

运行：`mvn compile -q`
预期：编译通过

---

### 任务 6：LoveAgentService — 集成 PlanExecuteGraph

**文件：**
- 修改：`src/main/java/com/aichat/app/service/LoveAgentService.java`

修改 `executeDatePlan` 方法，使用 PlanExecuteGraph 替代原来的 SearchPoiNode 调用。

- [ ] **步骤 1：注入 PlanExecuteGraph**

在 LoveAgentService 的构造函数中添加 `PlanExecuteGraph` 依赖。

- [ ] **步骤 2：重写 executeDatePlan**

```java
private void executeDatePlan(SseEmitter emitter, Consumer<Map<String, Object>> onEvent) {
    String location = collectedPrefs.getOrDefault("location", "");
    String budget = collectedPrefs.getOrDefault("budget", "");
    String style = collectedPrefs.getOrDefault("style", "浪漫");
    String occasion = collectedPrefs.getOrDefault("occasion", "");
    String activity = collectedPrefs.getOrDefault("activity", "");

    try {
        // 设置 Executor 的 SSE 回调
        executorNode.setOnEvent(event -> {
            safeSend(emitter, event);
            if (onEvent != null) onEvent.accept(event);
            sleep(300);
        });

        // 构建初始状态
        PlanExecuteState initState = PlanExecuteState.fromGoal(
                "规划约会", location, budget, style, occasion, activity);

        // 执行 LangGraph
        CompiledGraph<PlanExecuteState> graph = planExecuteGraph.getCompiledGraph();
        var result = graph.invoke(initState);

        // 获取最终结果
        PlanExecuteState finalState = result.get();

        // 推送地图（取每个类别的第一个）
        // ... 推送 map, pdf, 总结

        // 推送最终总结
        String finalAnswer = finalState.getFinalAnswer();
        if (!finalAnswer.isEmpty()) {
            sleep(500);
            safeSend(emitter, Map.of("type", "text", "content", finalAnswer));
        }

        safeSend(emitter, Map.of("type", "done"));
        try { emitter.complete(); } catch (Exception ignored) {}

    } catch (Exception e) {
        log.error("PlanExecute 执行失败", e);
        safeSend(emitter, Map.of("type", "error", "message", "执行出错: " + e.getMessage()));
        try { emitter.complete(); } catch (Exception ignored) {}
    }
}
```

- [ ] **步骤 3：编译验证**

运行：`mvn compile -q`
预期：编译通过

---

### 任务 7：端到端测试

- [ ] **步骤 1：重启服务**

```bash
taskkill //F //IM java.exe
cd yu-ai-agent && mvn clean package -DskipTests
java -jar target/ai-chat-1.0.0-SNAPSHOT.jar
```

- [ ] **步骤 2：测试 Plan-and-Execute 流程**

打开 http://localhost:3000 → 新对话 → 发送约会请求

预期流程：
1. AI 回复一句话 + 弹窗收集信息
2. 填写提交 → 看到执行计划（步骤列表）
3. 每步有 ⏳ → ✅ 状态变化
4. 搜索结果实时展示（POI 卡片）
5. 详情（评分+照片）
6. 最终总结在底部

- [ ] **步骤 3：Commit**

```bash
git add -A
git commit -m "feat: Plan-and-Execute Agent（LangGraph4j planner/executor/replanner）"
```

---

## 验证清单

- [ ] `mvn compile` 通过
- [ ] 约会请求 → 弹窗收集信息
- [ ] 填写提交 → 看到计划步骤
- [ ] 每步逐步执行，有状态变化
- [ ] 搜索结果在执行面板中展示
- [ ] 详情（评分+照片）展示
- [ ] 最终总结在最后
- [ ] 地图和 PDF 正常
- [ ] 消息存入数据库

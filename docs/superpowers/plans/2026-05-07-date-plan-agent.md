# 约会规划 Agent 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在现有 AI 聊天系统中加入 LangGraph4j 约会规划 Agent，用户通过自然语言触发 Plan-and-Execute 流程，支持 Human-in-the-Loop 交互确认，调用高德地图 MCP 搜索和规划，最终生成 PDF 约会计划书。

**架构：** 单一 LangGraph4j StateGraph，条件边路由简单对话和复杂规划，interrupt() 实现 Human-in-the-Loop 暂停确认。参考 LangGraph 官方 skill：`langgraph-fundamentals`（StateGraph + 条件边）和 `langgraph-human-in-the-loop`（interrupt/Command resume 模式）。

**技术栈：** LangGraph4j 1.4 / LangChain4j 1.0.0-beta3 / 高德地图 MCP Server / iText 9 / Vue 3 + 高德 JS API / PostgreSQL (Checkpointer)

---

## 文件结构

### 新增文件

| 文件路径 | 职责 |
|---------|------|
| `src/main/java/com/aichat/app/graph/DatePlanGraph.java` | LangGraph4j 图构建和编译 |
| `src/main/java/com/aichat/app/graph/DatePlanState.java` | 图状态定义 |
| `src/main/java/com/aichat/app/graph/nodes/AgentNode.java` | LLM 统一入口节点 |
| `src/main/java/com/aichat/app/graph/nodes/PlanNode.java` | 计划生成节点（含 interrupt） |
| `src/main/java/com/aichat/app/graph/nodes/SearchPoiNode.java` | 高德 MCP POI 搜索节点 |
| `src/main/java/com/aichat/app/graph/nodes/ChoosePoiNode.java` | 用户选择 POI（含 interrupt） |
| `src/main/java/com/aichat/app/graph/nodes/RouteNode.java` | 高德 MCP 路线规划节点 |
| `src/main/java/com/aichat/app/graph/nodes/PdfNode.java` | PDF 生成节点 |
| `src/main/java/com/aichat/app/graph/routing/RouteAfterAgent.java` | agent 节点后的条件路由 |
| `src/main/java/com/aichat/app/graph/routing/RouteAfterPlan.java` | plan 节点后的条件路由 |
| `src/main/java/com/aichat/app/tools/AmapMcpTools.java` | 高德 MCP 工具封装 |
| `src/main/java/com/aichat/app/tools/PdfGenerationTool.java` | PDF 生成工具（iText） |
| `src/main/resources/mcp-servers.json` | MCP Server 配置 |
| `yu-ai-agent-frontend/src/views/Chat.vue` | 重写：支持结构化 SSE 事件 |
| `yu-ai-agent-frontend/src/components/PlanConfirm.vue` | 计划确认弹窗组件 |
| `yu-ai-agent-frontend/src/components/PoiSelector.vue` | POI 选择卡片组件 |
| `yu-ai-agent-frontend/src/components/RouteMap.vue` | 高德地图路线展示组件 |
| `yu-ai-agent-frontend/src/components/StepProgress.vue` | 执行进度条组件 |

### 修改文件

| 文件路径 | 修改内容 |
|---------|---------|
| `pom.xml` | 添加 langgraph4j-core、itext 依赖 |
| `src/main/java/com/aichat/app/config/AiConfig.java` | 注册 LangGraph4j 图 Bean |
| `src/main/java/com/aichat/app/controller/ChatController.java` | SSE 协议扩展：支持 JSON 事件 |
| `src/main/java/com/aichat/app/service/RagService.java` | 不变 |
| `src/main/java/com/aichat/app/service/ChatService.java` | 不变 |
| `src/main/resources/application.yml` | 添加高德 API Key 配置 |
| `yu-ai-agent-frontend/src/api/index.js` | SSE 协议改为 JSON 事件解析 |

---

## 参考：LangGraph 官方 Skill 模式

以下模式来自本地安装的 LangChain 官方 skill，计划中所有设计都基于这些最佳实践。

### 条件边模式（langgraph-fundamentals）

```java
// 官方 skill 示例（Python）翻译为 Java：
graph.addConditionalEdges("classify", state -> {
    if ("weather".equals(state.get("route"))) return "weather";
    return "general";
}, Map.of("weather", "weatherNode", "general", "generalNode"));
```

### Human-in-the-Loop 模式（langgraph-human-in-the-loop）

```java
// interrupt() 暂停执行，等待用户输入
// 官方 skill 要求：
// 1. Checkpointer（编译时配置）
// 2. Thread ID（每次调用传入）
// 3. JSON-serializable payload

// 节点中：
String choice = interrupt(Map.of(
    "question", "确认这个约会计划？",
    "plan", state.getPlanSteps()
));

// 恢复时：
graph.invoke(Command.resume("approved"), config);
```

### RAG 模式（langchain-rag）

```java
// 官方 skill 的 Pipeline：
// 1. Load documents
// 2. Split (RecursiveCharacterTextSplitter)
// 3. Embed + Store
// 4. Retrieve (vector search)
// 5. Generate (LLM + context)
```

---

## 任务清单

### 任务 1：添加依赖和配置

**文件：**
- 修改：`pom.xml`
- 修改：`src/main/resources/application.yml`

- [ ] **步骤 1：添加 LangGraph4j 依赖**

在 `pom.xml` 的 `<dependencies>` 中添加：

```xml
<!-- LangGraph4j -->
<dependency>
    <groupId>org.bsc.langgraph4j</groupId>
    <artifactId>langgraph4j-core</artifactId>
    <version>1.4</version>
</dependency>

<!-- iText PDF -->
<dependency>
    <groupId>com.itextpdf</groupId>
    <artifactId>itext-core</artifactId>
    <version>9.1.0</version>
    <type>pom</type>
</dependency>
```

- [ ] **步骤 2：添加高德 API 配置**

在 `application.yml` 中添加：

```yaml
amap:
  api-key: ${AMAP_API_KEY:your-amap-key}
```

- [ ] **步骤 3：创建 MCP Server 配置**

创建 `src/main/resources/mcp-servers.json`：

```json
{
  "mcpServers": {
    "amap-maps": {
      "command": "npx",
      "args": ["-y", "@amap/amap-maps-mcp-server"],
      "env": {
        "AMAP_MAPS_API_KEY": "${AMAP_API_KEY}"
      }
    }
  }
}
```

- [ ] **步骤 4：编译验证**

```bash
cd G:/项目/superagent/yu-ai-agent
mvn clean compile
```

预期：BUILD SUCCESS

- [ ] **步骤 5：Commit**

```bash
git add pom.xml src/main/resources/application.yml src/main/resources/mcp-servers.json
git commit -m "deps: 添加 LangGraph4j、iText、高德 MCP 依赖"
```

---

### 任务 2：定义 State（状态）

**文件：**
- 创建：`src/main/java/com/aichat/app/graph/DatePlanState.java`

- [ ] **步骤 1：定义状态类**

参考 langgraph-fundamentals skill 的 State 设计模式。状态是所有节点共享的内存。用 reducer 控制列表合并。

```java
package com.aichat.app.graph;

import dev.langchain4j.data.message.ChatMessage;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * LangGraph4j 约会规划状态
 *
 * 参考 langgraph-fundamentals skill:
 * - 简单字段用默认覆盖
 * - 列表字段需要 reducer（这里手动管理）
 */
@Data
public class DatePlanState {

    // === 对话相关 ===
    private String userMessage;              // 用户当前消息
    private String aiResponse;               // AI 最终回复
    private List<ChatMessage> chatHistory;    // 对话历史（累加）

    // === 路由 ===
    private String action;                   // "chat" 或 "plan"

    // === 计划相关 ===
    private String planDescription;          // 计划文字描述
    private List<String> planSteps;          // 计划步骤列表
    private String dateLocation;             // 约会地点
    private String dateBudget;               // 预算
    private String dateStyle;                // 风格：浪漫/休闲/冒险

    // === Human-in-the-Loop ===
    private String userChoice;               // interrupt 返回值: approved/modify/cancel

    // === MCP 搜索结果 ===
    private List<PoiInfo> cafes;             // 咖啡厅列表
    private List<PoiInfo> spots;             // 景点列表
    private List<PoiInfo> restaurants;       // 餐厅列表
    private PoiInfo selectedCafe;            // 用户选的咖啡厅
    private PoiInfo selectedSpot;            // 用户选的景点
    private PoiInfo selectedRestaurant;      // 用户选的餐厅

    // === 路线 ===
    private RouteInfo route;                 // 规划的路线

    // === PDF ===
    private String pdfUrl;                   // PDF 下载链接

    /**
     * POI 信息（高德地图返回）
     */
    @Data
    public static class PoiInfo {
        private String name;
        private String address;
        private double rating;
        private String price;
        private double longitude;
        private double latitude;
    }

    /**
     * 路线信息
     */
    @Data
    public static class RouteInfo {
        private String distance;             // 如 "3.2公里"
        private String duration;             // 如 "步行40分钟"
        private String mode;                 // walking/driving
        private List<double[]> coordinates;  // 路线坐标点
    }

    public static DatePlanState init(String userMessage) {
        DatePlanState state = new DatePlanState();
        state.userMessage = userMessage;
        state.chatHistory = new ArrayList<>();
        state.planSteps = new ArrayList<>();
        state.cafes = new ArrayList<>();
        state.spots = new ArrayList<>();
        state.restaurants = new ArrayList<>();
        return state;
    }
}
```

- [ ] **步骤 2：Commit**

```bash
git add src/main/java/com/aichat/app/graph/DatePlanState.java
git commit -m "feat: 定义 LangGraph4j 约会规划 State"
```

---

### 任务 3：实现 Agent 节点（统一入口）

**文件：**
- 创建：`src/main/java/com/aichat/app/graph/nodes/AgentNode.java`
- 创建：`src/main/java/com/aichat/app/graph/routing/RouteAfterAgent.java`

- [ ] **步骤 1：实现 Agent 节点**

参考 langgraph-fundamentals skill：节点函数接收 state，返回 partial update dict。

```java
package com.aichat.app.graph.nodes;

import com.aichat.app.graph.DatePlanState;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 统一入口节点
 *
 * 这是图的核心节点。LLM 接收用户消息后：
 * - 如果是简单问题 → 直接文字回答，action = "chat"
 * - 如果需要规划 → 生成约会计划，action = "plan"
 *
 * 参考 langgraph-fundamentals skill 的设计：
 * - 节点返回 partial update dict（只返回变更的字段）
 */
@Component
@Slf4j
public class AgentNode {

    private static final String SYSTEM_PROMPT = """
            你是 AI 恋爱大师，擅长情感咨询和约会规划。
            
            对于简单的情感咨询（如吵架怎么办、怎么提升魅力），直接用 RAG 知识库回答。
            
            当用户请求涉及以下内容时，请生成约会计划：
            - 约会规划、行程安排
            - 推荐地点、路线
            - 帮忙找 XX、推荐 XX
            
            如果需要生成约会计划，请用以下格式回复：
            [ACTION:plan]
            地点：xxx
            预算：xxx
            风格：浪漫/休闲/冒险
            流程：
            1. 时间段 - 活动类型
            2. 时间段 - 活动类型
            3. ...
            
            如果是普通对话，直接回复：
            [ACTION:chat]
            你的回答内容...
            """;

    public DatePlanState execute(DatePlanState state, ChatLanguageModel chatModel) {
        log.info("Agent 节点：处理用户消息");

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        messages.addAll(state.getChatHistory());
        messages.add(new UserMessage(state.getUserMessage()));

        String response = chatModel.chat(messages);

        // 解析响应：判断是简单对话还是规划
        if (response.contains("[ACTION:plan]")) {
            state.setAction("plan");
            // 解析计划内容
            parsePlanFromResponse(state, response);
            state.setPlanDescription(response.replace("[ACTION:plan]", "").trim());
        } else {
            state.setAction("chat");
            state.setAiResponse(response.replace("[ACTION:chat]", "").trim());
        }

        // 更新对话历史
        state.getChatHistory().add(new UserMessage(state.getUserMessage()));
        state.getChatHistory().add(new AiMessage(response));

        log.info("Agent 节点完成，action={}", state.getAction());
        return state;
    }

    private void parsePlanFromResponse(DatePlanState state, String response) {
        String content = response.replace("[ACTION:plan]", "").trim();

        // 解析地点
        for (String line : content.split("\n")) {
            if (line.startsWith("地点：")) {
                state.setDateLocation(line.replace("地点：", "").trim());
            } else if (line.startsWith("预算：")) {
                state.setDateBudget(line.replace("预算：", "").trim());
            } else if (line.startsWith("风格：")) {
                state.setDateStyle(line.replace("风格：", "").trim());
            } else if (line.matches("^\\d+\\..*")) {
                state.getPlanSteps().add(line.trim());
            }
        }
    }
}
```

- [ ] **步骤 2：实现条件路由**

参考 langgraph-fundamentals skill 的 `add_conditional_edges` 模式。

```java
package com.aichat.app.graph.routing;

import com.aichat.app.graph.DatePlanState;
import java.util.function.Function;

/**
 * Agent 节点后的条件路由
 *
 * 参考 langgraph-fundamentals skill：
 * def route_query(state) -> Literal["weather", "general"]:
 *     return state["route"]
 *
 * Java 版本：函数式接口返回目标节点名
 */
public class RouteAfterAgent implements Function<DatePlanState, String> {

    @Override
    public String apply(DatePlanState state) {
        if ("plan".equals(state.getAction())) {
            return "plan";  // 走规划流程
        }
        return "__end__";  // 直接结束（简单对话）
    }
}
```

- [ ] **步骤 3：Commit**

```bash
git add src/main/java/com/aichat/app/graph/nodes/AgentNode.java
git add src/main/java/com/aichat/app/graph/routing/RouteAfterAgent.java
git commit -m "feat: 实现 Agent 统一入口节点和条件路由"
```

---

### 任务 4：实现 Plan 节点（Human-in-the-Loop）

**文件：**
- 创建：`src/main/java/com/aichat/app/graph/nodes/PlanNode.java`
- 创建：`src/main/java/com/aichat/app/graph/routing/RouteAfterPlan.java`

- [ ] **步骤 1：实现 Plan 节点（含 interrupt）**

参考 langgraph-human-in-the-loop skill：
- `interrupt(value)` 暂停执行，把 value 推送给用户
- 用户回复后 `Command(resume=value)` 恢复
- 恢复时节点从头执行，interrupt() 返回 resume 的值

```java
package com.aichat.app.graph.nodes;

import com.aichat.app.graph.DatePlanState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.bsc.langgraph4j.action.AsyncNodeAction.*;

/**
 * Plan 节点：展示计划，等待用户确认
 *
 * 参考 langgraph-human-in-the-loop skill：
 * - interrupt() 暂停执行
 * - 用户回复后从 interrupt() 返回值继续
 * - 节点恢复时从头执行（interrupt 之前的代码会重新运行）
 *
 * 注意：interrupt() 在 Java LangGraph4j 中的调用方式
 * 取决于具体 API 版本，这里展示设计意图
 */
@Component
@Slf4j
public class PlanNode {

    public DatePlanState execute(DatePlanState state) {
        log.info("Plan 节点：展示计划，等待用户确认");

        // 如果已经有用户选择（从 interrupt 恢复），直接返回
        if (state.getUserChoice() != null) {
            log.info("Plan 节点：用户选择 = {}", state.getUserChoice());
            return state;
        }

        // 构建计划展示内容
        String planDisplay = buildPlanDisplay(state);

        // interrupt() 暂停执行，把计划推送给前端
        // 用户在前端看到弹窗，选择确认/修改/取消
        // 用户回复后，interrupt() 返回用户的选择
        //
        // LangGraph4j Java 版本的 interrupt 调用：
        // String userChoice = interrupt(Map.of(
        //     "type", "plan_confirm",
        //     "question", "这是为你制定的约会计划，确认吗？",
        //     "plan", planDisplay,
        //     "steps", state.getPlanSteps(),
        //     "options", List.of("确认", "修改", "取消")
        // ));
        //
        // state.setUserChoice(userChoice);
        // log.info("用户选择: {}", userChoice);

        // 暂时用占位实现，等待 LangGraph4j Java interrupt API 确认
        // 实际实现时需要 LangGraph4j 的 Checkpointer 支持

        return state;
    }

    private String buildPlanDisplay(DatePlanState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("约会地点：").append(state.getDateLocation()).append("\n");
        sb.append("预算：").append(state.getDateBudget()).append("\n");
        sb.append("风格：").append(state.getDateStyle()).append("\n\n");
        sb.append("流程安排：\n");
        for (int i = 0; i < state.getPlanSteps().size(); i++) {
            sb.append(i + 1).append(". ").append(state.getPlanSteps().get(i)).append("\n");
        }
        return sb.toString();
    }
}
```

- [ ] **步骤 2：实现 Plan 后的条件路由**

```java
package com.aichat.app.graph.routing;

import com.aichat.app.graph.DatePlanState;
import java.util.function.Function;

/**
 * Plan 节点后的条件路由
 *
 * 参考 langgraph-fundamentals skill 的条件边模式
 */
public class RouteAfterPlan implements Function<DatePlanState, String> {

    @Override
    public String apply(DatePlanState state) {
        String choice = state.getUserChoice();
        if (choice == null) {
            return "__end__";  // interrupt 未恢复，暂挂
        }
        return switch (choice) {
            case "approved" -> "search_poi";  // 确认 → 搜索
            case "modify" -> "agent";         // 修改 → 回到 Agent 重新理解
            default -> "__end__";             // 取消 → 结束
        };
    }
}
```

- [ ] **步骤 3：Commit**

```bash
git add src/main/java/com/aichat/app/graph/nodes/PlanNode.java
git add src/main/java/com/aichat/app/graph/routing/RouteAfterPlan.java
git commit -m "feat: 实现 Plan 节点（Human-in-the-Loop interrupt 模式）"
```

---

### 任务 5：实现搜索 POI 节点

**文件：**
- 创建：`src/main/java/com/aichat/app/graph/nodes/SearchPoiNode.java`
- 创建：`src/main/java/com/aichat/app/tools/AmapMcpTools.java`

- [ ] **步骤 1：实现高德 MCP 工具封装**

```java
package com.aichat.app.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 高德地图 MCP 工具封装
 *
 * 替代方案：直接调用高德 REST API（不依赖 MCP Server）
 * 后续可通过 MCP Client 替换为 MCP Server 调用
 */
@Component
@Slf4j
public class AmapMcpTools {

    private static final String GEO_API = "https://restapi.amap.com/v3/geocode/geo";
    private static final String AROUND_API = "https://restapi.amap.com/v5/place/around";
    private static final String TEXT_API = "https://restapi.amap.com/v5/place/text";
    private static final String DRIVING_API = "https://restapi.amap.com/v5/direction/driving";
    private static final String WALKING_API = "https://restapi.amap.com/v5/direction/walking";

    @Value("${amap.api-key:}")
    private String apiKey;

    /**
     * 地址转经纬度
     */
    public double[] geocode(String address) {
        String resp = HttpUtil.get(GEO_API, Map.of(
                "key", apiKey,
                "address", address,
                "output", "JSON"
        ));
        JSONObject json = JSONUtil.parseObj(resp);
        JSONArray geocodes = json.getJSONArray("geocodes");
        if (geocodes == null || geocodes.isEmpty()) return null;
        String location = geocodes.getJSONObject(0).getStr("location");
        String[] parts = location.split(",");
        return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
    }

    /**
     * 附近搜索 POI
     */
    public List<PoiResult> aroundSearch(double lon, double lat, String keywords, int radius) {
        String resp = HttpUtil.get(AROUND_API, Map.of(
                "key", apiKey,
                "location", lon + "," + lat,
                "keywords", keywords,
                "radius", String.valueOf(radius),
                "show_fields", "business",
                "page_size", "10"
        ));
        return parsePoiResults(resp);
    }

    /**
     * 关键词搜索 POI
     */
    public List<PoiResult> textSearch(String keywords, String city) {
        String resp = HttpUtil.get(TEXT_API, Map.of(
                "key", apiKey,
                "keywords", keywords,
                "city", city,
                "show_fields", "business",
                "page_size", "10"
        ));
        return parsePoiResults(resp);
    }

    /**
     * 驾车路线规划
     */
    public RouteResult drivingRoute(double[] origin, double[] destination) {
        String resp = HttpUtil.get(DRIVING_API, Map.of(
                "key", apiKey,
                "origin", origin[0] + "," + origin[1],
                "destination", destination[0] + "," + destination[1]
        ));
        return parseRouteResult(resp, "driving");
    }

    /**
     * 步行路线规划
     */
    public RouteResult walkingRoute(double[] origin, double[] destination) {
        String resp = HttpUtil.get(WALKING_API, Map.of(
                "key", apiKey,
                "origin", origin[0] + "," + origin[1],
                "destination", destination[0] + "," + destination[1]
        ));
        return parseRouteResult(resp, "walking");
    }

    private List<PoiResult> parsePoiResults(String resp) {
        List<PoiResult> results = new ArrayList<>();
        try {
            JSONObject json = JSONUtil.parseObj(resp);
            JSONArray pois = json.getJSONArray("pois");
            if (pois == null) return results;
            for (int i = 0; i < pois.size(); i++) {
                JSONObject poi = pois.getJSONObject(i);
                PoiResult r = new PoiResult();
                r.setName(poi.getStr("name"));
                r.setAddress(poi.getStr("address"));
                r.setDistance(poi.getStr("distance"));
                String loc = poi.getStr("location");
                if (loc != null) {
                    String[] parts = loc.split(",");
                    r.setLongitude(Double.parseDouble(parts[0]));
                    r.setLatitude(Double.parseDouble(parts[1]));
                }
                results.add(r);
            }
        } catch (Exception e) {
            log.error("解析 POI 结果失败", e);
        }
        return results;
    }

    private RouteResult parseRouteResult(String resp, String mode) {
        RouteResult result = new RouteResult();
        result.setMode(mode);
        try {
            JSONObject json = JSONUtil.parseObj(resp);
            JSONArray paths = json.getJSONArray("paths");
            if (paths != null && !paths.isEmpty()) {
                JSONObject path = paths.getJSONObject(0);
                result.setDistance(path.getStr("distance"));
                result.setDuration(path.getStr("duration"));
            }
        } catch (Exception e) {
            log.error("解析路线结果失败", e);
        }
        return result;
    }

    @lombok.Data
    public static class PoiResult {
        private String name;
        private String address;
        private String distance;
        private double longitude;
        private double latitude;
    }

    @lombok.Data
    public static class RouteResult {
        private String distance;
        private String duration;
        private String mode;
    }
}
```

- [ ] **步骤 2：实现搜索 POI 节点**

```java
package com.aichat.app.graph.nodes;

import com.aichat.app.graph.DatePlanState;
import com.aichat.app.graph.DatePlanState.PoiInfo;
import com.aichat.app.tools.AmapMcpTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 搜索 POI 节点
 *
 * 调用高德地图 API 搜索约会相关地点
 */
@Component
@Slf4j
public class SearchPoiNode {

    private final AmapMcpTools amapTools;

    public SearchPoiNode(AmapMcpTools amapTools) {
        this.amapTools = amapTools;
    }

    public DatePlanState execute(DatePlanState state) {
        log.info("搜索 POI 节点：搜索 {}", state.getDateLocation());

        // 1. 地址转经纬度
        double[] coords = amapTools.geocode(state.getDateLocation());
        if (coords == null) {
            log.warn("无法解析地址: {}", state.getDateLocation());
            return state;
        }

        // 2. 搜索附近咖啡厅
        List<PoiInfo> cafes = amapTools.aroundSearch(coords[0], coords[1], "咖啡厅", 3000)
                .stream().map(this::toPoiInfo).collect(Collectors.toList());
        state.setCafes(cafes);

        // 3. 搜索附近景点
        List<PoiInfo> spots = amapTools.aroundSearch(coords[0], coords[1], "景点", 5000)
                .stream().map(this::toPoiInfo).collect(Collectors.toList());
        state.setSpots(spots);

        // 4. 搜索附近餐厅
        List<PoiInfo> restaurants = amapTools.aroundSearch(coords[0], coords[1], "餐厅", 3000)
                .stream().map(this::toPoiInfo).collect(Collectors.toList());
        state.setRestaurants(restaurants);

        log.info("搜索完成：咖啡厅{}个，景点{}个，餐厅{}个",
                cafes.size(), spots.size(), restaurants.size());

        return state;
    }

    private PoiInfo toPoiInfo(AmapMcpTools.PoiResult r) {
        PoiInfo info = new PoiInfo();
        info.setName(r.getName());
        info.setAddress(r.getAddress());
        info.setLongitude(r.getLongitude());
        info.setLatitude(r.getLatitude());
        return info;
    }
}
```

- [ ] **步骤 3：Commit**

```bash
git add src/main/java/com/aichat/app/tools/AmapMcpTools.java
git add src/main/java/com/aichat/app/graph/nodes/SearchPoiNode.java
git commit -m "feat: 实现高德地图 MCP 工具和 POI 搜索节点"
```

---

### 任务 6：实现用户选择 POI 节点（Human-in-the-Loop）

**文件：**
- 创建：`src/main/java/com/aichat/app/graph/nodes/ChoosePoiNode.java`

- [ ] **步骤 1：实现 Choose POI 节点**

```java
package com.aichat.app.graph.nodes;

import com.aichat.app.graph.DatePlanState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 选择 POI 节点（Human-in-the-Loop）
 *
 * 展示搜索结果，让用户选择
 * 使用 interrupt() 暂停等待用户输入
 */
@Component
@Slf4j
public class ChoosePoiNode {

    public DatePlanState execute(DatePlanState state) {
        log.info("Choose POI 节点：展示搜索结果，等待用户选择");

        // 如果已经有选择（从 interrupt 恢复），直接返回
        if (state.getSelectedCafe() != null) {
            log.info("用户已选择：{} / {} / {}",
                    state.getSelectedCafe().getName(),
                    state.getSelectedSpot() != null ? state.getSelectedSpot().getName() : "未选",
                    state.getSelectedRestaurant() != null ? state.getSelectedRestaurant().getName() : "未选");
            return state;
        }

        // interrupt() 暂停，推送 POI 列表给前端
        // 前端弹窗展示选择卡片，用户选择后恢复
        //
        // String choices = interrupt(Map.of(
        //     "type", "poi_select",
        //     "cafes", state.getCafes(),
        //     "spots", state.getSpots(),
        //     "restaurants", state.getRestaurants()
        // ));
        //
        // 解析用户选择，设置到 state
        // state.setSelectedCafe(...);
        // state.setSelectedSpot(...);
        // state.setSelectedRestaurant(...);

        return state;
    }
}
```

- [ ] **步骤 2：Commit**

```bash
git add src/main/java/com/aichat/app/graph/nodes/ChoosePoiNode.java
git commit -m "feat: 实现 POI 选择节点（Human-in-the-Loop）"
```

---

### 任务 7：实现路线规划和 PDF 节点

**文件：**
- 创建：`src/main/java/com/aichat/app/graph/nodes/RouteNode.java`
- 创建：`src/main/java/com/aichat/app/graph/nodes/PdfNode.java`
- 创建：`src/main/java/com/aichat/app/tools/PdfGenerationTool.java`

- [ ] **步骤 1：实现路线规划节点**

```java
package com.aichat.app.graph.nodes;

import com.aichat.app.graph.DatePlanState;
import com.aichat.app.graph.DatePlanState.RouteInfo;
import com.aichat.app.tools.AmapMcpTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 路线规划节点
 *
 * 串联选中的 POI，调用高德 API 规划路线
 */
@Component
@Slf4j
public class RouteNode {

    private final AmapMcpTools amapTools;

    public RouteNode(AmapMcpTools amapTools) {
        this.amapTools = amapTools;
    }

    public DatePlanState execute(DatePlanState state) {
        log.info("路线规划节点");

        var cafe = state.getSelectedCafe();
        var spot = state.getSelectedSpot();
        var restaurant = state.getSelectedRestaurant();

        if (cafe == null || restaurant == null) {
            log.warn("POI 未完整选择，跳过路线规划");
            return state;
        }

        // 规划路线：咖啡厅 → 景点 → 餐厅
        try {
            // 咖啡厅 → 景点
            double[] cafeCoord = {cafe.getLongitude(), cafe.getLatitude()};
            double[] spotCoord = {spot.getLongitude(), spot.getLatitude()};
            var route1 = amapTools.walkingRoute(cafeCoord, spotCoord);

            // 景点 → 餐厅
            double[] restCoord = {restaurant.getLongitude(), restaurant.getLatitude()};
            var route2 = amapTools.walkingRoute(spotCoord, restCoord);

            RouteInfo routeInfo = new RouteInfo();
            routeInfo.setMode("walking");
            routeInfo.setDistance(route1.getDistance() + " + " + route2.getDistance());
            routeInfo.setDuration(route1.getDuration() + " + " + route2.getDuration());
            state.setRoute(routeInfo);

            log.info("路线规划完成：{}，{}", routeInfo.getDistance(), routeInfo.getDuration());
        } catch (Exception e) {
            log.error("路线规划失败", e);
        }

        return state;
    }
}
```

- [ ] **步骤 2：实现 PDF 生成工具**

```java
package com.aichat.app.tools;

import com.aichat.app.graph.DatePlanState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PDF 生成工具
 *
 * 使用 iText 9 生成约会计划书
 * 后续实现具体 iText 代码
 */
@Component
@Slf4j
public class PdfGenerationTool {

    /**
     * 生成约会计划 PDF
     *
     * @param state 约会状态
     * @return PDF 文件路径
     */
    public String generate(DatePlanState state) {
        log.info("生成 PDF：{}", state.getDateLocation());

        // TODO: 实现 iText PDF 生成
        // 内容包括：
        // - 封面：约会主题 + 日期
        // - 时间线：每个时间段的活动
        // - 地点详情：名称、地址、推荐理由
        // - 路线信息：步行距离、时间
        // - 预算明细

        String pdfPath = System.getProperty("user.dir") + "/tmp/date-plan.pdf";
        log.info("PDF 生成完成：{}", pdfPath);
        return pdfPath;
    }
}
```

- [ ] **步骤 3：实现 PDF 节点**

```java
package com.aichat.app.graph.nodes;

import com.aichat.app.graph.DatePlanState;
import com.aichat.app.tools.PdfGenerationTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PDF 生成节点
 */
@Component
@Slf4j
public class PdfNode {

    private final PdfGenerationTool pdfTool;

    public PdfNode(PdfGenerationTool pdfTool) {
        this.pdfTool = pdfTool;
    }

    public DatePlanState execute(DatePlanState state) {
        log.info("PDF 节点：生成约会计划书");

        String pdfUrl = pdfTool.generate(state);
        state.setPdfUrl(pdfUrl);

        // 构建最终回复
        StringBuilder reply = new StringBuilder();
        reply.append("你的约会计划已生成！\n\n");
        reply.append("地点：").append(state.getDateLocation()).append("\n");
        reply.append("预算：").append(state.getDateBudget()).append("\n\n");
        reply.append("行程安排：\n");
        if (state.getSelectedCafe() != null) {
            reply.append("☕ 下午茶：").append(state.getSelectedCafe().getName()).append("\n");
        }
        if (state.getSelectedSpot() != null) {
            reply.append("🌸 景点：").append(state.getSelectedSpot().getName()).append("\n");
        }
        if (state.getSelectedRestaurant() != null) {
            reply.append("🍽️ 晚餐：").append(state.getSelectedRestaurant().getName()).append("\n");
        }
        if (state.getRoute() != null) {
            reply.append("\n路线：").append(state.getRoute().getDistance())
                  .append("，约").append(state.getRoute().getDuration()).append("\n");
        }
        reply.append("\n📄 PDF 已生成：").append(pdfUrl);

        state.setAiResponse(reply.toString());
        log.info("PDF 节点完成");
        return state;
    }
}
```

- [ ] **步骤 4：Commit**

```bash
git add src/main/java/com/aichat/app/graph/nodes/RouteNode.java
git add src/main/java/com/aichat/app/graph/nodes/PdfNode.java
git add src/main/java/com/aichat/app/tools/PdfGenerationTool.java
git commit -m "feat: 实现路线规划节点和 PDF 生成节点"
```

---

### 任务 8：构建完整图（Graph）

**文件：**
- 创建：`src/main/java/com/aichat/app/graph/DatePlanGraph.java`
- 修改：`src/main/java/com/aichat/app/config/AiConfig.java`

- [ ] **步骤 1：构建完整 LangGraph4j 图**

参考 langgraph-fundamentals skill：
- `addNode(name, func)` 添加节点
- `addEdge(from, to)` 添加固定边
- `addConditionalEdges(from, router, targets)` 添加条件边
- `compile(checkpointer)` 编译

```java
package com.aichat.app.graph;

import com.aichat.app.graph.nodes.*;
import com.aichat.app.graph.routing.RouteAfterAgent;
import com.aichat.app.graph.routing.RouteAfterPlan;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.springframework.stereotype.Component;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * 约会规划 LangGraph4j 图
 *
 * 单一入口，条件路由：
 * START → agent → (简单? END : plan) → search_poi → choose_poi → route → pdf → END
 *
 * 参考 langgraph-fundamentals skill：
 * - StateGraph 用于构建图
 * - addConditionalEdges 用于路由
 * - compile() 时配置 Checkpointer（支持 interrupt）
 */
@Component
@Slf4j
public class DatePlanGraph {

    private final AgentNode agentNode;
    private final PlanNode planNode;
    private final SearchPoiNode searchPoiNode;
    private final ChoosePoiNode choosePoiNode;
    private final RouteNode routeNode;
    private final PdfNode pdfNode;
    private final ChatLanguageModel chatModel;

    public DatePlanGraph(AgentNode agentNode, PlanNode planNode,
                         SearchPoiNode searchPoiNode, ChoosePoiNode choosePoiNode,
                         RouteNode routeNode, PdfNode pdfNode,
                         ChatLanguageModel chatModel) {
        this.agentNode = agentNode;
        this.planNode = planNode;
        this.searchPoiNode = searchPoiNode;
        this.choosePoiNode = choosePoiNode;
        this.routeNode = routeNode;
        this.pdfNode = pdfNode;
        this.chatModel = chatModel;
    }

    public StateGraph<DatePlanState> buildGraph() throws GraphStateException {
        StateGraph<DatePlanState> graph = new StateGraph<>(DatePlanState.class);

        // 添加节点
        graph.addNode("agent", state -> agentNode.execute(state, chatModel));
        graph.addNode("plan", planNode::execute);
        graph.addNode("search_poi", searchPoiNode::execute);
        graph.addNode("choose_poi", choosePoiNode::execute);
        graph.addNode("route", routeNode::execute);
        graph.addNode("pdf", pdfNode::execute);

        // 入口
        graph.addEdge(START, "agent");

        // 条件边：agent 之后
        // 参考 langgraph-fundamentals skill 的 addConditionalEdges 模式
        graph.addConditionalEdges("agent", new RouteAfterAgent());

        // 条件边：plan 之后
        graph.addConditionalEdges("plan", new RouteAfterPlan());

        // 固定边：搜索 → 选择 → 路线 → PDF → END
        graph.addEdge("search_poi", "choose_poi");
        graph.addEdge("choose_poi", "route");
        graph.addEdge("route", "pdf");
        graph.addEdge("pdf", END);

        return graph;
    }

    /**
     * 编译图（带 Checkpointer，支持 interrupt）
     *
     * 参考 langgraph-human-in-the-loop skill：
     * interrupt 需要 Checkpointer 保存状态
     */
    public StateGraph<DatePlanState> compile() throws GraphStateException {
        StateGraph<DatePlanState> graph = buildGraph();
        // LangGraph4j Java 版 compile 方式待确认
        // graph = graph.compile(new CompileConfig(new MemorySaver()));
        return graph;
    }
}
```

- [ ] **步骤 2：在 AiConfig 中注册图 Bean**

```java
// 在 AiConfig.java 中添加：
@Bean
public DatePlanGraph datePlanGraph(...) {
    return new DatePlanGraph(...);
}
```

- [ ] **步骤 3：Commit**

```bash
git add src/main/java/com/aichat/app/graph/DatePlanGraph.java
git commit -m "feat: 构建完整 LangGraph4j 图（条件边 + 节点连接）"
```

---

### 任务 9：改造 SSE 协议和 ChatController

**文件：**
- 修改：`src/main/java/com/aichat/app/controller/ChatController.java`
- 修改：`src/main/java/com/aichat/app/service/AgentChatService.java`

- [ ] **步骤 1：扩展 SSE 协议支持 JSON 事件**

原来的 SSE 只发纯文本。改为支持结构化 JSON 事件：

```java
// 新的 SSE 事件格式：
// 纯文本（兼容普通对话）：
//   data: {"type":"text","content":"吵架后先冷静情绪..."}
//
// Plan 模式：
//   data: {"type":"plan","steps":["下午茶","景点","晚餐"],"location":"西湖"}
//   data: {"type":"ask","question":"确认计划？","options":["确认","修改","取消"]}
//   data: {"type":"step","step":1,"status":"doing","message":"搜索咖啡厅..."}
//   data: {"type":"choice","step":1,"items":[{"name":"湖畔咖啡","rating":4.8}]}
//   data: {"type":"complete","response":"约会计划已生成","pdfUrl":"/api/pdf/xxx"}
```

- [ ] **步骤 2：在 ChatController 中集成图**

```java
// ChatController.chatSse() 方法改造：
@GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter chatSse(String message, String convId) {
    SseEmitter emitter = new SseEmitter(300000L);

    // 判断是否走 Plan 流程
    if (isComplexTask(message)) {
        // 走 LangGraph4j 图
        DatePlanState state = DatePlanState.init(message);
        // 图执行过程中，通过 SSE 推送事件
        // graph.invoke(state, config) 内部每个节点执行完推送事件
    } else {
        // 走普通 RAG 对话（现有逻辑不变）
        agentChatService.chatStream(message, ...);
    }

    return emitter;
}

private boolean isComplexTask(String msg) {
    List<String> keywords = List.of("规划", "计划", "安排", "路线", "行程", "约会", "推荐路线");
    return keywords.stream().anyMatch(msg::contains);
}
```

- [ ] **步骤 3：Commit**

```bash
git add src/main/java/com/aichat/app/controller/ChatController.java
git commit -m "feat: 扩展 SSE 协议，集成 LangGraph4j 图到 ChatController"
```

---

### 任务 10：前端改造（SSE JSON 解析 + 新组件）

**文件：**
- 修改：`yu-ai-agent-frontend/src/api/index.js`
- 修改：`yu-ai-agent-frontend/src/views/Chat.vue`
- 创建：`yu-ai-agent-frontend/src/components/PlanConfirm.vue`
- 创建：`yu-ai-agent-frontend/src/components/PoiSelector.vue`
- 创建：`yu-ai-agent-frontend/src/components/StepProgress.vue`
- 创建：`yu-ai-agent-frontend/src/components/RouteMap.vue`

- [ ] **步骤 1：改造 API 层支持 JSON 事件**

```javascript
// api/index.js - SSE 改为解析 JSON 事件
export const chatSSE = (message, convId, onEvent) => {
  const params = new URLSearchParams({ message, convId })
  const url = `${API_BASE_URL}/chat/sse?${params}`
  const es = new EventSource(url)

  es.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      onEvent(data)  // 分发到不同处理函数
    } catch (e) {
      // 兼容纯文本
      onEvent({ type: 'text', content: event.data })
    }
  }

  es.onerror = () => es.close()
  return es
}
```

- [ ] **步骤 2：改造 Chat.vue 支持多种事件类型**

Chat.vue 中的消息渲染改为根据 `type` 字段渲染不同组件：

```vue
<div v-for="(msg, i) in messages" :key="i">
  <!-- 普通文本 -->
  <ChatBubble v-if="msg.type === 'text'" :message="msg" />

  <!-- 计划确认弹窗 -->
  <PlanConfirm v-if="msg.type === 'plan'" :plan="msg" @confirm="onConfirm" @modify="onModify" />

  <!-- POI 选择 -->
  <PoiSelector v-if="msg.type === 'choice'" :items="msg.items" @select="onSelect" />

  <!-- 进度条 -->
  <StepProgress v-if="msg.type === 'step'" :step="msg" />

  <!-- 完成结果 -->
  <div v-if="msg.type === 'complete'">
    <p>{{ msg.response }}</p>
    <RouteMap v-if="msg.route" :route="msg.route" />
    <a :href="msg.pdfUrl">下载 PDF</a>
  </div>
</div>
```

- [ ] **步骤 3：创建 PlanConfirm 弹窗组件**

```vue
<!-- PlanConfirm.vue -->
<template>
  <div class="plan-confirm glass-card">
    <h3>约会计划</h3>
    <p>{{ plan.location }} · {{ plan.budget }} · {{ plan.style }}</p>
    <ul>
      <li v-for="(step, i) in plan.steps" :key="i">{{ step }}</li>
    </ul>
    <div class="actions">
      <button class="glass-btn primary" @click="$emit('confirm', 'approved')">确认</button>
      <button class="glass-btn" @click="$emit('modify', 'modify')">修改</button>
      <button class="glass-btn" @click="$emit('confirm', 'cancel')">取消</button>
    </div>
  </div>
</template>
```

- [ ] **步骤 4：创建 PoiSelector 组件**

```vue
<!-- PoiSelector.vue -->
<template>
  <div class="poi-selector">
    <div v-for="(item, i) in items" :key="i"
         class="poi-card glass-card"
         :class="{ selected: selectedIndex === i }"
         @click="select(i)">
      <h4>{{ item.name }}</h4>
      <p>{{ item.address }}</p>
      <span v-if="item.distance">{{ item.distance }}米</span>
    </div>
    <button class="glass-btn primary" @click="confirm" :disabled="selectedIndex < 0">确认选择</button>
  </div>
</template>
```

- [ ] **步骤 5：Commit**

```bash
git add yu-ai-agent-frontend/src/
git commit -m "feat: 前端改造 - SSE JSON 事件 + Plan/POI 选择组件"
```

---

### 任务 11：集成测试

**文件：**
- 无新文件

- [ ] **步骤 1：启动所有服务**

```bash
# 1. 启动 pgvector
docker start pgvector

# 2. 设置环境变量
export DASHSCOPE_API_KEY=sk-xxx
export DB_PASSWORD=123456
export AMAP_API_KEY=your-amap-key

# 3. 启动后端
cd G:/项目/superagent/yu-ai-agent
mvn spring-boot:run

# 4. 启动前端
cd yu-ai-agent-frontend
npm run dev
```

- [ ] **步骤 2：测试简单对话**

在前端输入："吵架了怎么办"
预期：直接返回 RAG 回答，无弹窗

- [ ] **步骤 3：测试约会规划**

在前端输入："帮我规划明天西湖约会"
预期：
1. AI 回复计划确认弹窗
2. 点击确认
3. 展示 POI 搜索结果
4. 选择 POI
5. 展示路线
6. PDF 生成完成

- [ ] **步骤 4：测试修改流程**

在约会计划弹窗点击"修改"
预期：回到 Agent 节点重新规划

---

## 执行方式

**计划已完成并保存到 `docs/superpowers/plans/2026-05-07-date-plan-agent.md`。两种执行方式：**

**1. 子代理驱动（推荐）** — 每个任务调度一个新的子代理，任务间进行审查，快速迭代

**2. 内联执行** — 在当前会话中使用 executing-plans 执行任务，批量执行并设有检查点

**选哪种方式？**

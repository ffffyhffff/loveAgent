# 约会攻略逐步展示实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development 或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 约会规划执行过程中，每个步骤的结果实时以丰富内容（POI卡片、评价、推荐文案）展示在对话中，而不是最后才一次性展示。

**架构：** SearchPoiNode 工具调用循环中，每个工具调用完成后立即推送 SSE 事件（step 进度 + pois 评价卡片 + 文字）。前端在对话流中实时渲染这些内容。最终再生成 PDF。

**技术栈：** LangChain4j Tool Calling / SSE / Vue 3

---

## SSE 事件类型（新增）

| type | 数据 | 渲染效果 |
|------|------|----------|
| `step` | `{message, status: "active"/"done"}` | "⏳ 正在搜索..." → "✅ 搜索完成" |
| `section` | `{title, icon, items}` | 一段分类结果（如"☕ 推荐花店"），下面跟 POI 卡片 |
| `review` | `{placeName, rating, comment, source}` | 评价气泡 |
| `plan_text` | `{content}` | AI 攻略文案 |
| `text` | `{content}` | 普通文字（已有） |
| `pois` | `{categories}` | POI 列表（已有，用于选择修改） |
| `map` | `{pois, routeInfo}` | 地图（已有） |
| `pdf` | `{url}` | PDF 下载（已有） |

---

## 文件清单

| 文件 | 职责 | 操作 |
|------|------|------|
| `SearchPoiNode.java` | 工具调用循环中推送内容事件 | 修改 |
| `LoveAgentService.java` | executeDatePlan 调用 SearchPoiNode 的新方法 | 修改 |
| `DatePlanService.java` | 新增带回调的执行方法 | 修改 |
| `Chat.vue` | 处理 section/review/step 事件 | 修改 |
| `PoiCard.vue` (新) | POI 卡片组件（名称+地址+评分+评价） | 创建 |

---

### 任务 1：SearchPoiNode — 推送内容事件

**文件：**
- 修改：`src/main/java/com/aichat/app/graph/nodes/SearchPoiNode.java`

- [ ] **步骤 1：新增 applyWithCallback 方法**

```java
public Map<String, Object> applyWithCallback(AgentState agentState,
        Consumer<Map<String, Object>> onEvent) throws Exception {
    // onEvent 用于推送 SSE 事件到前端
    // 内部逻辑与 apply() 相同，但每个工具调用后都推送事件
}
```

- [ ] **步骤 2：工具调用后推送 section 事件**

在工具调用循环中，aroundSearch 完成后立即推送结果：

```java
// 执行工具
String result = executeTool(toolName, args);

// 如果是搜索类工具，推送内容到前端
if ("aroundSearch".equals(toolName) || "searchPoi".equals(toolName)) {
    String keyword = jsonArgs.getStr("keyword", "搜索结果");
    onEvent.accept(Map.of(
        "type", "section",
        "title", keyword,
        "items", parsePoiItems(result)  // 解析 JSON 为 POI 列表
    ));
}

// 如果是评价搜索，推送评价
if ("searchReviews".equals(toolName)) {
    String placeName = jsonArgs.getStr("placeName", "");
    onEvent.accept(Map.of(
        "type", "review",
        "placeName", placeName,
        "content", result
    ));
}
```

- [ ] **步骤 3：工具调用前后推送 step 事件**

```java
// 调用前
onEvent.accept(Map.of("type", "step", "message", "正在搜索 " + keyword + "...", "status", "active"));

// 调用后
onEvent.accept(Map.of("type", "step", "message", "搜索 " + keyword + " 完成", "status", "done"));
```

- [ ] **步骤 4：添加 parsePoiItems 辅助方法**

```java
private List<Map<String, Object>> parsePoiItems(String jsonStr) {
    List<Map<String, Object>> items = new ArrayList<>();
    try {
        JSONArray arr = JSONUtil.parseArray(jsonStr);
        for (int i = 0; i < arr.size(); i++) {
            JSONObject poi = arr.getJSONObject(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", poi.getStr("name"));
            m.put("address", poi.getStr("address"));
            m.put("distance", poi.getStr("distance"));
            m.put("longitude", poi.getDouble("longitude"));
            m.put("latitude", poi.getDouble("latitude"));
            items.add(m);
        }
    } catch (Exception ignored) {}
    return items;
}
```

- [ ] **步骤 5：编译验证**

运行：`mvn compile -q`
预期：编译通过

---

### 任务 2：LoveAgentService / DatePlanService — 传递回调

**文件：**
- 修改：`src/main/java/com/aichat/app/service/LoveAgentService.java`
- 修改：`src/main/java/com/aichat/app/service/DatePlanService.java`

- [ ] **步骤 1：DatePlanService 新增带回调的执行方法**

```java
public ExecuteResult executeWithCallback(String location, String budget, String style,
        String occasion, String activity,
        Consumer<Map<String, Object>> onEvent) {
    // 构造 state...
    // 调用 searchPoiNode.applyWithCallback(state, onEvent)
    // 其余同 executeApproved
}
```

- [ ] **步骤 2：LoveAgentService.executeDatePlan 使用新方法**

```java
// 替换原来调用 datePlanService.executeApproved()
// 为调用 datePlanService.executeCallback()
// onEvent 回调同时推送到 SSE emitter

DatePlanService.ExecuteResult result = datePlanService.executeWithCallback(
    location, budget, style, occasion, activity,
    event -> {
        safeSend(emitter, event);      // 推到前端
        if (onEvent != null) onEvent.accept(event);  // 存数据库
    }
);
```

- [ ] **步骤 3：编译验证**

运行：`mvn compile -q`
预期：编译通过

---

### 任务 3：前端 PoiCard 组件

**文件：**
- 创建：`yu-ai-agent-frontend/src/components/PoiCard.vue`

- [ ] **步骤 1：创建 POI 卡片组件**

```vue
<template>
  <div class="poi-card">
    <div class="poi-card-header">
      <span class="poi-name">{{ poi.name }}</span>
      <span v-if="poi.distance" class="poi-dist">{{ poi.distance }}m</span>
    </div>
    <div v-if="poi.address" class="poi-addr">📍 {{ poi.address }}</div>
    <div v-if="poi.rating" class="poi-rating">⭐ {{ poi.rating }}分</div>
    <div v-if="poi.comment" class="poi-comment">{{ poi.comment }}</div>
  </div>
</template>

<script setup>
defineProps({ poi: { type: Object, required: true } })
</script>

<style scoped>
.poi-card {
  padding: 12px 14px; margin: 6px 0; border-radius: 12px;
  background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.1);
  transition: all 0.2s;
}
.poi-card:hover { background: rgba(255,255,255,0.1); }
.poi-card-header { display: flex; justify-content: space-between; align-items: center; }
.poi-name { font-size: 0.85rem; font-weight: 600; }
.poi-dist { font-size: 0.72rem; color: var(--color-text-dim); }
.poi-addr { font-size: 0.75rem; color: var(--color-text-secondary); margin-top: 4px; }
.poi-rating { font-size: 0.75rem; color: #f59e0b; margin-top: 4px; }
.poi-comment { font-size: 0.72rem; color: var(--color-text-secondary); margin-top: 6px; font-style: italic; padding: 6px 8px; background: rgba(255,255,255,0.04); border-radius: 8px; }
</style>
```

---

### 任务 4：Chat.vue — 处理新事件类型

**文件：**
- 修改：`yu-ai-agent-frontend/src/views/Chat.vue`

- [ ] **步骤 1：导入 PoiCard 组件**

```javascript
import PoiCard from '../components/PoiCard.vue'
```

- [ ] **步骤 2：在 createLoveEventHandler 中添加 section 事件处理**

```javascript
if (parsed.type === 'section' && parsed.items) {
  // 添加分类标题 + POI 卡片列表
  messages.value.push({
    content: '',
    isUser: false,
    type: 'section',
    title: parsed.title,
    icon: getCatIcon(parsed.title),
    items: parsed.items,
  })
}
```

- [ ] **步骤 3：在 createLoveEventHandler 中添加 review 事件处理**

```javascript
if (parsed.type === 'review') {
  messages.value.push({
    content: '',
    isUser: false,
    type: 'review',
    placeName: parsed.placeName,
    text: parsed.content,
  })
}
```

- [ ] **步骤 4：在 createLoveEventHandler 中添加 plan_text 事件处理**

```javascript
if (parsed.type === 'plan_text') {
  messages.value.push({
    content: parsed.content,
    isUser: false,
    type: 'text',  // 当普通文字展示
  })
}
```

- [ ] **步骤 5：在模板中添加 section 类型渲染**

在 POI 列表模板附近添加：

```vue
<!-- 分类结果展示 -->
<template v-else-if="msg.type === 'section'">
  <div class="avatar ai-avatar">...</div>
  <div class="section-card glass-card">
    <div class="section-title">{{ msg.icon }} {{ msg.title }}</div>
    <PoiCard v-for="(poi, j) in msg.items" :key="j" :poi="poi" />
  </div>
</template>
```

- [ ] **步骤 6：在模板中添加 review 类型渲染**

```vue
<!-- 评价气泡 -->
<template v-else-if="msg.type === 'review'">
  <div class="avatar ai-avatar">...</div>
  <div class="review-bubble">
    <div class="review-place">💬 {{ msg.placeName }} 的评价</div>
    <div class="review-text">{{ msg.text }}</div>
  </div>
</template>
```

- [ ] **步骤 7：添加 CSS**

```css
.section-card { padding: 14px; margin: 8px 0; }
.section-title { font-size: 0.9rem; font-weight: 700; margin-bottom: 8px;
  background: linear-gradient(135deg, #8b5cf6, #ec4899);
  -webkit-background-clip: text; -webkit-text-fill-color: transparent; }

.review-bubble { padding: 12px 16px; margin: 6px 0; border-radius: 14px;
  background: rgba(139,92,246,0.08); border: 1px solid rgba(139,92,246,0.15); }
.review-place { font-size: 0.8rem; font-weight: 600; margin-bottom: 6px; }
.review-text { font-size: 0.78rem; color: var(--color-text-secondary); white-space: pre-wrap; }
```

- [ ] **步骤 8：编译前端验证**

运行：`cd yu-ai-agent-frontend && npm run dev`
预期：前端正常启动

---

### 任务 5：端到端测试

- [ ] **步骤 1：重启服务**

```bash
taskkill //F //IM java.exe
cd yu-ai-agent && mvn package -DskipTests -q && java -jar target/ai-chat-1.0.0-SNAPSHOT.jar
```

- [ ] **步骤 2：测试完整流程**

打开 http://localhost:3000 → 开新对话 → 发送约会请求

预期流程：
1. AI 回复文字 + 弹窗收集信息
2. 提交后看到 "正在搜索..."
3. 花店卡片实时出现在对话中
4. 景点卡片实时出现在对话中
5. 餐厅卡片实时出现在对话中
6. 评价信息出现在对话中
7. AI 攻略总结文案
8. 地图 + PDF 下载

- [ ] **步骤 3：Commit**

```bash
git add -A
git commit -m "feat: 约会攻略逐步展示（POI卡片+评价+流程可视化）"
```

---

## 验证清单

- [ ] `mvn compile` 通过
- [ ] 前端 `npm run dev` 正常
- [ ] 约会请求 → 弹窗收集信息
- [ ] 提交后逐步展示搜索结果（POI 卡片）
- [ ] 评价信息显示在对话中
- [ ] AI 攻略文案显示
- [ ] 地图和 PDF 正常
- [ ] 消息存入数据库

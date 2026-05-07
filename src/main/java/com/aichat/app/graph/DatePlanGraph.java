package com.aichat.app.graph;

import com.aichat.app.graph.nodes.*;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.AgentStateFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * 约会规划 LangGraph4j 图
 *
 * 使用 AsyncNodeAction.node_async() 包装同步 NodeAction
 * 使用 AsyncEdgeAction.edge_async() 包装同步 EdgeAction
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

    public DatePlanGraph(AgentNode agentNode, PlanNode planNode,
                         SearchPoiNode searchPoiNode, ChoosePoiNode choosePoiNode,
                         RouteNode routeNode, PdfNode pdfNode) {
        this.agentNode = agentNode;
        this.planNode = planNode;
        this.searchPoiNode = searchPoiNode;
        this.choosePoiNode = choosePoiNode;
        this.routeNode = routeNode;
        this.pdfNode = pdfNode;
    }

    public StateGraph<DatePlanState> buildGraph() throws GraphStateException {
        AgentStateFactory<DatePlanState> factory = DatePlanState::create;
        StateGraph<DatePlanState> graph = new StateGraph<>(factory);

        // 添加节点（用 node_async 包装同步 NodeAction）
        graph.addNode("agent", AsyncNodeAction.node_async(agentNode));
        graph.addNode("plan", AsyncNodeAction.node_async(planNode));
        graph.addNode("search_poi", AsyncNodeAction.node_async(searchPoiNode));
        graph.addNode("choose_poi", AsyncNodeAction.node_async(choosePoiNode));
        graph.addNode("route", AsyncNodeAction.node_async(routeNode));
        graph.addNode("pdf", AsyncNodeAction.node_async(pdfNode));

        // 入口
        graph.addEdge(START, "agent");

        // 条件边：agent 之后（简单对话 or 约会规划）
        EdgeAction routeAfterAgent = state -> {
            DatePlanState s = (DatePlanState) state;
            String action = s.getAction();
            log.info("路由：action = {}", action);
            if ("plan".equals(action)) return "plan";
            return END;
        };
        graph.addConditionalEdges("agent", AsyncEdgeAction.edge_async(routeAfterAgent),
                Map.of("plan", "plan", END, END));

        // 条件边：plan 之后
        // 由于 interrupt 未实现，暂时自动执行后续步骤
        // 后续改为：有 userChoice 时按选择路由，无 userChoice 时默认继续
        EdgeAction routeAfterPlan = state -> {
            DatePlanState s = (DatePlanState) state;
            String choice = s.getUserChoice();
            log.info("路由：userChoice = {}", choice);
            if ("modify".equals(choice)) return "agent";
            if ("cancel".equals(choice)) return END;
            // approved 或 pending → 继续执行
            return "search_poi";
        };
        graph.addConditionalEdges("plan", AsyncEdgeAction.edge_async(routeAfterPlan),
                Map.of("search_poi", "search_poi", "agent", "agent", END, END));

        // 固定边
        graph.addEdge("search_poi", "choose_poi");
        graph.addEdge("choose_poi", "route");
        graph.addEdge("route", "pdf");
        graph.addEdge("pdf", END);

        return graph;
    }
}

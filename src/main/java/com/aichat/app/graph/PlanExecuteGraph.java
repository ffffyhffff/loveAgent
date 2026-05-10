package com.aichat.app.graph;

import com.aichat.app.graph.nodes.ExecutorNode;
import com.aichat.app.graph.nodes.PlannerNode;
import com.aichat.app.graph.nodes.ReplannerNode;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
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
 * START → planner → executor → replanner ─┬→ executor（继续）
 *                                          └→ END（完成）
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
            log.info("PlanExecute 图编译完成");
        }
        return compiledGraph;
    }

    private StateGraph<PlanExecuteState> buildGraph() throws GraphStateException {
        StateGraph<PlanExecuteState> graph = new StateGraph<>(PlanExecuteState::create);

        graph.addNode("planner", AsyncNodeAction.node_async(plannerNode));
        graph.addNode("executor", AsyncNodeAction.node_async(executorNode));
        graph.addNode("replanner", AsyncNodeAction.node_async(replannerNode));

        graph.addEdge(START, "planner");
        graph.addEdge("planner", "executor");
        graph.addEdge("executor", "replanner");

        graph.addConditionalEdges("replanner",
                AsyncEdgeAction.edge_async(state -> {
                    PlanExecuteState s = (PlanExecuteState) state;
                    String action = s.getAction();
                    log.info("路由: action={}", action);
                    if ("done".equals(action)) return END;
                    return "executor";
                }),
                Map.of("executor", "executor", END, END));

        return graph;
    }
}

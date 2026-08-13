package com.workflow.engine.service;

import com.workflow.engine.exception.CircularDependencyException;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DagValidationService {

    public record Edge(String source, String target) {}

    /**
     * Validates that the graph defined by nodeIds and edges is acyclic (DAG),
     * and returns the topological order for execution.
     *
     * @param nodeIds set of all node/task identifiers in the workflow graph
     * @param edges   directed execution dependencies where source -> target means source must run before target
     * @return List of nodeIds in valid topological execution order
     * @throws CircularDependencyException if a cycle is detected or invalid references exist
     */
    public List<String> validateAndSort(Set<String> nodeIds, List<Edge> edges) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return List.of();
        }

        // Build adjacency list (source -> list of targets) and calculate in-degrees (target -> count)
        Map<String, List<String>> adjList = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        for (String node : nodeIds) {
            adjList.put(node, new ArrayList<>());
            inDegree.put(node, 0);
        }

        if (edges != null) {
            for (Edge edge : edges) {
                if (!nodeIds.contains(edge.source())) {
                    throw new IllegalArgumentException("Edge source '" + edge.source() + "' does not exist in workflow nodes");
                }
                if (!nodeIds.contains(edge.target())) {
                    throw new IllegalArgumentException("Edge target '" + edge.target() + "' does not exist in workflow nodes");
                }

                adjList.get(edge.source()).add(edge.target());
                inDegree.put(edge.target(), inDegree.get(edge.target()) + 1);
            }
        }

        // Kahn's Algorithm
        Queue<String> queue = new ArrayDeque<>();
        for (String node : nodeIds) {
            if (inDegree.get(node) == 0) {
                queue.add(node);
            }
        }

        List<String> topologicalOrder = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            topologicalOrder.add(current);

            for (String neighbor : adjList.get(current)) {
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                if (inDegree.get(neighbor) == 0) {
                    queue.add(neighbor);
                }
            }
        }

        if (topologicalOrder.size() != nodeIds.size()) {
            // Cycle detected! Trace exact cycle path using DFS
            List<String> cyclePath = traceCyclePath(nodeIds, adjList);
            throw new CircularDependencyException(
                    "Workflow DAG validation failed: Circular dependency (cycle) detected.",
                    cyclePath
            );
        }

        return topologicalOrder;
    }

    /**
     * DFS helper to trace the exact sequence of nodes forming a cycle.
     */
    private List<String> traceCyclePath(Set<String> nodeIds, Map<String, List<String>> adjList) {
        Map<String, Integer> state = new HashMap<>(); // 0: UNVISITED, 1: VISITING, 2: VISITED
        for (String node : nodeIds) {
            state.put(node, 0);
        }

        List<String> currentPath = new ArrayList<>();
        for (String node : nodeIds) {
            if (state.get(node) == 0) {
                List<String> cycle = dfsTrace(node, adjList, state, currentPath);
                if (!cycle.isEmpty()) {
                    return cycle;
                }
            }
        }
        return List.of();
    }

    private List<String> dfsTrace(String u, Map<String, List<String>> adjList, Map<String, Integer> state, List<String> path) {
        state.put(u, 1);
        path.add(u);

        for (String v : adjList.getOrDefault(u, List.of())) {
            if (state.get(v) == 1) { // Cycle found!
                int startIndex = path.indexOf(v);
                List<String> cycle = new ArrayList<>(path.subList(startIndex, path.size()));
                cycle.add(v); // Complete the loop visualization
                return cycle;
            } else if (state.get(v) == 0) {
                List<String> cycle = dfsTrace(v, adjList, state, path);
                if (!cycle.isEmpty()) {
                    return cycle;
                }
            }
        }

        state.put(u, 2);
        path.remove(path.size() - 1);
        return List.of();
    }
}

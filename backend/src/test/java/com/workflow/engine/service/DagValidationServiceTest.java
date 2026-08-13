package com.workflow.engine.service;

import com.workflow.engine.exception.CircularDependencyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DagValidationServiceTest {

    private DagValidationService dagValidationService;

    @BeforeEach
    void setUp() {
        dagValidationService = new DagValidationService();
    }

    @Test
    @DisplayName("Linear DAG (A -> B -> C) should return valid topological order")
    void testLinearDag() {
        Set<String> nodes = Set.of("A", "B", "C");
        List<DagValidationService.Edge> edges = List.of(
                new DagValidationService.Edge("A", "B"),
                new DagValidationService.Edge("B", "C")
        );

        List<String> order = dagValidationService.validateAndSort(nodes, edges);

        assertEquals(3, order.size());
        assertEquals("A", order.get(0));
        assertEquals("B", order.get(1));
        assertEquals("C", order.get(2));
    }

    @Test
    @DisplayName("Diamond DAG (A -> B, A -> C, B -> D, C -> D) should respect dependencies")
    void testDiamondDag() {
        Set<String> nodes = Set.of("A", "B", "C", "D");
        List<DagValidationService.Edge> edges = List.of(
                new DagValidationService.Edge("A", "B"),
                new DagValidationService.Edge("A", "C"),
                new DagValidationService.Edge("B", "D"),
                new DagValidationService.Edge("C", "D")
        );

        List<String> order = dagValidationService.validateAndSort(nodes, edges);

        assertEquals(4, order.size());
        assertEquals("A", order.get(0), "Node A must be first as it has no dependencies");
        assertEquals("D", order.get(3), "Node D must be last as both B and C depend on A and precede D");
        assertTrue(order.indexOf("B") > order.indexOf("A"));
        assertTrue(order.indexOf("C") > order.indexOf("A"));
        assertTrue(order.indexOf("D") > order.indexOf("B"));
        assertTrue(order.indexOf("D") > order.indexOf("C"));
    }

    @Test
    @DisplayName("Disconnected components DAG should return all nodes in valid order")
    void testDisconnectedDag() {
        Set<String> nodes = Set.of("A", "B", "C", "D");
        List<DagValidationService.Edge> edges = List.of(
                new DagValidationService.Edge("A", "B"),
                new DagValidationService.Edge("C", "D")
        );

        List<String> order = dagValidationService.validateAndSort(nodes, edges);

        assertEquals(4, order.size());
        assertTrue(order.indexOf("B") > order.indexOf("A"));
        assertTrue(order.indexOf("D") > order.indexOf("C"));
    }

    @Test
    @DisplayName("Single node DAG should return the single node")
    void testSingleNodeDag() {
        Set<String> nodes = Set.of("A");
        List<String> order = dagValidationService.validateAndSort(nodes, List.of());

        assertEquals(List.of("A"), order);
    }

    @Test
    @DisplayName("Empty DAG should return empty list")
    void testEmptyDag() {
        List<String> order = dagValidationService.validateAndSort(Set.of(), List.of());
        assertTrue(order.isEmpty());
    }

    @Test
    @DisplayName("Self-loop (A -> A) should throw CircularDependencyException")
    void testSelfLoopCycle() {
        Set<String> nodes = Set.of("A");
        List<DagValidationService.Edge> edges = List.of(
                new DagValidationService.Edge("A", "A")
        );

        CircularDependencyException exception = assertThrows(
                CircularDependencyException.class,
                () -> dagValidationService.validateAndSort(nodes, edges)
        );

        assertTrue(exception.getMessage().contains("Circular dependency"));
        assertEquals(List.of("A", "A"), exception.getCyclePath());
    }

    @Test
    @DisplayName("Direct 2-node cycle (A -> B -> A) should throw CircularDependencyException")
    void testTwoNodeCycle() {
        Set<String> nodes = Set.of("A", "B");
        List<DagValidationService.Edge> edges = List.of(
                new DagValidationService.Edge("A", "B"),
                new DagValidationService.Edge("B", "A")
        );

        CircularDependencyException exception = assertThrows(
                CircularDependencyException.class,
                () -> dagValidationService.validateAndSort(nodes, edges)
        );

        assertTrue(exception.getMessage().contains("Circular dependency"));
        assertFalse(exception.getCyclePath().isEmpty());
    }

    @Test
    @DisplayName("Complex 4-node cycle (A -> B -> C -> D -> B) should trace exact cycle path")
    void testComplexCyclePath() {
        Set<String> nodes = Set.of("A", "B", "C", "D");
        List<DagValidationService.Edge> edges = List.of(
                new DagValidationService.Edge("A", "B"),
                new DagValidationService.Edge("B", "C"),
                new DagValidationService.Edge("C", "D"),
                new DagValidationService.Edge("D", "B")
        );

        CircularDependencyException exception = assertThrows(
                CircularDependencyException.class,
                () -> dagValidationService.validateAndSort(nodes, edges)
        );

        assertTrue(exception.getMessage().contains("Circular dependency"));
        List<String> cyclePath = exception.getCyclePath();
        assertFalse(cyclePath.isEmpty());
        assertEquals("B", cyclePath.get(0));
        assertEquals("B", cyclePath.get(cyclePath.size() - 1));
    }

    @Test
    @DisplayName("Edge referring to non-existent node should throw IllegalArgumentException")
    void testUnknownNodeInEdge() {
        Set<String> nodes = Set.of("A", "B");
        List<DagValidationService.Edge> edges = List.of(
                new DagValidationService.Edge("A", "Z")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> dagValidationService.validateAndSort(nodes, edges)
        );
    }
}

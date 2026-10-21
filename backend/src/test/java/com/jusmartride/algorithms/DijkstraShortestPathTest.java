package com.jusmartride.algorithms;

import com.jusmartride.graph.CampusGraph;
import com.jusmartride.graph.Edge;
import com.jusmartride.graph.Vertex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Dijkstra Shortest Path Algorithm Tests")
class DijkstraShortestPathTest {

    private CampusGraph graph;
    private DijkstraShortestPath dijkstra;

    @BeforeEach
    void setUp() {
        graph = new CampusGraph();

        // Create diamond network:
        // A -> B (100m) -> D (100m) => total 200m
        // A -> C (50m)  -> D (120m) => total 170m (optimal)
        // E (isolated node)
        Vertex a = new Vertex("A", "Node A", "নোড এ", 23.88, 90.26, "GATE", List.of("a"), true);
        Vertex b = new Vertex("B", "Node B", "নোড বি", 23.881, 90.261, "ACADEMIC", List.of("b"), false);
        Vertex c = new Vertex("C", "Node C", "নোড সি", 23.882, 90.262, "HALL", List.of("c"), false);
        Vertex d = new Vertex("D", "Node D", "নোড ডি", 23.883, 90.263, "FOOD", List.of("d"), true);
        Vertex e = new Vertex("E", "Isolated E", "বিচ্ছিন্ন ই", 23.884, 90.264, "FACILITY", List.of("e"), false);

        graph.addVertex(a);
        graph.addVertex(b);
        graph.addVertex(c);
        graph.addVertex(d);
        graph.addVertex(e);

        graph.addEdge(new Edge("e_ab", "A", "B", "Road AB", 100.0, 30L, true, true, "Paved", "VERIFIED"));
        graph.addEdge(new Edge("e_bd", "B", "D", "Road BD", 100.0, 30L, true, true, "Paved", "VERIFIED"));
        graph.addEdge(new Edge("e_ac", "A", "C", "Road AC", 50.0, 15L, true, true, "Paved", "VERIFIED"));
        graph.addEdge(new Edge("e_cd", "C", "D", "Road CD", 120.0, 36L, true, true, "Paved", "VERIFIED"));

        dijkstra = new DijkstraShortestPath(graph);
    }

    @Test
    @DisplayName("Optimal path selection via lower cost intermediate vertex")
    void testFindShortestPath_OptimalFound() {
        RouteResult result = dijkstra.findShortestPath("A", "D");

        assertTrue(result.isReachable(), "Path should be reachable");
        assertEquals(170.0, result.getTotalDistanceMeters(), 0.001, "Optimal distance is 50 + 120 = 170m");
        assertEquals(List.of("A", "C", "D"), result.getPathNodeIds());
        assertEquals(2, result.getHopCount());
        assertEquals(51L, result.getEstimatedTravelTimeSeconds());
    }

    @Test
    @DisplayName("Identical source and destination returns zero distance route")
    void testFindShortestPath_IdenticalSourceAndTarget() {
        RouteResult result = dijkstra.findShortestPath("A", "A");

        assertTrue(result.isReachable());
        assertEquals(0.0, result.getTotalDistanceMeters(), 0.001);
        assertEquals(0L, result.getEstimatedTravelTimeSeconds());
        assertEquals(List.of("A"), result.getPathNodeIds());
        assertEquals(0, result.getHopCount());
    }

    @Test
    @DisplayName("Unreachable destination returns unreachable RouteResult")
    void testFindShortestPath_UnreachableDestination() {
        RouteResult result = dijkstra.findShortestPath("A", "E");

        assertFalse(result.isReachable(), "Isolated node E should be unreachable");
        assertTrue(Double.isInfinite(result.getTotalDistanceMeters()));
        assertTrue(result.getPathNodeIds().isEmpty());
    }

    @Test
    @DisplayName("Non-existent node handles gracefully")
    void testFindShortestPath_NonExistentNode() {
        RouteResult result = dijkstra.findShortestPath("A", "NON_EXISTENT");

        assertFalse(result.isReachable());
        assertTrue(result.getNotes().contains("does not exist"));
    }

    @Test
    @DisplayName("Equal cost paths resolve stably")
    void testFindShortestPath_EqualCostPaths() {
        // Add symmetrical path A -> B -> D (both 200m)
        CampusGraph eqGraph = new CampusGraph();
        Vertex a = new Vertex("A", "Node A", "এ", 23.88, 90.26, "GATE", null, true);
        Vertex b1 = new Vertex("B1", "Node B1", "বি১", 23.881, 90.261, "LOC", null, false);
        Vertex b2 = new Vertex("B2", "Node B2", "বি২", 23.882, 90.262, "LOC", null, false);
        Vertex d = new Vertex("D", "Node D", "ডি", 23.883, 90.263, "LOC", null, true);

        eqGraph.addVertex(a);
        eqGraph.addVertex(b1);
        eqGraph.addVertex(b2);
        eqGraph.addVertex(d);

        eqGraph.addEdge(new Edge("e1", "A", "B1", "R1", 100.0, 30L, true, true, "Paved", "VERIFIED"));
        eqGraph.addEdge(new Edge("e2", "B1", "D", "R2", 100.0, 30L, true, true, "Paved", "VERIFIED"));
        eqGraph.addEdge(new Edge("e3", "A", "B2", "R3", 100.0, 30L, true, true, "Paved", "VERIFIED"));
        eqGraph.addEdge(new Edge("e4", "B2", "D", "R4", 100.0, 30L, true, true, "Paved", "VERIFIED"));

        DijkstraShortestPath eqDijkstra = new DijkstraShortestPath(eqGraph);
        RouteResult result = eqDijkstra.findShortestPath("A", "D");

        assertTrue(result.isReachable());
        assertEquals(200.0, result.getTotalDistanceMeters(), 0.001);
        assertEquals(3, result.getPathNodeIds().size());
        assertEquals("A", result.getPathNodeIds().get(0));
        assertEquals("D", result.getPathNodeIds().get(2));
    }
}

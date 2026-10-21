package com.jusmartride.algorithms;

import com.jusmartride.graph.CampusGraph;
import com.jusmartride.graph.Edge;
import com.jusmartride.graph.Vertex;

import java.util.*;

/**
 * Production-grade implementation of Dijkstra's Shortest Path Algorithm for the JU Campus Graph.
 *
 * Algorithmic Details:
 * - Employs a Min-PriorityQueue to greedily extract the unvisited vertex with smallest tentative distance.
 * - Relaxes outgoing rickshaw-accessible edges.
 * - Reconstructs both vertex sequence and edge sequence.
 *
 * Complexity Analysis:
 * - Time Complexity: O((V + E) log V), where V is the number of vertices and E is the number of edges.
 *   Each vertex is extracted from the PriorityQueue at most once (log V), and each edge relaxation
 *   inserts or updates an entry in the heap (log V).
 * - Space Complexity: O(V + E) to maintain distance map, predecessor vertices, predecessor edges,
 *   and priority queue entries.
 */
public class DijkstraShortestPath {

    private final CampusGraph graph;

    public DijkstraShortestPath(CampusGraph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("CampusGraph cannot be null");
        }
        this.graph = graph;
    }

    /**
     * Internal state representation for PriorityQueue.
     */
    private static class NodeDistance implements Comparable<NodeDistance> {
        final String vertexId;
        final double distance;

        NodeDistance(String vertexId, double distance) {
            this.vertexId = vertexId;
            this.distance = distance;
        }

        @Override
        public int compareTo(NodeDistance o) {
            int cmp = Double.compare(this.distance, o.distance);
            if (cmp != 0) return cmp;
            // Stable tie-breaking for equal-cost paths
            return this.vertexId.compareTo(o.vertexId);
        }
    }

    /**
     * Calculates the shortest path between sourceId and targetId on the campus graph.
     *
     * @param sourceId starting vertex ID
     * @param targetId destination vertex ID
     * @return structured RouteResult
     */
    public RouteResult findShortestPath(String sourceId, String targetId) {
        if (sourceId == null || targetId == null) {
            throw new IllegalArgumentException("Source and target IDs must not be null");
        }

        Vertex source = graph.getVertex(sourceId);
        Vertex target = graph.getVertex(targetId);

        if (source == null) {
            return RouteResult.unreachable(sourceId, targetId, "Source vertex does not exist: " + sourceId);
        }
        if (target == null) {
            return RouteResult.unreachable(sourceId, targetId, "Target vertex does not exist: " + targetId);
        }

        // Identical source and target
        if (sourceId.equals(targetId)) {
            return RouteResult.zeroDistance(source);
        }

        Map<String, Double> dist = new HashMap<>();
        Map<String, String> prevNode = new HashMap<>();
        Map<String, Edge> prevEdge = new HashMap<>();
        Set<String> settled = new HashSet<>();

        PriorityQueue<NodeDistance> pq = new PriorityQueue<>();

        dist.put(sourceId, 0.0);
        pq.add(new NodeDistance(sourceId, 0.0));

        while (!pq.isEmpty()) {
            NodeDistance current = pq.poll();
            String u = current.vertexId;

            if (settled.contains(u)) {
                continue;
            }
            settled.add(u);

            // Short-circuit if destination reached
            if (u.equals(targetId)) {
                break;
            }

            // Stale heap entry check
            if (current.distance > dist.getOrDefault(u, Double.POSITIVE_INFINITY)) {
                continue;
            }

            for (Edge edge : graph.getOutgoingEdges(u)) {
                if (!edge.isRickshawAccessible()) {
                    continue;
                }

                String v = edge.getTargetId();
                if (settled.contains(v)) {
                    continue;
                }

                double weight = edge.getDistanceMeters();
                if (weight < 0) {
                    throw new IllegalStateException("Dijkstra does not support negative edge weights: " + weight);
                }

                double newDist = dist.get(u) + weight;
                double oldDist = dist.getOrDefault(v, Double.POSITIVE_INFINITY);

                if (newDist < oldDist) {
                    dist.put(v, newDist);
                    prevNode.put(v, u);
                    prevEdge.put(v, edge);
                    pq.add(new NodeDistance(v, newDist));
                }
            }
        }

        if (!dist.containsKey(targetId) || dist.get(targetId).isInfinite()) {
            return RouteResult.unreachable(sourceId, targetId, "No reachable path between " + sourceId + " and " + targetId);
        }

        // Reconstruct path
        LinkedList<String> pathNodeIds = new LinkedList<>();
        LinkedList<Vertex> pathVertices = new LinkedList<>();
        LinkedList<Edge> pathEdges = new LinkedList<>();
        long totalTravelTimeSeconds = 0;

        String curr = targetId;
        while (curr != null) {
            pathNodeIds.addFirst(curr);
            pathVertices.addFirst(graph.getVertex(curr));
            Edge e = prevEdge.get(curr);
            if (e != null) {
                pathEdges.addFirst(e);
                totalTravelTimeSeconds += e.getEstimatedTimeSeconds();
            }
            curr = prevNode.get(curr);
        }

        double totalDistance = dist.get(targetId);
        int hopCount = pathEdges.size();

        return new RouteResult(
                sourceId,
                targetId,
                true,
                totalDistance,
                totalTravelTimeSeconds,
                pathNodeIds,
                pathVertices,
                pathEdges,
                hopCount,
                "DIJKSTRA",
                "Optimal shortest path found"
        );
    }
}

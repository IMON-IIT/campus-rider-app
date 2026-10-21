package com.jusmartride.graph;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe Weighted Graph representing the Jahangirnagar University campus road network.
 * Stores vertices (landmarks, intersections) and weighted edges (road segments).
 * Supports versioning and dynamic reloading without restarting the application.
 */
public class CampusGraph implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Map<String, Vertex> vertices = new ConcurrentHashMap<>();
    private final Map<String, List<Edge>> adjacencyList = new ConcurrentHashMap<>();
    private final Map<String, Edge> edgeMap = new ConcurrentHashMap<>();
    private final AtomicLong version = new AtomicLong(1L);

    public CampusGraph() {
    }

    public void addVertex(Vertex vertex) {
        if (vertex == null) {
            throw new IllegalArgumentException("Vertex cannot be null");
        }
        vertices.put(vertex.getId(), vertex);
        adjacencyList.computeIfAbsent(vertex.getId(), k -> new CopyOnWriteArrayList<>());
        version.incrementAndGet();
    }

    public void addEdge(Edge edge) {
        if (edge == null) {
            throw new IllegalArgumentException("Edge cannot be null");
        }
        if (!vertices.containsKey(edge.getSourceId())) {
            throw new IllegalArgumentException("Source vertex not found in graph: " + edge.getSourceId());
        }
        if (!vertices.containsKey(edge.getTargetId())) {
            throw new IllegalArgumentException("Target vertex not found in graph: " + edge.getTargetId());
        }

        edgeMap.put(edge.getId(), edge);
        adjacencyList.computeIfAbsent(edge.getSourceId(), k -> new CopyOnWriteArrayList<>()).add(edge);

        if (edge.isBidirectional()) {
            Edge rev = edge.reversed();
            edgeMap.put(rev.getId(), rev);
            adjacencyList.computeIfAbsent(edge.getTargetId(), k -> new CopyOnWriteArrayList<>()).add(rev);
        }
        version.incrementAndGet();
    }

    public Vertex getVertex(String vertexId) {
        return vertices.get(vertexId);
    }

    public boolean hasVertex(String vertexId) {
        return vertexId != null && vertices.containsKey(vertexId);
    }

    public Collection<Vertex> getAllVertices() {
        return Collections.unmodifiableCollection(vertices.values());
    }

    public List<Edge> getOutgoingEdges(String vertexId) {
        List<Edge> edges = adjacencyList.get(vertexId);
        if (edges == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(edges);
    }

    public Collection<Edge> getAllEdges() {
        return Collections.unmodifiableCollection(edgeMap.values());
    }

    public int getVertexCount() {
        return vertices.size();
    }

    public int getEdgeCount() {
        return edgeMap.size();
    }

    public long getVersion() {
        return version.get();
    }

    public void clear() {
        vertices.clear();
        adjacencyList.clear();
        edgeMap.clear();
        version.incrementAndGet();
    }

    /**
     * Checks if the graph is connected (treating rickshaw-accessible edges as bidirectional paths).
     */
    public boolean isConnected() {
        if (vertices.isEmpty()) return true;
        Set<String> visited = new HashSet<>();
        String start = vertices.keySet().iterator().next();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            String curr = queue.poll();
            for (Edge edge : getOutgoingEdges(curr)) {
                if (visited.add(edge.getTargetId())) {
                    queue.add(edge.getTargetId());
                }
            }
        }
        return visited.size() == vertices.size();
    }
}

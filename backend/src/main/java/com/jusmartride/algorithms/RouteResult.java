package com.jusmartride.algorithms;

import com.jusmartride.graph.Edge;
import com.jusmartride.graph.Vertex;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Structured result returned by routing algorithms (Dijkstra, A*).
 */
public class RouteResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String sourceId;
    private final String targetId;
    private final boolean reachable;
    private final double totalDistanceMeters;
    private final long estimatedTravelTimeSeconds;
    private final List<String> pathNodeIds;
    private final List<Vertex> pathVertices;
    private final List<Edge> pathEdges;
    private final int hopCount;
    private final String algorithmUsed;
    private final String notes;

    public RouteResult(String sourceId, String targetId, boolean reachable,
                       double totalDistanceMeters, long estimatedTravelTimeSeconds,
                       List<String> pathNodeIds, List<Vertex> pathVertices,
                       List<Edge> pathEdges, int hopCount, String algorithmUsed, String notes) {
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.reachable = reachable;
        this.totalDistanceMeters = totalDistanceMeters;
        this.estimatedTravelTimeSeconds = estimatedTravelTimeSeconds;
        this.pathNodeIds = pathNodeIds != null ? List.copyOf(pathNodeIds) : Collections.emptyList();
        this.pathVertices = pathVertices != null ? List.copyOf(pathVertices) : Collections.emptyList();
        this.pathEdges = pathEdges != null ? List.copyOf(pathEdges) : Collections.emptyList();
        this.hopCount = hopCount;
        this.algorithmUsed = algorithmUsed != null ? algorithmUsed : "DIJKSTRA";
        this.notes = notes != null ? notes : "";
    }

    public static RouteResult unreachable(String sourceId, String targetId, String reason) {
        return new RouteResult(sourceId, targetId, false, Double.POSITIVE_INFINITY, Long.MAX_VALUE,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), 0, "DIJKSTRA", reason);
    }

    public static RouteResult zeroDistance(Vertex source) {
        return new RouteResult(source.getId(), source.getId(), true, 0.0, 0L,
                List.of(source.getId()), List.of(source), Collections.emptyList(), 0, "DIJKSTRA",
                "Source and destination are identical");
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getTargetId() {
        return targetId;
    }

    public boolean isReachable() {
        return reachable;
    }

    public double getTotalDistanceMeters() {
        return totalDistanceMeters;
    }

    public long getEstimatedTravelTimeSeconds() {
        return estimatedTravelTimeSeconds;
    }

    public List<String> getPathNodeIds() {
        return pathNodeIds;
    }

    public List<Vertex> getPathVertices() {
        return pathVertices;
    }

    public List<Edge> getPathEdges() {
        return pathEdges;
    }

    public int getHopCount() {
        return hopCount;
    }

    public String getAlgorithmUsed() {
        return algorithmUsed;
    }

    public String getNotes() {
        return notes;
    }

    @Override
    public String toString() {
        return "RouteResult{" +
                "from='" + sourceId + '\'' +
                ", to='" + targetId + '\'' +
                ", reachable=" + reachable +
                ", distance=" + totalDistanceMeters + "m" +
                ", time=" + estimatedTravelTimeSeconds + "s" +
                ", hops=" + hopCount +
                '}';
    }
}

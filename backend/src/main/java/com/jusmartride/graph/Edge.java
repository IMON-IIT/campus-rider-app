package com.jusmartride.graph;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a directional or bidirectional road segment between two campus vertices.
 * Edge weight is distance in meters (and secondary weight is travel time in seconds).
 */
public class Edge implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String sourceId;
    private final String targetId;
    private final String roadName;
    private final double distanceMeters;
    private final long estimatedTimeSeconds;
    private final boolean bidirectional;
    private final boolean rickshawAccessible;
    private final String surface;
    private final String confidence;

    public Edge(String id, String sourceId, String targetId, String roadName,
                double distanceMeters, long estimatedTimeSeconds, boolean bidirectional,
                boolean rickshawAccessible, String surface, String confidence) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Edge id cannot be null or empty");
        }
        if (sourceId == null || targetId == null) {
            throw new IllegalArgumentException("Source and target IDs cannot be null");
        }
        if (sourceId.equals(targetId)) {
            throw new IllegalArgumentException("Self-loops are not permitted in road segments: " + sourceId);
        }
        if (distanceMeters <= 0) {
            throw new IllegalArgumentException("Distance must be strictly positive: " + distanceMeters);
        }
        this.id = id;
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.roadName = roadName != null ? roadName : "Campus Pathway";
        this.distanceMeters = distanceMeters;
        this.estimatedTimeSeconds = estimatedTimeSeconds > 0 ? estimatedTimeSeconds : Math.round(distanceMeters / 3.33);
        this.bidirectional = bidirectional;
        this.rickshawAccessible = rickshawAccessible;
        this.surface = surface != null ? surface : "Paved Asphalt";
        this.confidence = confidence != null ? confidence : "VERIFIED";
    }

    public String getId() {
        return id;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getRoadName() {
        return roadName;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public long getEstimatedTimeSeconds() {
        return estimatedTimeSeconds;
    }

    public boolean isBidirectional() {
        return bidirectional;
    }

    public boolean isRickshawAccessible() {
        return rickshawAccessible;
    }

    public String getSurface() {
        return surface;
    }

    public String getConfidence() {
        return confidence;
    }

    public Edge reversed() {
        return new Edge(id + "_rev", targetId, sourceId, roadName, distanceMeters,
                estimatedTimeSeconds, bidirectional, rickshawAccessible, surface, confidence);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Edge edge = (Edge) o;
        return Objects.equals(id, edge.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Edge{" +
                "id='" + id + '\'' +
                ", " + sourceId + " -> " + targetId +
                ", dist=" + distanceMeters + "m" +
                ", time=" + estimatedTimeSeconds + "s" +
                '}';
    }
}

package com.jusmartride.graph;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a physical or topological node on the Jahangirnagar University campus road network.
 * Fully decoupled from persistence layer and JPA entities.
 */
public class Vertex implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String name;
    private final String bengaliName;
    private final double latitude;
    private final double longitude;
    private final String category;
    private final List<String> aliases;
    private final boolean rickshawStand;

    public Vertex(String id, String name, String bengaliName, double latitude, double longitude,
                  String category, List<String> aliases, boolean rickshawStand) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Vertex id cannot be null or empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Vertex name cannot be null or empty");
        }
        this.id = id;
        this.name = name;
        this.bengaliName = bengaliName != null ? bengaliName : name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.category = category != null ? category : "LOCATION";
        this.aliases = aliases != null ? List.copyOf(aliases) : Collections.emptyList();
        this.rickshawStand = rickshawStand;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getBengaliName() {
        return bengaliName;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getCategory() {
        return category;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public boolean isRickshawStand() {
        return rickshawStand;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vertex vertex = (Vertex) o;
        return Objects.equals(id, vertex.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Vertex{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", lat=" + latitude +
                ", lon=" + longitude +
                '}';
    }
}

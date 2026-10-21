package com.jusmartride.campus;

import jakarta.persistence.*;

@Entity
@Table(name = "campus_locations")
public class CampusLocation {

    @Id
    @Column(length = 50)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "bengali_name", nullable = false)
    private String bengaliName;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "rickshaw_stand", nullable = false)
    private boolean rickshawStand;

    @Column(columnDefinition = "TEXT")
    private String description;

    public CampusLocation() {
    }

    public CampusLocation(String id, String name, String bengaliName, String category,
                          double latitude, double longitude, boolean rickshawStand, String description) {
        this.id = id;
        this.name = name;
        this.bengaliName = bengaliName;
        this.category = category;
        this.latitude = latitude;
        this.longitude = longitude;
        this.rickshawStand = rickshawStand;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBengaliName() {
        return bengaliName;
    }

    public void setBengaliName(String bengaliName) {
        this.bengaliName = bengaliName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public boolean isRickshawStand() {
        return rickshawStand;
    }

    public void setRickshawStand(boolean rickshawStand) {
        this.rickshawStand = rickshawStand;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}

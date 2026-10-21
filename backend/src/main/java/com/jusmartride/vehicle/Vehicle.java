package com.jusmartride.vehicle;

import com.jusmartride.campus.CampusLocation;
import com.jusmartride.user.User;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @Column(length = 36)
    private String id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "driver_id", unique = true, nullable = false)
    private User driver;

    @Column(name = "rickshaw_number", unique = true, nullable = false, length = 100)
    private String rickshawNumber;

    @Column(nullable = false)
    private int capacity = 3;

    @Column(name = "is_online", nullable = false)
    private boolean isOnline = false;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "current_location_id")
    private CampusLocation currentLocation;

    @Column(name = "current_latitude")
    private Double currentLatitude;

    @Column(name = "current_longitude")
    private Double currentLongitude;

    @Column(name = "last_location_update")
    private Instant lastLocationUpdate;

    @Column(name = "location_source", nullable = false, length = 50)
    private String locationSource = "UNKNOWN"; // LIVE_PHONE, LAST_KNOWN, MANUAL, UNKNOWN

    public Vehicle() {
    }

    public Vehicle(User driver, String rickshawNumber, int capacity) {
        this.id = UUID.randomUUID().toString();
        this.driver = driver;
        this.rickshawNumber = rickshawNumber;
        this.capacity = capacity > 0 ? capacity : 3;
        this.isOnline = false;
        this.locationSource = "UNKNOWN";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public User getDriver() {
        return driver;
    }

    public void setDriver(User driver) {
        this.driver = driver;
    }

    public String getRickshawNumber() {
        return rickshawNumber;
    }

    public void setRickshawNumber(String rickshawNumber) {
        this.rickshawNumber = rickshawNumber;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    @JsonProperty("isOnline")
    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        isOnline = online;
    }

    public CampusLocation getCurrentLocation() {
        return currentLocation;
    }

    public void setCurrentLocation(CampusLocation currentLocation) {
        this.currentLocation = currentLocation;
    }

    public Double getCurrentLatitude() {
        return currentLatitude;
    }

    public void setCurrentLatitude(Double currentLatitude) {
        this.currentLatitude = currentLatitude;
    }

    public Double getCurrentLongitude() {
        return currentLongitude;
    }

    public void setCurrentLongitude(Double currentLongitude) {
        this.currentLongitude = currentLongitude;
    }

    public Instant getLastLocationUpdate() {
        return lastLocationUpdate;
    }

    public void setLastLocationUpdate(Instant lastLocationUpdate) {
        this.lastLocationUpdate = lastLocationUpdate;
    }

    public String getLocationSource() {
        return locationSource;
    }

    public void setLocationSource(String locationSource) {
        this.locationSource = locationSource;
    }
}

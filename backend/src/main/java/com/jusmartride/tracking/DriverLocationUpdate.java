package com.jusmartride.tracking;

import jakarta.validation.constraints.NotNull;

public class DriverLocationUpdate {

    @NotNull(message = "Latitude is required")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    private Double longitude;

    private String nearestLocationId;

    private String locationSource = "LIVE_PHONE"; // LIVE_PHONE, MANUAL, UNKNOWN

    private long timestamp = System.currentTimeMillis();

    public DriverLocationUpdate() {
    }

    public DriverLocationUpdate(Double latitude, Double longitude, String nearestLocationId, String locationSource) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.nearestLocationId = nearestLocationId;
        this.locationSource = locationSource != null ? locationSource : "LIVE_PHONE";
        this.timestamp = System.currentTimeMillis();
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getNearestLocationId() {
        return nearestLocationId;
    }

    public void setNearestLocationId(String nearestLocationId) {
        this.nearestLocationId = nearestLocationId;
    }

    public String getLocationSource() {
        return locationSource;
    }

    public void setLocationSource(String locationSource) {
        this.locationSource = locationSource;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}

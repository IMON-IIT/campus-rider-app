package com.jusmartride.driver;

import java.io.Serializable;

/**
 * An online rickshaw near the passenger's GPS position, as shown on the passenger map.
 */
public class NearbyDriverDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private String driverId;
    private String vehicleId;
    private String driverName;
    private String driverPhone;
    private String rickshawNumber;
    private double latitude;
    private double longitude;
    private double distanceMeters;
    private long etaSeconds;
    private int capacity;
    private int occupiedSeats;
    private int availableSeats;
    private boolean onTrip;
    private String nearestLocationId;
    private String nearestLocationName;
    private String locationSource;
    private Long lastUpdateSecondsAgo;

    public NearbyDriverDto() {
    }

    public String getDriverId() {
        return driverId;
    }

    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    public String getDriverPhone() {
        return driverPhone;
    }

    public void setDriverPhone(String driverPhone) {
        this.driverPhone = driverPhone;
    }

    public String getRickshawNumber() {
        return rickshawNumber;
    }

    public void setRickshawNumber(String rickshawNumber) {
        this.rickshawNumber = rickshawNumber;
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

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(double distanceMeters) {
        this.distanceMeters = distanceMeters;
    }

    public long getEtaSeconds() {
        return etaSeconds;
    }

    public void setEtaSeconds(long etaSeconds) {
        this.etaSeconds = etaSeconds;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getOccupiedSeats() {
        return occupiedSeats;
    }

    public void setOccupiedSeats(int occupiedSeats) {
        this.occupiedSeats = occupiedSeats;
    }

    public int getAvailableSeats() {
        return availableSeats;
    }

    public void setAvailableSeats(int availableSeats) {
        this.availableSeats = availableSeats;
    }

    public boolean isOnTrip() {
        return onTrip;
    }

    public void setOnTrip(boolean onTrip) {
        this.onTrip = onTrip;
    }

    public String getNearestLocationId() {
        return nearestLocationId;
    }

    public void setNearestLocationId(String nearestLocationId) {
        this.nearestLocationId = nearestLocationId;
    }

    public String getNearestLocationName() {
        return nearestLocationName;
    }

    public void setNearestLocationName(String nearestLocationName) {
        this.nearestLocationName = nearestLocationName;
    }

    public String getLocationSource() {
        return locationSource;
    }

    public void setLocationSource(String locationSource) {
        this.locationSource = locationSource;
    }

    public Long getLastUpdateSecondsAgo() {
        return lastUpdateSecondsAgo;
    }

    public void setLastUpdateSecondsAgo(Long lastUpdateSecondsAgo) {
        this.lastUpdateSecondsAgo = lastUpdateSecondsAgo;
    }
}

package com.jusmartride.matching;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Immutable snapshot of an active trip or idle vehicle for route insertion evaluation.
 * Completely independent of database/JPA entities.
 */
public class DriverTripSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String tripId;
    private final String vehicleId;
    private final String driverId;
    private final String driverName;
    private final String rickshawNumber;
    private final int totalCapacity;
    private final String status; // "AVAILABLE", "ARRIVING", "IN_PROGRESS"
    private final String currentLocationId;
    private final long lastLocationEpochMs;
    private final List<TripStopSnapshot> scheduledStops;
    private final int currentlyOccupiedSeats;

    public DriverTripSnapshot(String tripId, String vehicleId, String driverId, String driverName,
                              String rickshawNumber, int totalCapacity, String status,
                              String currentLocationId, long lastLocationEpochMs,
                              List<TripStopSnapshot> scheduledStops, int currentlyOccupiedSeats) {
        this.tripId = tripId;
        this.vehicleId = vehicleId;
        this.driverId = driverId;
        this.driverName = driverName;
        this.rickshawNumber = rickshawNumber;
        this.totalCapacity = totalCapacity;
        this.status = status;
        this.currentLocationId = currentLocationId;
        this.lastLocationEpochMs = lastLocationEpochMs;
        this.scheduledStops = scheduledStops != null ? List.copyOf(scheduledStops) : Collections.emptyList();
        this.currentlyOccupiedSeats = currentlyOccupiedSeats;
    }

    public static DriverTripSnapshot idle(String vehicleId, String driverId, String driverName,
                                          String rickshawNumber, int totalCapacity, String currentLocationId,
                                          long lastLocationEpochMs) {
        return new DriverTripSnapshot(
                null, vehicleId, driverId, driverName, rickshawNumber,
                totalCapacity, "AVAILABLE", currentLocationId, lastLocationEpochMs,
                Collections.emptyList(), 0
        );
    }

    public String getTripId() {
        return tripId;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public String getDriverId() {
        return driverId;
    }

    public String getDriverName() {
        return driverName;
    }

    public String getRickshawNumber() {
        return rickshawNumber;
    }

    public int getTotalCapacity() {
        return totalCapacity;
    }

    public String getStatus() {
        return status;
    }

    public String getCurrentLocationId() {
        return currentLocationId;
    }

    public long getLastLocationEpochMs() {
        return lastLocationEpochMs;
    }

    public List<TripStopSnapshot> getScheduledStops() {
        return scheduledStops;
    }

    public int getCurrentlyOccupiedSeats() {
        return currentlyOccupiedSeats;
    }

    public int getAvailableSeats() {
        return Math.max(0, totalCapacity - currentlyOccupiedSeats);
    }
}

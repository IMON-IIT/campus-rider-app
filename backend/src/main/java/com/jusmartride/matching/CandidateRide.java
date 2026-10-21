package com.jusmartride.matching;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Evaluated ride candidate option for a passenger's requested trip.
 * Represents either an idle vehicle (direct ride) or an active trip with spare capacity (shared ride).
 */
public class CandidateRide implements Serializable, Comparable<CandidateRide> {
    private static final long serialVersionUID = 1L;

    private final String tripId;
    private final String vehicleId;
    private final String driverId;
    private final String driverName;
    private final String rickshawNumber;
    private final int totalCapacity;
    private final int occupiedSeats;
    private final int availableSeats;
    private final boolean isSharedRide;
    private final String driverCurrentLocationId;
    private final double pickupDistanceMeters;
    private final long estimatedPickupWaitSeconds;
    private final double directTripDistanceMeters;
    private final long directTripTimeSeconds;
    private final double totalRouteDistanceMeters;
    private final long totalRouteTimeSeconds;
    private final double detourDistanceMeters;
    private final double detourRatio;
    private final double compositeScore;
    private final boolean recommended;
    private final String recommendationReason;
    private final List<String> plannedStops;

    public CandidateRide(String tripId, String vehicleId, String driverId, String driverName,
                         String rickshawNumber, int totalCapacity, int occupiedSeats,
                         int availableSeats, boolean isSharedRide, String driverCurrentLocationId,
                         double pickupDistanceMeters, long estimatedPickupWaitSeconds,
                         double directTripDistanceMeters, long directTripTimeSeconds,
                         double totalRouteDistanceMeters, long totalRouteTimeSeconds,
                         double detourDistanceMeters, double detourRatio,
                         double compositeScore, boolean recommended,
                         String recommendationReason, List<String> plannedStops) {
        this.tripId = tripId;
        this.vehicleId = vehicleId;
        this.driverId = driverId;
        this.driverName = driverName;
        this.rickshawNumber = rickshawNumber;
        this.totalCapacity = totalCapacity;
        this.occupiedSeats = occupiedSeats;
        this.availableSeats = availableSeats;
        this.isSharedRide = isSharedRide;
        this.driverCurrentLocationId = driverCurrentLocationId;
        this.pickupDistanceMeters = pickupDistanceMeters;
        this.estimatedPickupWaitSeconds = estimatedPickupWaitSeconds;
        this.directTripDistanceMeters = directTripDistanceMeters;
        this.directTripTimeSeconds = directTripTimeSeconds;
        this.totalRouteDistanceMeters = totalRouteDistanceMeters;
        this.totalRouteTimeSeconds = totalRouteTimeSeconds;
        this.detourDistanceMeters = detourDistanceMeters;
        this.detourRatio = detourRatio;
        this.compositeScore = compositeScore;
        this.recommended = recommended;
        this.recommendationReason = recommendationReason;
        this.plannedStops = plannedStops != null ? List.copyOf(plannedStops) : Collections.emptyList();
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

    public int getOccupiedSeats() {
        return occupiedSeats;
    }

    public int getAvailableSeats() {
        return availableSeats;
    }

    public boolean isSharedRide() {
        return isSharedRide;
    }

    public String getDriverCurrentLocationId() {
        return driverCurrentLocationId;
    }

    public double getPickupDistanceMeters() {
        return pickupDistanceMeters;
    }

    public long getEstimatedPickupWaitSeconds() {
        return estimatedPickupWaitSeconds;
    }

    public double getDirectTripDistanceMeters() {
        return directTripDistanceMeters;
    }

    public long getDirectTripTimeSeconds() {
        return directTripTimeSeconds;
    }

    public double getTotalRouteDistanceMeters() {
        return totalRouteDistanceMeters;
    }

    public long getTotalRouteTimeSeconds() {
        return totalRouteTimeSeconds;
    }

    public double getDetourDistanceMeters() {
        return detourDistanceMeters;
    }

    public double getDetourRatio() {
        return detourRatio;
    }

    public double getCompositeScore() {
        return compositeScore;
    }

    public boolean isRecommended() {
        return recommended;
    }

    public String getRecommendationReason() {
        return recommendationReason;
    }

    public List<String> getPlannedStops() {
        return plannedStops;
    }

    @Override
    public int compareTo(CandidateRide o) {
        // Lower composite score indicates a better ride recommendation
        return Double.compare(this.compositeScore, o.compositeScore);
    }
}

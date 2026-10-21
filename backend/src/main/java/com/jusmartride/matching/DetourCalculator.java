package com.jusmartride.matching;

/**
 * Validates and calculates route detours when inserting new passenger stops into existing active trips.
 * Ensures existing passengers are not subjected to unreasonable route extensions or delays.
 */
public class DetourCalculator {

    private final double maxAllowedDetourRatio;
    private final double maxAllowedExtraDistanceMeters;
    private final long maxAllowedExtraTimeSeconds;

    public DetourCalculator() {
        this(0.45, 750.0, 240L); // 45% detour ratio, 750m max extra, 4 mins max wait
    }

    public DetourCalculator(double maxAllowedDetourRatio, double maxAllowedExtraDistanceMeters, long maxAllowedExtraTimeSeconds) {
        this.maxAllowedDetourRatio = maxAllowedDetourRatio;
        this.maxAllowedExtraDistanceMeters = maxAllowedExtraDistanceMeters;
        this.maxAllowedExtraTimeSeconds = maxAllowedExtraTimeSeconds;
    }

    /**
     * Determines whether an insertion detour is acceptable.
     *
     * @param originalRouteDistance distance of trip before insertion
     * @param newRouteDistance distance of trip after insertion
     * @param directDistance distance of the new passenger's direct A to B trip
     * @param extraTimeSeconds extra travel time incurred
     * @return true if detour satisfies university campus constraints
     */
    public boolean isDetourAcceptable(double originalRouteDistance, double newRouteDistance,
                                      double directDistance, long extraTimeSeconds) {
        double extraDistance = newRouteDistance - originalRouteDistance;
        if (extraDistance < 0) {
            extraDistance = 0; // Insertion is coincident or collinear
        }

        if (extraDistance > maxAllowedExtraDistanceMeters) {
            return false;
        }

        if (extraTimeSeconds > maxAllowedExtraTimeSeconds) {
            return false;
        }

        if (directDistance <= 0) {
            return true;
        }

        double detourRatio = extraDistance / directDistance;
        return detourRatio <= maxAllowedDetourRatio;
    }

    public double calculateDetourRatio(double originalRouteDistance, double newRouteDistance, double directDistance) {
        double extra = Math.max(0.0, newRouteDistance - originalRouteDistance);
        if (directDistance <= 0) return 0.0;
        return extra / directDistance;
    }

    public double getMaxAllowedDetourRatio() {
        return maxAllowedDetourRatio;
    }

    public double getMaxAllowedExtraDistanceMeters() {
        return maxAllowedExtraDistanceMeters;
    }

    public long getMaxAllowedExtraTimeSeconds() {
        return maxAllowedExtraTimeSeconds;
    }
}

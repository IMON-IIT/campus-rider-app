package com.jusmartride.matching;

import com.jusmartride.algorithms.DijkstraShortestPath;
import com.jusmartride.algorithms.RouteResult;
import com.jusmartride.graph.CampusGraph;

import java.util.*;

/**
 * Core Java algorithmic service for evaluating and matching ride-sharing requests with eligible trips.
 *
 * Capabilities:
 * 1. Route insertion evaluation: tests all valid insertion combinations of pickup and dropoff into active routes.
 * 2. Dynamic capacity validation: verifies passenger seat capacity along every road segment.
 * 3. Detour bounding: enforces maximum detour percentage and extra distance limits.
 * 4. Stale location detection: excludes drivers with outdated tracking updates.
 * 5. Multi-criteria ranking: utilizes Java PriorityQueue via CandidateScorer.
 */
public class RideMatchingService {

    private final CampusGraph graph;
    private final DijkstraShortestPath dijkstra;
    private final DetourCalculator detourCalculator;
    private final CandidateScorer candidateScorer;
    private final long maxLocationStaleEpochMs;

    public RideMatchingService(CampusGraph graph, DijkstraShortestPath dijkstra,
                               DetourCalculator detourCalculator, CandidateScorer candidateScorer) {
        this(graph, dijkstra, detourCalculator, candidateScorer, 5 * 60 * 1000L); // 5 min stale threshold
    }

    public RideMatchingService(CampusGraph graph, DijkstraShortestPath dijkstra,
                               DetourCalculator detourCalculator, CandidateScorer candidateScorer,
                               long maxLocationStaleEpochMs) {
        if (graph == null || dijkstra == null) {
            throw new IllegalArgumentException("Graph and Dijkstra algorithms cannot be null");
        }
        this.graph = graph;
        this.dijkstra = dijkstra;
        this.detourCalculator = detourCalculator != null ? detourCalculator : new DetourCalculator();
        this.candidateScorer = candidateScorer != null ? candidateScorer : new CandidateScorer();
        this.maxLocationStaleEpochMs = maxLocationStaleEpochMs;
    }

    /**
     * Finds and ranks all eligible ride options for a passenger traveling from pickup to destination.
     *
     * @param pickupId passenger pickup location ID
     * @param destinationId passenger destination location ID
     * @param passengerCount number of seats requested (typically 1 or 2)
     * @param availableTrips snapshot of candidate vehicles and active trips
     * @param currentTimeEpochMs current timestamp for staleness checking
     * @return ordered list of viable candidates, with the top candidate marked recommended
     */
    public List<CandidateRide> matchRides(String pickupId, String destinationId, int passengerCount,
                                          Collection<DriverTripSnapshot> availableTrips,
                                          long currentTimeEpochMs) {
        if (pickupId == null || destinationId == null) {
            throw new IllegalArgumentException("Pickup and destination cannot be null");
        }
        if (pickupId.equals(destinationId)) {
            throw new IllegalArgumentException("Pickup and destination cannot be identical");
        }
        if (passengerCount <= 0) {
            throw new IllegalArgumentException("Passenger count must be positive");
        }

        // Direct shortest route for baseline
        RouteResult directRoute = dijkstra.findShortestPath(pickupId, destinationId);
        if (!directRoute.isReachable()) {
            return Collections.emptyList();
        }

        List<CandidateRide> feasibleCandidates = new ArrayList<>();

        if (availableTrips != null) {
            for (DriverTripSnapshot trip : availableTrips) {
                // 1. Check stale location
                if (trip.getLastLocationEpochMs() > 0 &&
                        (currentTimeEpochMs - trip.getLastLocationEpochMs() > maxLocationStaleEpochMs)) {
                    continue; // Skip stale driver position
                }

                // 2. Check basic total capacity
                if (passengerCount > trip.getTotalCapacity()) {
                    continue;
                }

                if ("AVAILABLE".equalsIgnoreCase(trip.getStatus()) || trip.getScheduledStops().isEmpty()) {
                    // Idle driver -> Evaluate direct ride
                    CandidateRide directCandidate = evaluateIdleDriver(pickupId, destinationId, passengerCount, directRoute, trip);
                    if (directCandidate != null) {
                        feasibleCandidates.add(directCandidate);
                    }
                } else if ("IN_PROGRESS".equalsIgnoreCase(trip.getStatus()) || "ARRIVING".equalsIgnoreCase(trip.getStatus())) {
                    // Active trip -> Evaluate route insertion for carpooling / ride-share
                    CandidateRide sharedCandidate = evaluateActiveTripInsertion(pickupId, destinationId, passengerCount, directRoute, trip);
                    if (sharedCandidate != null) {
                        feasibleCandidates.add(sharedCandidate);
                    }
                }
            }
        }

        // Rank candidates using Min-Heap PriorityQueue
        return candidateScorer.rankCandidates(feasibleCandidates);
    }

    private CandidateRide evaluateIdleDriver(String pickupId, String destinationId, int passengerCount,
                                             RouteResult directRoute, DriverTripSnapshot trip) {
        int availableSeats = trip.getAvailableSeats();
        if (passengerCount > availableSeats) {
            return null;
        }

        String driverLoc = trip.getCurrentLocationId() != null ? trip.getCurrentLocationId() : pickupId;
        RouteResult toPickup = dijkstra.findShortestPath(driverLoc, pickupId);
        if (!toPickup.isReachable()) {
            return null;
        }

        double pickupDist = toPickup.getTotalDistanceMeters();
        long pickupWait = toPickup.getEstimatedTravelTimeSeconds();
        double totalDist = pickupDist + directRoute.getTotalDistanceMeters();
        long totalTime = pickupWait + directRoute.getEstimatedTravelTimeSeconds();

        double score = candidateScorer.calculateScore(pickupDist, pickupWait, 0.0, availableSeats - passengerCount, true);

        List<String> plannedStops = List.of(driverLoc, pickupId, destinationId);

        return new CandidateRide(
                trip.getTripId(),
                trip.getVehicleId(),
                trip.getDriverId(),
                trip.getDriverName(),
                trip.getRickshawNumber(),
                trip.getTotalCapacity(),
                trip.getCurrentlyOccupiedSeats(),
                availableSeats,
                false, // direct private ride
                driverLoc,
                pickupDist,
                pickupWait,
                directRoute.getTotalDistanceMeters(),
                directRoute.getEstimatedTravelTimeSeconds(),
                totalDist,
                totalTime,
                pickupDist,
                0.0,
                score,
                false,
                null,
                plannedStops
        );
    }

    private CandidateRide evaluateActiveTripInsertion(String pickupId, String destinationId, int passengerCount,
                                                      RouteResult directRoute, DriverTripSnapshot trip) {
        List<TripStopSnapshot> stops = trip.getScheduledStops();
        if (stops.isEmpty()) {
            return null;
        }

        // Calculate original route distance
        List<String> originalSequence = new ArrayList<>();
        if (trip.getCurrentLocationId() != null) {
            originalSequence.add(trip.getCurrentLocationId());
        }
        for (TripStopSnapshot s : stops) {
            originalSequence.add(s.getLocationId());
        }

        double originalRouteDistance = calculateSequenceDistance(originalSequence);
        if (Double.isInfinite(originalRouteDistance)) {
            return null;
        }

        // Try inserting pickup at index i and dropoff at index j (i <= j)
        // Note: index 0 is current location, stops follow at 1..N
        int n = originalSequence.size();
        double bestExtraDistance = Double.POSITIVE_INFINITY;
        List<String> bestPlannedStops = null;
        long bestExtraTime = 0;
        double bestDetourRatio = 0.0;
        int bestAvailableSeatsAtPickup = 0;

        for (int i = 1; i <= n; i++) {
            for (int j = i; j <= n; j++) {
                List<String> candidateSequence = new ArrayList<>(originalSequence);
                // Insert destination first if at same index, or insert carefully
                candidateSequence.add(j, destinationId);
                candidateSequence.add(i, pickupId);

                // Check capacity along all segments
                CapacityCheckResult capCheck = checkCapacityAlongSequence(
                        trip.getCurrentlyOccupiedSeats(),
                        trip.getTotalCapacity(),
                        stops,
                        i, j, passengerCount
                );

                if (!capCheck.valid) {
                    continue;
                }

                double candidateDist = calculateSequenceDistance(candidateSequence);
                if (Double.isInfinite(candidateDist)) {
                    continue;
                }

                double extraDist = candidateDist - originalRouteDistance;
                long extraTime = Math.round(extraDist / 3.33);

                if (!detourCalculator.isDetourAcceptable(originalRouteDistance, candidateDist,
                        directRoute.getTotalDistanceMeters(), extraTime)) {
                    continue;
                }

                if (extraDist < bestExtraDistance) {
                    bestExtraDistance = extraDist;
                    bestExtraTime = extraTime;
                    bestPlannedStops = candidateSequence;
                    bestDetourRatio = detourCalculator.calculateDetourRatio(originalRouteDistance, candidateDist, directRoute.getTotalDistanceMeters());
                    bestAvailableSeatsAtPickup = capCheck.minAvailableSeats;
                }
            }
        }

        if (bestPlannedStops == null) {
            return null; // No feasible insertion found within capacity and detour limits
        }

        // Calculate estimated pickup wait time from driver current location to pickup
        RouteResult toPickup = dijkstra.findShortestPath(trip.getCurrentLocationId(), pickupId);
        long pickupWait = toPickup.isReachable() ? toPickup.getEstimatedTravelTimeSeconds() : bestExtraTime;
        double pickupDist = toPickup.isReachable() ? toPickup.getTotalDistanceMeters() : bestExtraDistance;

        double compositeScore = candidateScorer.calculateScore(
                bestExtraDistance,
                pickupWait,
                bestDetourRatio,
                bestAvailableSeatsAtPickup,
                false
        );

        double totalRouteDistance = originalRouteDistance + bestExtraDistance;
        long totalRouteTime = Math.round(totalRouteDistance / 3.33);

        return new CandidateRide(
                trip.getTripId(),
                trip.getVehicleId(),
                trip.getDriverId(),
                trip.getDriverName(),
                trip.getRickshawNumber(),
                trip.getTotalCapacity(),
                trip.getCurrentlyOccupiedSeats(),
                bestAvailableSeatsAtPickup,
                true, // shared carpool ride
                trip.getCurrentLocationId(),
                pickupDist,
                pickupWait,
                directRoute.getTotalDistanceMeters(),
                directRoute.getEstimatedTravelTimeSeconds(),
                totalRouteDistance,
                totalRouteTime,
                bestExtraDistance,
                bestDetourRatio,
                compositeScore,
                false,
                null,
                bestPlannedStops
        );
    }

    private static class CapacityCheckResult {
        final boolean valid;
        final int minAvailableSeats;

        CapacityCheckResult(boolean valid, int minAvailableSeats) {
            this.valid = valid;
            this.minAvailableSeats = minAvailableSeats;
        }
    }

    private CapacityCheckResult checkCapacityAlongSequence(int initialOccupancy, int capacity,
                                                          List<TripStopSnapshot> existingStops,
                                                          int pickupInsertIdx, int dropoffInsertIdx,
                                                          int newPassengers) {
        int currentOccupancy = initialOccupancy;
        int minFreeSeats = capacity - currentOccupancy;

        // Simulate stop-by-stop passenger changes
        for (int k = 0; k < existingStops.size(); k++) {
            if (k + 1 == pickupInsertIdx) {
                currentOccupancy += newPassengers;
                if (currentOccupancy > capacity) {
                    return new CapacityCheckResult(false, 0);
                }
            }
            if (k + 1 == dropoffInsertIdx) {
                currentOccupancy -= newPassengers;
            }

            TripStopSnapshot s = existingStops.get(k);
            currentOccupancy += s.getBoardingCount();
            currentOccupancy -= s.getAlightingCount();

            if (currentOccupancy > capacity || currentOccupancy < 0) {
                return new CapacityCheckResult(false, 0);
            }

            minFreeSeats = Math.min(minFreeSeats, capacity - currentOccupancy);
        }

        // In case dropoff is at the very end
        if (dropoffInsertIdx > existingStops.size()) {
            if (currentOccupancy + newPassengers > capacity) {
                return new CapacityCheckResult(false, 0);
            }
        }

        return new CapacityCheckResult(true, Math.max(0, minFreeSeats));
    }

    private double calculateSequenceDistance(List<String> nodeIds) {
        double total = 0.0;
        for (int i = 0; i < nodeIds.size() - 1; i++) {
            String u = nodeIds.get(i);
            String v = nodeIds.get(i + 1);
            if (u.equals(v)) continue;
            RouteResult step = dijkstra.findShortestPath(u, v);
            if (!step.isReachable()) {
                return Double.POSITIVE_INFINITY;
            }
            total += step.getTotalDistanceMeters();
        }
        return total;
    }
}

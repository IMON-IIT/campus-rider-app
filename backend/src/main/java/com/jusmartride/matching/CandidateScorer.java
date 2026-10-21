package com.jusmartride.matching;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Multi-Criteria Candidate Scorer for JU Smart Ride.
 *
 * Utilizes a Java PriorityQueue (Min-Heap) to rank feasible ride candidates
 * based on a normalized composite cost function.
 *
 * Cost Function:
 *   Score = w_extraDist * extraDist + w_waitTime * pickupWait + w_detour * (detourRatio * 1000) - w_seats * availableSeats
 *
 * Candidates with lower scores are preferred (shorter wait, smaller detour, more available seats).
 */
public class CandidateScorer {

    private final double weightExtraDistance;
    private final double weightWaitTime;
    private final double weightDetourRatio;
    private final double weightAvailableSeats;

    public CandidateScorer() {
        // Defaults: balanced for campus scale where 1 min wait ~ 200m distance
        this.weightExtraDistance = 1.0;     // 1 point per extra meter
        this.weightWaitTime = 2.5;          // 2.5 points per second wait time
        this.weightDetourRatio = 500.0;     // Penalty for higher detour percentage
        this.weightAvailableSeats = 150.0;  // Reward for extra seat comfort & availability
    }

    public CandidateScorer(double weightExtraDistance, double weightWaitTime,
                           double weightDetourRatio, double weightAvailableSeats) {
        this.weightExtraDistance = weightExtraDistance;
        this.weightWaitTime = weightWaitTime;
        this.weightDetourRatio = weightDetourRatio;
        this.weightAvailableSeats = weightAvailableSeats;
    }

    public double calculateScore(double extraDistanceMeters, long pickupWaitSeconds,
                                 double detourRatio, int availableSeats, boolean isDirect) {
        double score = (weightExtraDistance * Math.max(0.0, extraDistanceMeters))
                + (weightWaitTime * Math.max(0, pickupWaitSeconds))
                + (weightDetourRatio * Math.max(0.0, detourRatio))
                - (weightAvailableSeats * availableSeats);

        // Small tie-break preference for direct rides if detour is noticeable
        if (!isDirect) {
            score += 50.0;
        }

        return Math.max(0.0, score);
    }

    /**
     * Ranks a collection of candidates using a Java PriorityQueue (Min-Heap).
     * The lowest-cost candidate is marked as recommended with an explicit explanation.
     *
     * @param candidates unordered collection of feasible candidates
     * @return ordered list from most recommended to least recommended
     */
    public List<CandidateRide> rankCandidates(Collection<CandidateRide> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        // Java PriorityQueue stores candidates in ascending score order (Min-Heap)
        PriorityQueue<CandidateRide> minHeap = new PriorityQueue<>(candidates);

        List<CandidateRide> ranked = new ArrayList<>(candidates.size());
        boolean isFirst = true;

        while (!minHeap.isEmpty()) {
            CandidateRide candidate = minHeap.poll();
            if (isFirst) {
                // Top candidate gets marked as recommended with explanation
                String explanation = buildExplanation(candidate);
                CandidateRide recommendedRide = new CandidateRide(
                        candidate.getTripId(),
                        candidate.getVehicleId(),
                        candidate.getDriverId(),
                        candidate.getDriverName(),
                        candidate.getRickshawNumber(),
                        candidate.getTotalCapacity(),
                        candidate.getOccupiedSeats(),
                        candidate.getAvailableSeats(),
                        candidate.isSharedRide(),
                        candidate.getDriverCurrentLocationId(),
                        candidate.getPickupDistanceMeters(),
                        candidate.getEstimatedPickupWaitSeconds(),
                        candidate.getDirectTripDistanceMeters(),
                        candidate.getDirectTripTimeSeconds(),
                        candidate.getTotalRouteDistanceMeters(),
                        candidate.getTotalRouteTimeSeconds(),
                        candidate.getDetourDistanceMeters(),
                        candidate.getDetourRatio(),
                        candidate.getCompositeScore(),
                        true,
                        explanation,
                        candidate.getPlannedStops()
                );
                ranked.add(recommendedRide);
                isFirst = false;
            } else {
                ranked.add(candidate);
            }
        }

        return ranked;
    }

    private String buildExplanation(CandidateRide c) {
        long waitMinutes = Math.max(1, Math.round(c.getEstimatedPickupWaitSeconds() / 60.0));
        if (!c.isSharedRide()) {
            return String.format("Recommended: Direct private ride, shortest pickup time (~%d min), %d seats free",
                    waitMinutes, c.getAvailableSeats());
        } else {
            double detourPercent = Math.round(c.getDetourRatio() * 1000.0) / 10.0;
            return String.format("Recommended: Optimal shared ride, pickup in ~%d min, low detour (+%.1f%%), %d seats free",
                    waitMinutes, detourPercent, c.getAvailableSeats());
        }
    }
}

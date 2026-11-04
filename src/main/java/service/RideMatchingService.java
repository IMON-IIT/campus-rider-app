package service;

import dsa.CampusGraph;
import dsa.Dijkstra;
import dsa.RouteResult;
import model.RideRequest;
import model.Rickshaw;
import model.TripStop;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Chooses a rickshaw for a ride request.
 *
 * Rule: among the online, AVAILABLE rickshaws, pick the one with the shortest
 * road distance (Dijkstra) from its current location to the passenger's pickup.
 * The passenger's pickup-to-destination route must also be reachable.
 * If two are equally close, the smaller rickshaw id wins, so the result is
 * always the same for the same data.
 *
 * Cost: one Dijkstra run per available rickshaw, O(R * (V + E) log V).
 */
public class RideMatchingService {
    private static final double MAX_DETOUR_RATIO = 0.20;
    private static final double MAX_ABSOLUTE_DETOUR_METERS = 150.0;

    /** The chosen rickshaw and how far it must travel to reach the pickup. */
    public static class Match {
        private final Rickshaw rickshaw;
        private final double distanceToPickupMeters;

        Match(Rickshaw rickshaw, double distanceToPickupMeters) {
            this.rickshaw = rickshaw;
            this.distanceToPickupMeters = distanceToPickupMeters;
        }

        public Rickshaw getRickshaw() {
            return rickshaw;
        }

        public double getDistanceToPickupMeters() {
            return distanceToPickupMeters;
        }
    }

    /** A route-compatible active trip and its proposed stop order. */
    public static class TripMatch {
        private final Rickshaw rickshaw;
        private final double distanceToPickupMeters;
        private final double extraDistanceMeters;
        private final List<TripStop> itinerary;

        TripMatch(Rickshaw rickshaw, double distanceToPickupMeters,
                  double extraDistanceMeters, List<TripStop> itinerary) {
            this.rickshaw = rickshaw;
            this.distanceToPickupMeters = distanceToPickupMeters;
            this.extraDistanceMeters = extraDistanceMeters;
            this.itinerary = List.copyOf(itinerary);
        }

        public Rickshaw getRickshaw() {
            return rickshaw;
        }

        public double getDistanceToPickupMeters() {
            return distanceToPickupMeters;
        }

        public double getExtraDistanceMeters() {
            return extraDistanceMeters;
        }

        public List<TripStop> getItinerary() {
            return itinerary;
        }
    }

    private final CampusGraph graph;

    public RideMatchingService(CampusGraph graph) {
        this.graph = graph;
    }

    /** Returns the best match, or null if no available rickshaw can reach the pickup. */
    public Match findBestRickshaw(RideRequest request, List<Rickshaw> rickshaws) {
        return findBestRickshaw(request, rickshaws, Set.of());
    }

    /** Finds the nearest available rickshaw that has not rejected this request. */
    public Match findBestRickshaw(RideRequest request, List<Rickshaw> rickshaws, Set<String> rejectedRickshawIds) {
        return findBestRickshaw(request, rickshaws, rejectedRickshawIds, Set.of());
    }

    public Match findBestRickshaw(
            RideRequest request, List<Rickshaw> rickshaws, Set<String> rejectedRickshawIds,
            Set<String> offlineRickshawIds) {
        RouteResult passengerRoute = Dijkstra.findShortestPath(
                graph, request.getPickupId(), request.getDestinationId());
        if (!passengerRoute.isFound()) {
            return null;
        }

        Match best = null;
        for (Rickshaw rickshaw : rickshaws) {
            if (!rickshaw.isAvailable() || rejectedRickshawIds.contains(rickshaw.getId())
                    || offlineRickshawIds.contains(rickshaw.getId())) {
                continue;
            }
            RouteResult toPickup = Dijkstra.findShortestPath(graph, rickshaw.getLocationId(), request.getPickupId());
            if (!toPickup.isFound()) {
                continue;
            }
            double distance = toPickup.getTotalDistanceMeters();
            if (best == null
                    || distance < best.distanceToPickupMeters
                    || (distance == best.distanceToPickupMeters
                        && rickshaw.getId().compareTo(best.rickshaw.getId()) < 0)) {
                best = new Match(rickshaw, distance);
            }
        }
        return best;
    }

    /**
     * Finds active trips whose remaining route can include pickup then drop-off.
     * Up to 20% detour is allowed, with a 150 m minimum allowance for short trips.
     */
    public List<TripMatch> findCompatibleTrips(
            String pickupId, String destinationId, int seats, List<Rickshaw> rickshaws,
            Set<String> rejectedRickshawIds, Set<String> offlineRickshawIds,
            java.util.Map<String, Integer> reservedSeats) {
        List<TripMatch> matches = new ArrayList<>();
        for (Rickshaw rickshaw : rickshaws) {
            if (!rickshaw.hasActiveTrip()
                    || rejectedRickshawIds.contains(rickshaw.getId())
                    || offlineRickshawIds.contains(rickshaw.getId())
                    || rickshaw.getAvailableSeats() - reservedSeats.getOrDefault(rickshaw.getId(), 0) < seats) {
                continue;
            }
            TripMatch match = fitIntoTrip(pickupId, destinationId, rickshaw);
            if (match != null) {
                matches.add(match);
            }
        }
        matches.sort(Comparator.comparingDouble(TripMatch::getExtraDistanceMeters)
                .thenComparingDouble(TripMatch::getDistanceToPickupMeters)
                .thenComparing(match -> match.getRickshaw().getId()));
        return List.copyOf(matches);
    }

    public TripMatch fitIntoTrip(String pickupId, String destinationId, Rickshaw rickshaw) {
        if (!graph.hasLocation(pickupId) || !graph.hasLocation(destinationId)
                || pickupId.equals(destinationId) || !rickshaw.hasActiveTrip()
                || rickshaw.getItinerary().isEmpty()) {
            return null;
        }
        List<TripStop> existing = rickshaw.getItinerary();
        if (existing.get(existing.size() - 1).type() != TripStop.Type.DESTINATION) {
            return null;
        }
        double baseline = routeDistance(rickshaw.getLocationId(), existing);
        RouteResult toPickup = Dijkstra.findShortestPath(graph, rickshaw.getLocationId(), pickupId);
        if (baseline < 0 || !toPickup.isFound()) {
            return null;
        }
        double maxDetour = Math.max(MAX_ABSOLUTE_DETOUR_METERS, baseline * MAX_DETOUR_RATIO);
        TripMatch best = null;
        int destinationIndex = existing.size() - 1;
        for (int pickupIndex = 0; pickupIndex <= destinationIndex; pickupIndex++) {
            for (int dropoffIndex = pickupIndex + 1; dropoffIndex <= destinationIndex + 1; dropoffIndex++) {
                List<TripStop> candidate = new ArrayList<>(existing);
                candidate.add(pickupIndex, new TripStop(-1, pickupId, TripStop.Type.PICKUP));
                candidate.add(dropoffIndex, new TripStop(-1, destinationId, TripStop.Type.DROPOFF));
                double candidateDistance = routeDistance(rickshaw.getLocationId(), candidate);
                double extra = candidateDistance - baseline;
                if (candidateDistance < 0 || extra > maxDetour + 1e-9) {
                    continue;
                }
                TripMatch found = new TripMatch(rickshaw, toPickup.getTotalDistanceMeters(),
                        Math.max(0, extra), candidate);
                if (best == null
                        || found.getExtraDistanceMeters() < best.getExtraDistanceMeters()
                        || (found.getExtraDistanceMeters() == best.getExtraDistanceMeters()
                            && found.getDistanceToPickupMeters() < best.getDistanceToPickupMeters())) {
                    best = found;
                }
            }
        }
        return best;
    }

    private double routeDistance(String startId, List<TripStop> stops) {
        String from = startId;
        double total = 0;
        for (TripStop stop : stops) {
            RouteResult leg = Dijkstra.findShortestPath(graph, from, stop.locationId());
            if (!leg.isFound()) {
                return -1;
            }
            total += leg.getTotalDistanceMeters();
            from = stop.locationId();
        }
        return total;
    }
}

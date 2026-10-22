package service;

import dsa.QueuePolicy;
import dsa.RideQueue;
import dsa.RouteResult;
import model.PassengerType;
import model.RideRequest;
import model.RideStatus;
import model.Rickshaw;
import storage.CsvStorage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Manages ride requests and rickshaws: create, match, start, complete, cancel.
 *
 * Every successful change is saved to rides.csv and rickshaws.csv right away.
 * Every method returns a message for the user, or throws an exception
 * (IllegalArgumentException / IllegalStateException) with the reason it failed.
 */
public class RideService {
    private final CsvStorage storage;
    private final RouteService routeService;
    private final RideMatchingService matchingService;

    private final Map<Integer, RideRequest> rides = new LinkedHashMap<>();   // all rides, by id
    private final Map<String, Rickshaw> rickshaws = new LinkedHashMap<>();   // all rickshaws, by id
    private final RideQueue pendingQueue = new RideQueue(QueuePolicy.FIFO);
    private int nextRideId = 1;

    public RideService(CsvStorage storage, RouteService routeService) throws IOException {
        this.storage = storage;
        this.routeService = routeService;
        this.matchingService = new RideMatchingService(routeService.getGraph());
        loadRickshaws();
        loadRides();
    }

    // ------------------------------------------------------------- loading

    private void loadRickshaws() throws IOException {
        boolean skipped = false;
        for (Rickshaw r : storage.loadRickshaws()) {
            if (!routeService.isValidLocation(r.getLocationId()) || rickshaws.containsKey(r.getId())) {
                storage.addWarning("Skipped rickshaw " + r.getId() + ": unknown location or duplicate id.");
                skipped = true;
                continue;
            }
            r.setAvailable(true); // real availability is decided from the rides below
            rickshaws.put(r.getId(), r);
        }
        if (skipped) {
            storage.backup(CsvStorage.RICKSHAWS_FILE);
        }
    }

    /**
     * Loads rides and rebuilds the pending queue. A rickshaw is busy exactly
     * when it has a MATCHED or IN_PROGRESS ride, so the two files always agree.
     */
    private void loadRides() throws IOException {
        boolean skipped = false;
        for (RideRequest ride : storage.loadRides()) {
            String problem = checkLoadedRide(ride);
            if (problem != null) {
                storage.addWarning("Skipped ride #" + ride.getId() + ": " + problem);
                skipped = true;
                continue;
            }
            if (ride.getStatus() == RideStatus.PENDING) {
                ride.setRickshawId("");
                pendingQueue.add(ride);
            }
            if (ride.getStatus().isActive()) {
                rickshaws.get(ride.getRickshawId()).setAvailable(false);
            }
            rides.put(ride.getId(), ride);
            nextRideId = Math.max(nextRideId, ride.getId() + 1);
        }
        if (skipped) {
            storage.backup(CsvStorage.RIDES_FILE);
        }
    }

    private String checkLoadedRide(RideRequest ride) {
        if (rides.containsKey(ride.getId())) {
            return "duplicate id";
        }
        if (!routeService.isValidLocation(ride.getPickupId()) || !routeService.isValidLocation(ride.getDestinationId())) {
            return "unknown location";
        }
        if (ride.getStatus().isActive()) {
            Rickshaw rickshaw = rickshaws.get(ride.getRickshawId());
            if (rickshaw == null) {
                return "unknown rickshaw '" + ride.getRickshawId() + "'";
            }
            if (!rickshaw.isAvailable()) {
                return "rickshaw " + rickshaw.getId() + " already has another active ride";
            }
        }
        return null;
    }

    // ---------------------------------------------------------- read access

    public List<RideRequest> getRides() {
        return Collections.unmodifiableList(new ArrayList<>(rides.values()));
    }

    public List<Rickshaw> getRickshaws() {
        return Collections.unmodifiableList(new ArrayList<>(rickshaws.values()));
    }

    public RideRequest getRide(int id) {
        return rides.get(id);
    }

    public Rickshaw getRickshaw(String id) {
        return rickshaws.get(id);
    }

    public List<RideRequest> getPendingInOrder() {
        return pendingQueue.inProcessingOrder();
    }

    public QueuePolicy getQueuePolicy() {
        return pendingQueue.getPolicy();
    }

    public void setQueuePolicy(QueuePolicy policy) {
        pendingQueue.setPolicy(policy);
    }

    public RouteService getRouteService() {
        return routeService;
    }

    // ------------------------------------------------------------- actions

    /** Creates a PENDING request and adds it to the queue. */
    public RideRequest createRequest(String passengerName, PassengerType type, String pickupId, String destinationId) {
        // Commas would break the CSV format, so they are replaced with spaces.
        String name = passengerName == null ? "" : passengerName.replace(',', ' ').trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Please enter the passenger name.");
        }
        if (type == null) {
            throw new IllegalArgumentException("Please choose a passenger type.");
        }
        if (!routeService.isValidLocation(pickupId) || !routeService.isValidLocation(destinationId)) {
            throw new IllegalArgumentException("Please choose a valid pickup and destination.");
        }
        if (pickupId.equals(destinationId)) {
            throw new IllegalArgumentException("Pickup and destination must be different.");
        }
        RouteResult route = routeService.findRoute(pickupId, destinationId);
        if (!route.isFound()) {
            throw new IllegalArgumentException(route.getMessage());
        }

        RideRequest ride = new RideRequest(nextRideId, name, type, pickupId, destinationId,
                route.getTotalDistanceMeters(), RideStatus.PENDING, "");
        nextRideId++;
        rides.put(ride.getId(), ride);
        pendingQueue.add(ride);
        save();
        return ride;
    }

    /**
     * Takes the next request from the queue (by the active policy) and gives it
     * the nearest available rickshaw. If none is available the request stays
     * at the front of the queue.
     */
    public String matchNextRequest() {
        RideRequest next = pendingQueue.peek();
        if (next == null) {
            throw new IllegalStateException("There are no pending requests to match.");
        }
        RideMatchingService.Match match = matchingService.findBestRickshaw(next, getRickshaws());
        if (match == null) {
            throw new IllegalStateException("No available rickshaw for request #" + next.getId()
                    + ". It stays first in the queue.");
        }

        pendingQueue.poll(); // removed from the queue, so it can never be matched twice
        Rickshaw rickshaw = match.getRickshaw();
        next.moveTo(RideStatus.MATCHED);
        next.setRickshawId(rickshaw.getId());
        rickshaw.setAvailable(false);
        save();
        return String.format(Locale.ROOT, "Request #%d (%s) matched with %s (%s), %.1f m from the pickup.",
                next.getId(), next.getPassengerName(), rickshaw.getId(), rickshaw.getDriverName(),
                match.getDistanceToPickupMeters());
    }

    public String startRide(int rideId) {
        RideRequest ride = requireRide(rideId);
        ride.moveTo(RideStatus.IN_PROGRESS);
        save();
        return "Ride #" + rideId + " is now in progress.";
    }

    /** Completes the ride; the rickshaw becomes available at the destination. */
    public String completeRide(int rideId) {
        RideRequest ride = requireRide(rideId);
        ride.moveTo(RideStatus.COMPLETED);
        Rickshaw rickshaw = rickshaws.get(ride.getRickshawId());
        rickshaw.setAvailable(true);
        rickshaw.setLocationId(ride.getDestinationId());
        save();
        return "Ride #" + rideId + " completed. " + rickshaw.getId() + " is available at "
                + routeService.getLocationName(ride.getDestinationId()) + ".";
    }

    /** Cancels a PENDING or MATCHED ride and frees its rickshaw if it had one. */
    public String cancelRide(int rideId) {
        RideRequest ride = requireRide(rideId);
        RideStatus before = ride.getStatus();
        ride.moveTo(RideStatus.CANCELLED);
        if (before == RideStatus.PENDING) {
            pendingQueue.remove(ride);
        } else {
            rickshaws.get(ride.getRickshawId()).setAvailable(true);
        }
        save();
        return "Ride #" + rideId + " cancelled.";
    }

    private RideRequest requireRide(int rideId) {
        RideRequest ride = rides.get(rideId);
        if (ride == null) {
            throw new IllegalArgumentException("Ride #" + rideId + " does not exist.");
        }
        return ride;
    }

    private void save() {
        try {
            storage.saveRides(getRides());
            storage.saveRickshaws(getRickshaws());
        } catch (IOException e) {
            throw new UncheckedIOException("The change was made but could not be saved to "
                    + storage.getDataDir() + ": " + e.getMessage(), e);
        }
    }

    /** Count of rickshaws that are free right now. */
    public int countAvailableRickshaws() {
        int count = 0;
        for (Rickshaw r : rickshaws.values()) {
            if (r.isAvailable()) {
                count++;
            }
        }
        return count;
    }
}

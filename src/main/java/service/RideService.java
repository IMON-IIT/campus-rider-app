package service;

import dsa.QueuePolicy;
import dsa.RideQueue;
import dsa.RouteResult;
import model.PassengerType;
import model.RideRequest;
import model.RideStatus;
import model.Rickshaw;
import model.TripStop;
import storage.CsvStorage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Manages ride requests and rickshaws: create, match, start, complete, cancel.
 *
 * Every successful change is saved to rides.csv and rickshaws.csv right away.
 * Every method returns a message for the user, or throws an exception
 * (IllegalArgumentException / IllegalStateException) with the reason it failed.
 */
public class RideService {
    public static final int TRIP_STEP_INTERVAL_MS = 2400;

    private final CsvStorage storage;
    private final RouteService routeService;
    private final RideMatchingService matchingService;

    private final Map<Integer, RideRequest> rides = new LinkedHashMap<>();   // all rides, by id
    private final Map<String, Rickshaw> rickshaws = new LinkedHashMap<>();   // all rickshaws, by id
    private final Map<Integer, List<String>> rideUpdates = new LinkedHashMap<>();
    private final Map<String, TripMovement> tripMovements = new LinkedHashMap<>();
    private final Set<String> offlineRickshawIds = new HashSet<>();
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
            if (!routeService.isValidLocation(r.getLocationId())
                    || (r.hasActiveTrip() && (!routeService.isValidLocation(r.getDestinationId())
                    || r.getItinerary().stream().anyMatch(stop -> !routeService.isValidLocation(stop.locationId()))))
                    || rickshaws.containsKey(r.getId())) {
                storage.addWarning("Skipped rickshaw " + r.getId() + ": unknown location or duplicate id.");
                skipped = true;
                continue;
            }
            if (!r.hasActiveTrip()) {
                r.setAvailable(true); // legacy availability is reconstructed from rides below
            }
            rickshaws.put(r.getId(), r);
        }
        if (skipped) {
            storage.backup(CsvStorage.RICKSHAWS_FILE);
        }
    }

    /**
     * Loads rides and rebuilds the pending queue. Shared-trip bookings may have
     * several active rides on the same rickshaw; legacy rides may not.
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
            if (!ride.isSharedTrip() && !rickshaw.isAvailable()) {
                return "rickshaw " + rickshaw.getId() + " already has another active ride";
            }
            if (ride.isSharedTrip() && !rickshaw.hasActiveTrip()) {
                return "shared ride is not attached to an active trip";
            }
        }
        return null;
    }

    // ---------------------------------------------------------- read access

    public synchronized List<RideRequest> getRides() {
        return Collections.unmodifiableList(new ArrayList<>(rides.values()));
    }

    public synchronized RideRequest getLatestRideForPassenger(String mobileNumber) {
        RideRequest latest = null;
        for (RideRequest ride : rides.values()) {
            if (ride.getPassengerMobileNumber().equals(mobileNumber)
                    && (latest == null || ride.getId() > latest.getId())) {
                latest = ride;
            }
        }
        return latest;
    }

    public synchronized List<Rickshaw> getRickshaws() {
        return Collections.unmodifiableList(new ArrayList<>(rickshaws.values()));
    }

    public synchronized RideRequest getRide(int id) {
        return rides.get(id);
    }

    public synchronized Rickshaw getRickshaw(String id) {
        return rickshaws.get(id);
    }

    public synchronized List<RideRequest> getPendingInOrder() {
        return pendingQueue.inProcessingOrder();
    }

    public synchronized List<RideRequest> getAssignedRequests(String rickshawId) {
        List<RideRequest> assigned = new ArrayList<>();
        for (RideRequest ride : rides.values()) {
            if (ride.getStatus() == RideStatus.OFFERED && ride.getRickshawId().equals(rickshawId)) {
                assigned.add(ride);
            }
        }
        return Collections.unmodifiableList(assigned);
    }

    public synchronized List<RideRequest> getActivePassengerRides(String rickshawId) {
        List<RideRequest> active = new ArrayList<>();
        for (RideRequest ride : rides.values()) {
            if (ride.isSharedTrip() && ride.getRickshawId().equals(rickshawId)
                    && (ride.getStatus() == RideStatus.MATCHED
                    || ride.getStatus() == RideStatus.ARRIVED_AT_PICKUP
                    || ride.getStatus() == RideStatus.IN_PROGRESS
                    || ride.getStatus() == RideStatus.ARRIVED_AT_DESTINATION)) {
                active.add(ride);
            }
        }
        return Collections.unmodifiableList(active);
    }

    public synchronized SeatAvailability getSeatAvailability(String rickshawId) {
        Rickshaw rickshaw = requireRickshaw(rickshawId);
        int bookedSeats = 0;
        int occupiedSeats = 0;
        int passengerBookings = 0;
        for (RideRequest ride : rides.values()) {
            if (!ride.isSharedTrip() || !ride.getRickshawId().equals(rickshawId)
                    || !ride.getStatus().isActive()) {
                continue;
            }
            bookedSeats += ride.getRequestedSeats();
            passengerBookings++;
            if (ride.getStatus() == RideStatus.IN_PROGRESS
                    || ride.getStatus() == RideStatus.ARRIVED_AT_DESTINATION) {
                occupiedSeats += ride.getRequestedSeats();
            }
        }
        int offeredSeats = getReservedSeatsByRickshaw().getOrDefault(rickshawId, 0);
        int availableSeats = Math.max(0, rickshaw.getAvailableSeats() - offeredSeats);
        return new SeatAvailability(rickshaw.getTotalSeats(), bookedSeats, occupiedSeats,
                availableSeats, passengerBookings);
    }

    public synchronized List<RideMatchingService.TripMatch> findAvailableTrips(
            String pickupId, String destinationId, int seats) {
        if (!routeService.isValidLocation(pickupId) || !routeService.isValidLocation(destinationId)
                || pickupId.equals(destinationId) || seats < 1) {
            return List.of();
        }
        return matchingService.findCompatibleTrips(pickupId, destinationId, seats,
                getRickshaws(), Set.of(), offlineRickshawIds, getReservedSeatsByRickshaw());
    }

    public synchronized boolean isDriverOnline(String rickshawId) {
        requireRickshaw(rickshawId);
        return !offlineRickshawIds.contains(rickshawId);
    }

    public synchronized void setDriverOnline(String rickshawId, boolean online) {
        requireRickshaw(rickshawId);
        if (!online && (!getAssignedRequests(rickshawId).isEmpty()
                || !getActivePassengerRides(rickshawId).isEmpty())) {
            throw new IllegalStateException("Finish or respond to assigned passenger rides before going offline.");
        }
        if (online) {
            offlineRickshawIds.remove(rickshawId);
        } else {
            offlineRickshawIds.add(rickshawId);
        }
    }

    public synchronized void registerRickshaw(Rickshaw rickshaw) {
        if (rickshaw == null || rickshaw.getId() == null || rickshaw.getId().isBlank()
                || rickshaw.getDriverName() == null || rickshaw.getDriverName().isBlank()
                || !routeService.isValidLocation(rickshaw.getLocationId())) {
            throw new IllegalArgumentException("The rickshaw registration details are invalid.");
        }

        if (rickshaws.containsKey(rickshaw.getId())) {
            throw new IllegalArgumentException("Rickshaw '" + rickshaw.getId() + "' is already registered.");
        }
        rickshaws.put(rickshaw.getId(), rickshaw);
        try {
            save();
        } catch (RuntimeException e) {
            rickshaws.remove(rickshaw.getId());
            throw e;
        }
    }

    public synchronized void configureRickshawSeats(String rickshawId, int totalSeats, int availableSeats) {
        Rickshaw rickshaw = requireRickshaw(rickshawId);
        if (rickshaw.hasStoredSeatConfiguration()) {
            return;
        }
        rickshaw.setSeatCounts(totalSeats, availableSeats);
        save();
    }

    public synchronized List<String> getRideUpdates(int rideId) {
        requireRide(rideId);
        return List.copyOf(rideUpdates.getOrDefault(rideId, List.of()));
    }

    public synchronized QueuePolicy getQueuePolicy() {
        return pendingQueue.getPolicy();
    }

    public synchronized void setQueuePolicy(QueuePolicy policy) {
        pendingQueue.setPolicy(policy);
    }

    public RouteService getRouteService() {
        return routeService;
    }

    // ------------------------------------------------------------- actions

    /** Creates a PENDING request and adds it to the queue. */
    public synchronized RideRequest createRequest(
            String passengerName, PassengerType type, String pickupId, String destinationId) {
        RideRequest ride = buildRequest(passengerName, type, pickupId, destinationId);
        nextRideId++;
        rides.put(ride.getId(), ride);
        pendingQueue.add(ride);
        save();
        return ride;
    }

    /** Creates a passenger request and immediately offers it to the nearest suitable driver. */
    public synchronized RideRequest submitRideRequest(
            String passengerName, PassengerType type, String pickupId, String destinationId) {
        return submitRideRequest(passengerName, type, pickupId, destinationId, "");
    }

    public synchronized RideRequest submitRideRequest(
            String passengerName, PassengerType type, String pickupId, String destinationId,
            String passengerMobileNumber) {
        RideRequest ride = buildRequest(passengerName, type, pickupId, destinationId, passengerMobileNumber);
        nextRideId++;
        rides.put(ride.getId(), ride);
        addRideUpdate(ride, "Request submitted.");
        addRideUpdate(ride, "Searching for a suitable driver.");
        offerToNextDriver(ride);
        save();
        return ride;
    }

    /** Creates a seat booking and offers it to the selected compatible active trip. */
    public synchronized RideRequest bookSharedRide(
            String passengerName, String passengerMobileNumber, String pickupId, String destinationId,
            int requestedSeats, String selectedRickshawId) {
        if (passengerMobileNumber != null && !passengerMobileNumber.isBlank()
                && rides.values().stream().anyMatch(ride -> ride.isSharedTrip()
                    && ride.getPassengerMobileNumber().equals(passengerMobileNumber)
                    && ride.getStatus().isActive())) {
            throw new IllegalStateException(
                    "You already have an active ride request. Complete or cancel it before requesting another.");
        }
        RideRequest ride = buildRequest(passengerName, PassengerType.STUDENT, pickupId, destinationId,
                passengerMobileNumber, requestedSeats, true);
        List<RideMatchingService.TripMatch> options = matchingService.findCompatibleTrips(
                pickupId, destinationId, requestedSeats, getRickshaws(), Set.of(),
                offlineRickshawIds, getReservedSeatsByRickshaw());
        RideMatchingService.TripMatch selected = options.stream()
                .filter(match -> selectedRickshawId == null || selectedRickshawId.isBlank()
                        || match.getRickshaw().getId().equals(selectedRickshawId))
                .findFirst().orElse(null);
        if (selectedRickshawId != null && !selectedRickshawId.isBlank() && selected == null) {
            throw new IllegalStateException("That rickshaw is no longer available for this route and seat count.");
        }

        nextRideId++;
        rides.put(ride.getId(), ride);
        addRideUpdate(ride, "Request submitted for " + requestedSeats + " seat(s).");
        if (selected == null) {
            ride.moveTo(RideStatus.NO_DRIVER_AVAILABLE);
            addRideUpdate(ride, "No active trip has a compatible route and enough available seats.");
        } else {
            offerSharedRideTo(ride, selected);
        }
        save();
        return ride;
    }

    private RideRequest buildRequest(
            String passengerName, PassengerType type, String pickupId, String destinationId) {
        return buildRequest(passengerName, type, pickupId, destinationId, "");
    }

    private RideRequest buildRequest(
            String passengerName, PassengerType type, String pickupId, String destinationId,
            String passengerMobileNumber) {
        return buildRequest(passengerName, type, pickupId, destinationId, passengerMobileNumber, 1, false);
    }

    private RideRequest buildRequest(
            String passengerName, PassengerType type, String pickupId, String destinationId,
            String passengerMobileNumber, int requestedSeats, boolean sharedTrip) {
        String name = passengerName == null ? "" : passengerName.replace(',', ' ').trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Please enter the passenger name.");
        }
        if (type == null) {
            throw new IllegalArgumentException("Please choose a passenger type.");
        }
        if (requestedSeats < 1) {
            throw new IllegalArgumentException("Select at least one seat.");
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
        return new RideRequest(nextRideId, name, type, pickupId, destinationId,
                route.getTotalDistanceMeters(), RideStatus.PENDING, "", List.of(), passengerMobileNumber,
                requestedSeats, sharedTrip);
    }

    private void offerToNextDriver(RideRequest ride) {
        Set<String> rejectedRickshawIds = new HashSet<>(ride.getRejectedRickshawIds());
        RideMatchingService.Match match =
                matchingService.findBestRickshaw(
                        ride, getRickshaws(), rejectedRickshawIds, offlineRickshawIds);
        if (match == null) {
            ride.setRickshawId("");
            ride.moveTo(RideStatus.NO_DRIVER_AVAILABLE);
            addRideUpdate(ride, "No suitable driver is available for this route.");
            return;
        }

        Rickshaw rickshaw = match.getRickshaw();
        ride.setRickshawId(rickshaw.getId());
        ride.moveTo(RideStatus.OFFERED);
        rickshaw.setAvailable(false);
        addRideUpdate(ride, "Request sent to " + rickshaw.getDriverName()
                + " (" + rickshaw.getId() + "). Waiting for the driver to respond.");
    }

    public synchronized String acceptRideRequest(int rideId, String rickshawId) {
        RideRequest ride = requireAssignedOffer(rideId, rickshawId);
        if (ride.isSharedTrip()) {
            return acceptSharedRide(ride, requireRickshaw(rickshawId));
        }
        ride.moveTo(RideStatus.MATCHED);
        addRideUpdate(ride, "Driver " + rickshaws.get(rickshawId).getDriverName() + " accepted the request.");
        save();
        return "Ride #" + rideId + " accepted.";
    }

    public synchronized String rejectRideRequest(int rideId, String rickshawId) {
        RideRequest ride = requireAssignedOffer(rideId, rickshawId);
        Rickshaw rejectingRickshaw = rickshaws.get(rickshawId);
        ride.addRejectedRickshaw(rickshawId);
        ride.setRickshawId("");
        ride.moveTo(RideStatus.PENDING);
        addRideUpdate(ride, "Driver " + rejectingRickshaw.getDriverName() + " rejected the request.");
        addRideUpdate(ride, "Finding the next suitable driver.");

        if (ride.isSharedTrip()) {
            offerSharedRideToNextTrip(ride);
        } else {
            rejectingRickshaw.setAvailable(true);
            offerToNextDriver(ride);
        }
        save();
        return ride.getStatus() == RideStatus.NO_DRIVER_AVAILABLE
                ? "No other suitable driver is available for ride #" + rideId + "."
                : "Ride #" + rideId + " was sent to " + rickshaws.get(ride.getRickshawId()).getDriverName() + ".";
    }

    private RideRequest requireAssignedOffer(int rideId, String rickshawId) {
        RideRequest ride = requireRide(rideId);
        if (ride.getStatus() != RideStatus.OFFERED || !ride.getRickshawId().equals(rickshawId)) {
            throw new IllegalStateException("Ride #" + rideId + " is not currently offered to this driver.");
        }
        return ride;
    }

    private void addRideUpdate(RideRequest ride, String update) {
        rideUpdates.computeIfAbsent(ride.getId(), ignored -> new ArrayList<>()).add(update);
    }

    private String acceptSharedRide(RideRequest ride, Rickshaw rickshaw) {
        if (!rickshaw.hasActiveTrip() || !isDriverOnline(rickshaw.getId())) {
            throw new IllegalStateException("The selected driver is no longer on an online trip.");
        }
        int reserved = getReservedSeatsByRickshaw().getOrDefault(rickshaw.getId(), 0);
        if (reserved > rickshaw.getAvailableSeats()) {
            throw new IllegalStateException("There are not enough unreserved seats for this booking.");
        }
        RideMatchingService.TripMatch match =
                matchingService.fitIntoTrip(ride.getPickupId(), ride.getDestinationId(), rickshaw);
        if (match == null) {
            throw new IllegalStateException("The rickshaw's remaining route no longer fits this booking.");
        }
        List<TripStop> acceptedItinerary = new ArrayList<>();
        for (TripStop stop : match.getItinerary()) {
            acceptedItinerary.add(stop.rideId() == -1
                    ? new TripStop(ride.getId(), stop.locationId(), stop.type()) : stop);
        }
        int previousSeats = rickshaw.getAvailableSeats();
        rickshaw.setAvailableSeats(previousSeats - ride.getRequestedSeats());
        rickshaw.setItinerary(acceptedItinerary);
        ride.moveTo(RideStatus.MATCHED);
        addRideUpdate(ride, "Driver " + rickshaw.getDriverName() + " accepted. Rickshaw is approaching pickup.");
        save();
        return "Ride #" + ride.getId() + " accepted; rickshaw is approaching the pickup.";
    }

    private Map<String, Integer> getReservedSeatsByRickshaw() {
        Map<String, Integer> reserved = new LinkedHashMap<>();
        for (RideRequest ride : rides.values()) {
            if (ride.isSharedTrip() && ride.getStatus() == RideStatus.OFFERED
                    && !ride.getRickshawId().isBlank()) {
                reserved.merge(ride.getRickshawId(), ride.getRequestedSeats(), Integer::sum);
            }
        }
        return reserved;
    }

    private void offerSharedRideToNextTrip(RideRequest ride) {
        Set<String> rejected = new HashSet<>(ride.getRejectedRickshawIds());
        List<RideMatchingService.TripMatch> options = matchingService.findCompatibleTrips(
                ride.getPickupId(), ride.getDestinationId(), ride.getRequestedSeats(),
                getRickshaws(), rejected, offlineRickshawIds, getReservedSeatsByRickshaw());
        if (options.isEmpty()) {
            ride.setRickshawId("");
            ride.moveTo(RideStatus.NO_DRIVER_AVAILABLE);
            addRideUpdate(ride, "No suitable active trip has enough seats for this route.");
            return;
        }
        offerSharedRideTo(ride, options.get(0));
    }

    private void offerSharedRideTo(RideRequest ride, RideMatchingService.TripMatch match) {
        Rickshaw rickshaw = match.getRickshaw();
        ride.setRickshawId(rickshaw.getId());
        ride.moveTo(RideStatus.OFFERED);
        addRideUpdate(ride, "Request offered to " + rickshaw.getDriverName() + " ("
                + rickshaw.getId() + "), " + String.format(Locale.ROOT, "%.1f m", match.getDistanceToPickupMeters())
                + " from pickup; detour " + String.format(Locale.ROOT, "%.1f m", match.getExtraDistanceMeters())
                + ". Waiting for the driver.");
    }

    public synchronized void startTrip(
            String rickshawId, String currentLocationId, String destinationId,
            int totalSeats, int availableSeats) {
        Rickshaw rickshaw = requireRickshaw(rickshawId);
        if (rickshaw.hasActiveTrip() || hasActiveRideForRickshaw(rickshawId)) {
            throw new IllegalStateException("End the current trip and passenger bookings first.");
        }
        if (!routeService.isValidLocation(currentLocationId) || !routeService.isValidLocation(destinationId)
                || currentLocationId.equals(destinationId)) {
            throw new IllegalArgumentException("Choose different, valid current and destination locations.");
        }
        RouteResult route = routeService.findRoute(currentLocationId, destinationId);
        if (!route.isFound()) {
            throw new IllegalArgumentException(route.getMessage());
        }
        rickshaw.setSeatCounts(totalSeats, availableSeats);
        List<TripStop> stops = List.of(new TripStop(0, destinationId, TripStop.Type.DESTINATION));
        rickshaw.setLocationId(currentLocationId);
        rickshaw.startTrip(destinationId, stops);
        tripMovements.remove(rickshawId);
        save();
    }

    public synchronized void endTrip(String rickshawId) {
        Rickshaw rickshaw = requireRickshaw(rickshawId);
        if (!rickshaw.hasActiveTrip()) {
            throw new IllegalStateException("There is no active trip to end.");
        }
        if (hasActiveRideForRickshaw(rickshawId)) {
            throw new IllegalStateException("Complete or cancel all passenger bookings before ending the trip.");
        }
        rickshaw.finishTrip();
        tripMovements.remove(rickshawId);
        save();
    }

    public synchronized void updateTripSeats(String rickshawId, int totalSeats, int availableSeats) {
        Rickshaw rickshaw = requireRickshaw(rickshawId);
        if (!getAssignedRequests(rickshawId).isEmpty() || !getActivePassengerRides(rickshawId).isEmpty()) {
            throw new IllegalStateException("Seat capacity cannot change while passenger requests are active.");
        }
        rickshaw.setSeatCounts(totalSeats, availableSeats);
        save();
    }

    public synchronized List<String> getTripRoute(String rickshawId) {
        Rickshaw rickshaw = requireRickshaw(rickshawId);
        List<String> route = new ArrayList<>();
        String from = rickshaw.getLocationId();
        route.add(from);
        for (TripStop stop : rickshaw.getItinerary()) {
            RouteResult leg = routeService.findRoute(from, stop.locationId());
            if (!leg.isFound()) {
                break;
            }
            for (int i = 1; i < leg.getPath().size(); i++) {
                route.add(leg.getPath().get(i));
            }
            from = stop.locationId();
        }
        return List.copyOf(route);
    }

    /** The most recently simulated road segment, shared by every dashboard in this process. */
    public synchronized TripMovement getTripMovement(String rickshawId) {
        return tripMovements.get(rickshawId);
    }

    public record TripMovement(String fromLocationId, String toLocationId, long startedAtMillis) {
    }

    /** Advances one road in the active itinerary; the driver screen invokes this off the EDT. */
    public synchronized String advanceTripOneStep(String rickshawId) {
        Rickshaw rickshaw = requireRickshaw(rickshawId);
        if (!rickshaw.hasActiveTrip() || rickshaw.getItinerary().isEmpty()) {
            return rickshaw.getLocationId();
        }
        List<RideRequest> activeRides = getActivePassengerRides(rickshawId);
        boolean acceptedBookingInProgress = activeRides.stream()
                .anyMatch(ride -> ride.getStatus() == RideStatus.MATCHED
                        || ride.getStatus() == RideStatus.IN_PROGRESS);
        if (!acceptedBookingInProgress) {
            return rickshaw.getLocationId();
        }
        if (activeRides.stream().anyMatch(ride -> ride.getStatus() == RideStatus.ARRIVED_AT_DESTINATION)) {
            return rickshaw.getLocationId();
        }
        String fromLocationId = rickshaw.getLocationId();
        TripStop stop = rickshaw.getItinerary().get(0);
        boolean passengerWaitingAtPickup = activeRides.stream()
                .anyMatch(ride -> ride.getStatus() == RideStatus.ARRIVED_AT_PICKUP);
        if (passengerWaitingAtPickup
                && (stop.type() != TripStop.Type.DROPOFF || !rickshaw.getLocationId().equals(stop.locationId()))) {
            return rickshaw.getLocationId();
        }
        if (rickshaw.getLocationId().equals(stop.locationId())) {
            processTripStop(rickshaw, stop);
        } else {
            RouteResult leg = routeService.findRoute(rickshaw.getLocationId(), stop.locationId());
            if (!leg.isFound() || leg.getPath().size() < 2) {
                throw new IllegalStateException("The active trip route cannot reach its next stop.");
            }
            rickshaw.setLocationId(leg.getPath().get(1));
            if (rickshaw.getLocationId().equals(stop.locationId())) {
                processTripStop(rickshaw, stop);
            }
        }
        save();
        if (!fromLocationId.equals(rickshaw.getLocationId())) {
            tripMovements.put(rickshawId, new TripMovement(
                    fromLocationId, rickshaw.getLocationId(), System.currentTimeMillis()));
        }
        return rickshaw.getLocationId();
    }

    private void processTripStop(Rickshaw rickshaw, TripStop stop) {
        if (stop.type() == TripStop.Type.DESTINATION
                && hasActiveRideForRickshaw(rickshaw.getId())) {
            return;
        }
        RideRequest ride = stop.rideId() == 0 ? null : requireRide(stop.rideId());
        if (stop.type() == TripStop.Type.PICKUP && ride != null
                && ride.getStatus() == RideStatus.MATCHED) {
            ride.moveTo(RideStatus.ARRIVED_AT_PICKUP);
            addRideUpdate(ride, "Rickshaw arrived at pickup: "
                    + routeService.getLocationName(stop.locationId()) + ". Driver is waiting for the passenger.");
            rickshaw.setItinerary(rickshaw.getItinerary().subList(1, rickshaw.getItinerary().size()));
            return;
        }
        if (stop.type() == TripStop.Type.DROPOFF && ride != null
                && ride.getStatus() == RideStatus.IN_PROGRESS) {
            ride.moveTo(RideStatus.ARRIVED_AT_DESTINATION);
            addRideUpdate(ride, "Reached destination: " + routeService.getLocationName(stop.locationId())
                    + ". Waiting for the driver to confirm trip completion.");
        }
        rickshaw.setItinerary(rickshaw.getItinerary().subList(1, rickshaw.getItinerary().size()));
        if (stop.type() == TripStop.Type.DESTINATION) {
            if (hasActiveRideForRickshaw(rickshaw.getId()) || !getAssignedRequests(rickshaw.getId()).isEmpty()) {
                throw new IllegalStateException("The trip reached its destination with active passenger bookings.");
            }
            rickshaw.finishTrip();
        }
    }

    public synchronized void confirmPassengerPickup(int rideId, String rickshawId) {
        RideRequest ride = requireRide(rideId);
        if (!ride.isSharedTrip() || !ride.getRickshawId().equals(rickshawId)
                || ride.getStatus() != RideStatus.ARRIVED_AT_PICKUP) {
            throw new IllegalStateException("The passenger is not waiting at this rickshaw's pickup.");
        }
        ride.moveTo(RideStatus.IN_PROGRESS);
        addRideUpdate(ride, "Passenger onboard. Trip to destination is in progress.");
        save();
    }

    /** Kept for compatibility with callers of the previous service API. */
    public synchronized void startPassengerTrip(int rideId, String rickshawId) {
        confirmPassengerPickup(rideId, rickshawId);
    }

    public synchronized String completePassengerTrip(int rideId, String rickshawId) {
        RideRequest ride = requireRide(rideId);
        Rickshaw rickshaw = requireRickshaw(rickshawId);
        if (!ride.isSharedTrip() || !ride.getRickshawId().equals(rickshawId)
                || ride.getStatus() != RideStatus.ARRIVED_AT_DESTINATION
                || !rickshaw.getLocationId().equals(ride.getDestinationId())) {
            throw new IllegalStateException("The rickshaw must reach this passenger's destination first.");
        }
        ride.moveTo(RideStatus.COMPLETED);
        rickshaw.setAvailableSeats(Math.min(rickshaw.getTotalSeats(),
                rickshaw.getAvailableSeats() + ride.getRequestedSeats()));
        addRideUpdate(ride, "Trip completed at " + routeService.getLocationName(ride.getDestinationId()) + ".");
        save();
        return "Ride #" + rideId + " completed.";
    }

    public record SeatAvailability(
            int totalSeats, int bookedSeats, int occupiedSeats,
            int availableSeats, int passengerBookings) {
    }

    private boolean hasActiveRideForRickshaw(String rickshawId) {
        return rides.values().stream().anyMatch(ride -> ride.getRickshawId().equals(rickshawId)
                && ride.getStatus().isActive());
    }

    /**
     * Takes the next request from the queue (by the active policy) and gives it
     * the nearest available rickshaw. If none is available the request stays
     * at the front of the queue.
     */
    public synchronized String matchNextRequest() {
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
        addRideUpdate(next, "Request matched with " + rickshaw.getDriverName() + " (" + rickshaw.getId() + ").");
        save();
        return String.format(Locale.ROOT, "Request #%d (%s) matched with %s (%s), %.1f m from the pickup.",
                next.getId(), next.getPassengerName(), rickshaw.getId(), rickshaw.getDriverName(),
                match.getDistanceToPickupMeters());
    }

    public synchronized String startRide(int rideId) {
        RideRequest ride = requireRide(rideId);
        if (ride.isSharedTrip()) {
            throw new IllegalStateException("Shared passengers start after the rickshaw reaches their pickup.");
        }
        ride.moveTo(RideStatus.IN_PROGRESS);
        addRideUpdate(ride, "Ride is now in progress.");
        save();
        return "Ride #" + rideId + " is now in progress.";
    }

    /** Completes the ride; the rickshaw becomes available at the destination. */
    public synchronized String completeRide(int rideId) {
        RideRequest ride = requireRide(rideId);
        if (ride.isSharedTrip()) {
            throw new IllegalStateException("Shared rides complete through the trip simulator.");
        }
        ride.moveTo(RideStatus.COMPLETED);
        Rickshaw rickshaw = rickshaws.get(ride.getRickshawId());
        rickshaw.setAvailable(true);
        rickshaw.setLocationId(ride.getDestinationId());
        addRideUpdate(ride, "Ride completed.");
        save();
        return "Ride #" + rideId + " completed. " + rickshaw.getId() + " is available at "
                + routeService.getLocationName(ride.getDestinationId()) + ".";
    }

    /** Cancels a PENDING or MATCHED ride and frees its rickshaw if it had one. */
    public synchronized String cancelRide(int rideId) {
        RideRequest ride = requireRide(rideId);
        RideStatus before = ride.getStatus();
        ride.moveTo(RideStatus.CANCELLED);
        if (before == RideStatus.PENDING) {
            pendingQueue.remove(ride);
        } else if (ride.isSharedTrip()) {
            Rickshaw rickshaw = requireRickshaw(ride.getRickshawId());
            if (before == RideStatus.MATCHED || before == RideStatus.ARRIVED_AT_PICKUP) {
                rickshaw.setAvailableSeats(Math.min(rickshaw.getTotalSeats(),
                        rickshaw.getAvailableSeats() + ride.getRequestedSeats()));
            }
            rickshaw.setItinerary(rickshaw.getItinerary().stream()
                    .filter(stop -> stop.rideId() != ride.getId()).toList());
        } else {
            rickshaws.get(ride.getRickshawId()).setAvailable(true);
        }
        addRideUpdate(ride, "Ride cancelled.");
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

    private Rickshaw requireRickshaw(String rickshawId) {
        Rickshaw rickshaw = rickshaws.get(rickshawId);
        if (rickshaw == null) {
            throw new IllegalArgumentException("Rickshaw '" + rickshawId + "' does not exist.");
        }
        return rickshaw;
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
    public synchronized int countAvailableRickshaws() {
        int count = 0;
        for (Rickshaw r : rickshaws.values()) {
            if (r.isAvailable()) {
                count++;
            }
        }
        return count;
    }
}

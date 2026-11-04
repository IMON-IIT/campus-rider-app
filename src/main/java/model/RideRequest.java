package model;

import java.util.ArrayList;
import java.util.List;

/**
 * A passenger's ride request. The id grows by one for every new request,
 * so a smaller id always means an earlier request.
 */
public class RideRequest {
    private final int id;
    private final String passengerName;
    private final PassengerType passengerType;
    private final String pickupId;
    private final String destinationId;
    private final double distanceMeters;
    private RideStatus status;
    private String rickshawId; // empty until a driver is offered the request
    private final List<String> rejectedRickshawIds = new ArrayList<>();
    private final String passengerMobileNumber;
    private final int requestedSeats;
    private final boolean sharedTrip;

    public RideRequest(int id, String passengerName, PassengerType passengerType, String pickupId,
                       String destinationId, double distanceMeters, RideStatus status, String rickshawId) {
        this(id, passengerName, passengerType, pickupId, destinationId, distanceMeters, status, rickshawId,
                List.of(), "", 1, false);
    }

    public RideRequest(int id, String passengerName, PassengerType passengerType, String pickupId,
                       String destinationId, double distanceMeters, RideStatus status, String rickshawId,
                       List<String> rejectedRickshawIds) {
        this(id, passengerName, passengerType, pickupId, destinationId, distanceMeters, status, rickshawId,
                rejectedRickshawIds, "", 1, false);
    }

    public RideRequest(int id, String passengerName, PassengerType passengerType, String pickupId,
                       String destinationId, double distanceMeters, RideStatus status, String rickshawId,
                       List<String> rejectedRickshawIds, String passengerMobileNumber) {
        this(id, passengerName, passengerType, pickupId, destinationId, distanceMeters, status, rickshawId,
                rejectedRickshawIds, passengerMobileNumber, 1, false);
    }

    public RideRequest(int id, String passengerName, PassengerType passengerType, String pickupId,
                       String destinationId, double distanceMeters, RideStatus status, String rickshawId,
                       List<String> rejectedRickshawIds, String passengerMobileNumber,
                       int requestedSeats, boolean sharedTrip) {
        this.id = id;
        this.passengerName = passengerName;
        this.passengerType = passengerType;
        this.pickupId = pickupId;
        this.destinationId = destinationId;
        this.distanceMeters = distanceMeters;
        this.status = status;
        this.rickshawId = rickshawId == null ? "" : rickshawId;
        this.rejectedRickshawIds.addAll(rejectedRickshawIds);
        this.passengerMobileNumber = passengerMobileNumber == null ? "" : passengerMobileNumber;
        if (requestedSeats < 1) {
            throw new IllegalArgumentException("A ride must request at least one seat.");
        }
        this.requestedSeats = requestedSeats;
        this.sharedTrip = sharedTrip;
    }

    public int getId() {
        return id;
    }

    public String getPassengerName() {
        return passengerName;
    }

    public PassengerType getPassengerType() {
        return passengerType;
    }

    public String getPickupId() {
        return pickupId;
    }

    public String getDestinationId() {
        return destinationId;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public RideStatus getStatus() {
        return status;
    }

    public String getRickshawId() {
        return rickshawId;
    }

    public String getPassengerMobileNumber() {
        return passengerMobileNumber;
    }

    public int getRequestedSeats() {
        return requestedSeats;
    }

    public boolean isSharedTrip() {
        return sharedTrip;
    }

    public void setRickshawId(String rickshawId) {
        this.rickshawId = rickshawId == null ? "" : rickshawId;
    }

    public List<String> getRejectedRickshawIds() {
        return List.copyOf(rejectedRickshawIds);
    }

    public void addRejectedRickshaw(String rejectedRickshawId) {
        if (rejectedRickshawId != null && !rejectedRickshawId.isBlank()
                && !rejectedRickshawIds.contains(rejectedRickshawId)) {
            rejectedRickshawIds.add(rejectedRickshawId);
        }
    }

    /** Changes the status only if the lifecycle allows it. */
    public void moveTo(RideStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Ride #" + id + " cannot change from " + status + " to " + next);
        }
        status = next;
    }
}

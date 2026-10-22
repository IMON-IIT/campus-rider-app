package model;

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
    private String rickshawId; // empty until the request is matched

    public RideRequest(int id, String passengerName, PassengerType passengerType, String pickupId,
                       String destinationId, double distanceMeters, RideStatus status, String rickshawId) {
        this.id = id;
        this.passengerName = passengerName;
        this.passengerType = passengerType;
        this.pickupId = pickupId;
        this.destinationId = destinationId;
        this.distanceMeters = distanceMeters;
        this.status = status;
        this.rickshawId = rickshawId == null ? "" : rickshawId;
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

    public void setRickshawId(String rickshawId) {
        this.rickshawId = rickshawId == null ? "" : rickshawId;
    }

    /** Changes the status only if the lifecycle allows it. */
    public void moveTo(RideStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Ride #" + id + " cannot change from " + status + " to " + next);
        }
        status = next;
    }
}

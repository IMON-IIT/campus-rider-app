package model;

/** A pickup, drop-off, or the driver's final destination on an active trip. */
public record TripStop(int rideId, String locationId, Type type) {
    public enum Type {
        PICKUP,
        DROPOFF,
        DESTINATION
    }

    public TripStop {
        if (rideId < -1 || locationId == null || locationId.isBlank() || type == null) {
            throw new IllegalArgumentException("Trip stop details are invalid.");
        }
        if (type == Type.DESTINATION && rideId != 0 || type != Type.DESTINATION && rideId == 0) {
            throw new IllegalArgumentException("Only a driver's destination can use ride id zero.");
        }
    }
}

package model;

/**
 * Ride lifecycle:
 * PENDING -> OFFERED -> MATCHED -> IN_PROGRESS -> COMPLETED
 * Shared trips: MATCHED -> ARRIVED_AT_PICKUP -> IN_PROGRESS
 *               -> ARRIVED_AT_DESTINATION -> COMPLETED
 * PENDING or OFFERED or MATCHED -> CANCELLED
 * PENDING -> NO_DRIVER_AVAILABLE
 */
public enum RideStatus {
    PENDING,
    OFFERED,
    MATCHED,
    ARRIVED_AT_PICKUP,
    IN_PROGRESS,
    ARRIVED_AT_DESTINATION,
    COMPLETED,
    CANCELLED,
    NO_DRIVER_AVAILABLE;

    public boolean canMoveTo(RideStatus next) {
        switch (this) {
            case PENDING:
                return next == OFFERED || next == MATCHED || next == CANCELLED
                        || next == NO_DRIVER_AVAILABLE;
            case OFFERED:
                return next == PENDING || next == MATCHED || next == CANCELLED;
            case MATCHED:
                return next == ARRIVED_AT_PICKUP || next == IN_PROGRESS || next == CANCELLED;
            case ARRIVED_AT_PICKUP:
                return next == IN_PROGRESS || next == CANCELLED;
            case IN_PROGRESS:
                return next == ARRIVED_AT_DESTINATION || next == COMPLETED;
            case ARRIVED_AT_DESTINATION:
                return next == COMPLETED;
            default:
                return false;
        }
    }

    /** True while the ride is holding or reserving a rickshaw. */
    public boolean isActive() {
        return this == OFFERED || this == MATCHED || this == ARRIVED_AT_PICKUP || this == IN_PROGRESS
                || this == ARRIVED_AT_DESTINATION;
    }
}

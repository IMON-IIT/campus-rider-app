package model;

/**
 * Ride lifecycle:
 * PENDING -> MATCHED -> IN_PROGRESS -> COMPLETED
 * PENDING or MATCHED -> CANCELLED
 */
public enum RideStatus {
    PENDING,
    MATCHED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    public boolean canMoveTo(RideStatus next) {
        switch (this) {
            case PENDING:
                return next == MATCHED || next == CANCELLED;
            case MATCHED:
                return next == IN_PROGRESS || next == CANCELLED;
            case IN_PROGRESS:
                return next == COMPLETED;
            default:
                return false; // COMPLETED and CANCELLED are final
        }
    }

    /** True while the ride is holding a rickshaw. */
    public boolean isActive() {
        return this == MATCHED || this == IN_PROGRESS;
    }
}

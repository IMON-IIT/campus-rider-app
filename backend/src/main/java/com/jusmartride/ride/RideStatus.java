package com.jusmartride.ride;

public enum RideStatus {
    REQUESTED,
    ACCEPTED,
    ARRIVING,
    PICKUP_REACHED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    REJECTED,
    EXPIRED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == REJECTED || this == EXPIRED;
    }

    public boolean canTransitionTo(RideStatus next) {
        if (this == next) return true; // Idempotent
        if (this.isTerminal()) return false;

        return switch (this) {
            case REQUESTED -> next == ACCEPTED || next == CANCELLED || next == REJECTED || next == EXPIRED;
            case ACCEPTED -> next == ARRIVING || next == PICKUP_REACHED || next == CANCELLED;
            case ARRIVING -> next == PICKUP_REACHED || next == IN_PROGRESS || next == CANCELLED;
            case PICKUP_REACHED -> next == IN_PROGRESS || next == CANCELLED;
            case IN_PROGRESS -> next == COMPLETED || next == CANCELLED;
            default -> false;
        };
    }
}

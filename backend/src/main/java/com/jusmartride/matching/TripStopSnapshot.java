package com.jusmartride.matching;

import java.io.Serializable;

/**
 * Immutable snapshot of a planned stop along an active rickshaw route.
 */
public class TripStopSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String locationId;
    private final int stopOrder;
    private final int boardingCount;
    private final int alightingCount;
    private final String passengerRequestId;

    public TripStopSnapshot(String locationId, int stopOrder, int boardingCount,
                            int alightingCount, String passengerRequestId) {
        this.locationId = locationId;
        this.stopOrder = stopOrder;
        this.boardingCount = boardingCount;
        this.alightingCount = alightingCount;
        this.passengerRequestId = passengerRequestId;
    }

    public String getLocationId() {
        return locationId;
    }

    public int getStopOrder() {
        return stopOrder;
    }

    public int getBoardingCount() {
        return boardingCount;
    }

    public int getAlightingCount() {
        return alightingCount;
    }

    public String getPassengerRequestId() {
        return passengerRequestId;
    }
}

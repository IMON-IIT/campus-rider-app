package com.jusmartride.ride;

import com.jusmartride.campus.CampusLocation;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "trip_stops")
public class TripStop {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private DriverTrip driverTrip;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "location_id", nullable = false)
    private CampusLocation location;

    @Column(name = "stop_order", nullable = false)
    private int stopOrder;

    @Column(name = "boarding_count", nullable = false)
    private int boardingCount = 0;

    @Column(name = "alighting_count", nullable = false)
    private int alightingCount = 0;

    @Column(name = "passenger_request_id", length = 36)
    private String passengerRequestId;

    @Column(nullable = false, length = 50)
    private String status = "PENDING"; // PENDING, VISITED, SKIPPED

    public TripStop() {
    }

    public TripStop(DriverTrip driverTrip, CampusLocation location, int stopOrder,
                    int boardingCount, int alightingCount, String passengerRequestId) {
        this.id = UUID.randomUUID().toString();
        this.driverTrip = driverTrip;
        this.location = location;
        this.stopOrder = stopOrder;
        this.boardingCount = boardingCount;
        this.alightingCount = alightingCount;
        this.passengerRequestId = passengerRequestId;
        this.status = "PENDING";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public DriverTrip getDriverTrip() {
        return driverTrip;
    }

    public void setDriverTrip(DriverTrip driverTrip) {
        this.driverTrip = driverTrip;
    }

    public CampusLocation getLocation() {
        return location;
    }

    public void setLocation(CampusLocation location) {
        this.location = location;
    }

    public int getStopOrder() {
        return stopOrder;
    }

    public void setStopOrder(int stopOrder) {
        this.stopOrder = stopOrder;
    }

    public int getBoardingCount() {
        return boardingCount;
    }

    public void setBoardingCount(int boardingCount) {
        this.boardingCount = boardingCount;
    }

    public int getAlightingCount() {
        return alightingCount;
    }

    public void setAlightingCount(int alightingCount) {
        this.alightingCount = alightingCount;
    }

    public String getPassengerRequestId() {
        return passengerRequestId;
    }

    public void setPassengerRequestId(String passengerRequestId) {
        this.passengerRequestId = passengerRequestId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

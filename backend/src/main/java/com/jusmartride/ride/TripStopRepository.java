package com.jusmartride.ride;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TripStopRepository extends JpaRepository<TripStop, String> {
    List<TripStop> findByDriverTripOrderByStopOrderAsc(DriverTrip driverTrip);
    List<TripStop> findByPassengerRequestId(String passengerRequestId);
}

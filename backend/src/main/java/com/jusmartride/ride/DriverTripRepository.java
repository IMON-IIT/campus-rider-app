package com.jusmartride.ride;

import com.jusmartride.user.User;
import com.jusmartride.vehicle.Vehicle;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriverTripRepository extends JpaRepository<DriverTrip, String> {
    Optional<DriverTrip> findByDriverAndStatusIn(User driver, List<String> statuses);
    Optional<DriverTrip> findByVehicleAndStatusIn(Vehicle vehicle, List<String> statuses);
    List<DriverTrip> findByStatusIn(List<String> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM DriverTrip t WHERE t.id = :id")
    Optional<DriverTrip> findByIdWithLock(@Param("id") String id);
}

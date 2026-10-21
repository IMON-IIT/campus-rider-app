package com.jusmartride.ride;

import com.jusmartride.user.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RideRequestRepository extends JpaRepository<RideRequest, String> {
    List<RideRequest> findByPassengerOrderByRequestedAtDesc(User passenger);
    List<RideRequest> findByDriverOrderByRequestedAtDesc(User driver);
    List<RideRequest> findByDriverTrip(DriverTrip driverTrip);
    List<RideRequest> findByStatus(RideStatus status);
    List<RideRequest> findByDriverAndStatusIn(User driver, List<RideStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RideRequest r WHERE r.id = :id")
    Optional<RideRequest> findByIdWithLock(@Param("id") String id);
}

package com.jusmartride.campus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampusLocationRepository extends JpaRepository<CampusLocation, String> {
    List<CampusLocation> findByRickshawStandTrue();
}

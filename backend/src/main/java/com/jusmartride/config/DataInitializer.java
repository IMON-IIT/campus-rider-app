package com.jusmartride.config;

import com.jusmartride.campus.CampusLocation;
import com.jusmartride.campus.CampusLocationRepository;
import com.jusmartride.user.Role;
import com.jusmartride.user.User;
import com.jusmartride.user.UserRepository;
import com.jusmartride.vehicle.Vehicle;
import com.jusmartride.vehicle.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final CampusLocationRepository campusLocationRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository,
                           VehicleRepository vehicleRepository,
                           CampusLocationRepository campusLocationRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
        this.campusLocationRepository = campusLocationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        log.info("Checking and seeding default demo users and vehicles...");

        if (userRepository.findByEmail("passenger@juniv.edu").isEmpty()) {
            User passenger = new User(
                    "passenger@juniv.edu",
                    passwordEncoder.encode("password123"),
                    "Rafiqul Islam (Student)",
                    "+8801711000001",
                    Role.ROLE_PASSENGER
            );
            userRepository.save(passenger);
            log.info("Created demo passenger: passenger@juniv.edu");
        }

        seedDriver("driver1@juniv.edu", "Abdul Karim Mama", "+8801811000001", "JU-RICK-101", "dairy_gate", 3);
        seedDriver("driver2@juniv.edu", "Mokbul Hossain", "+8801811000002", "JU-RICK-102", "bottola", 3);
        seedDriver("driver3@juniv.edu", "Salam Mia", "+8801811000003", "JU-RICK-103", "shaheed_minar", 3);
    }

    private void seedDriver(String email, String fullName, String phone, String rickshawNum, String locationId, int capacity) {
        User driver = userRepository.findByEmail(email).orElseGet(() -> {
            User u = new User(email, passwordEncoder.encode("password123"), fullName, phone, Role.ROLE_DRIVER);
            return userRepository.save(u);
        });

        if (vehicleRepository.findByDriverId(driver.getId()).isEmpty()) {
            Vehicle vehicle = new Vehicle(driver, rickshawNum, capacity);
            vehicle.setOnline(true);
            vehicle.setLocationSource("SEED");
            vehicle.setLastLocationUpdate(Instant.now());

            campusLocationRepository.findById(locationId).ifPresent(loc -> {
                vehicle.setCurrentLocation(loc);
                vehicle.setCurrentLatitude(loc.getLatitude());
                vehicle.setCurrentLongitude(loc.getLongitude());
            });

            vehicleRepository.save(vehicle);
            log.info("Created demo driver {} with vehicle {}", email, rickshawNum);
        }
    }
}

package com.jusmartride.auth;

import com.jusmartride.security.JwtTokenProvider;
import com.jusmartride.user.Role;
import com.jusmartride.user.User;
import com.jusmartride.user.UserRepository;
import com.jusmartride.vehicle.Vehicle;
import com.jusmartride.vehicle.VehicleRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserRepository userRepository, VehicleRepository vehicleRepository,
                       PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail().toLowerCase().trim())) {
            throw new IllegalArgumentException("User with email " + request.getEmail() + " already exists");
        }

        Role role = Role.fromString(request.getRole());
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        User user = new User(
                request.getEmail().toLowerCase().trim(),
                encodedPassword,
                request.getFullName().trim(),
                request.getPhone() != null ? request.getPhone().trim() : null,
                role
        );
        user = userRepository.save(user);

        Vehicle vehicle = null;
        if (role == Role.ROLE_DRIVER) {
            String plate = request.getRickshawNumber() != null && !request.getRickshawNumber().isBlank()
                    ? request.getRickshawNumber().trim()
                    : "JU-RICK-" + (100 + (int)(Math.random() * 900));
            int capacity = (request.getCapacity() != null && request.getCapacity() > 0) ? request.getCapacity() : 3;

            vehicle = new Vehicle(user, plate, capacity);
            vehicle = vehicleRepository.save(vehicle);
        }

        String token = jwtTokenProvider.generateToken(user);
        return buildAuthResponse(user, token, vehicle);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        Vehicle vehicle = null;
        if (user.getRole() == Role.ROLE_DRIVER) {
            vehicle = vehicleRepository.findByDriver(user).orElse(null);
        }

        String token = jwtTokenProvider.generateToken(user);
        return buildAuthResponse(user, token, vehicle);
    }

    @Transactional(readOnly = true)
    public AuthResponse getCurrentUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        Vehicle vehicle = null;
        if (user.getRole() == Role.ROLE_DRIVER) {
            vehicle = vehicleRepository.findByDriver(user).orElse(null);
        }

        String token = jwtTokenProvider.generateToken(user);
        return buildAuthResponse(user, token, vehicle);
    }

    private AuthResponse buildAuthResponse(User user, String token, Vehicle vehicle) {
        String vehicleId = vehicle != null ? vehicle.getId() : null;
        String rickshawNumber = vehicle != null ? vehicle.getRickshawNumber() : null;
        Integer capacity = vehicle != null ? vehicle.getCapacity() : null;
        Boolean isOnline = vehicle != null ? vehicle.isOnline() : null;

        return new AuthResponse(
                token,
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getRole(),
                vehicleId,
                rickshawNumber,
                capacity,
                isOnline
        );
    }
}

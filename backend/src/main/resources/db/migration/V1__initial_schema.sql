-- V1 Initial Database Schema for JU Smart Ride
-- Jahangirnagar University Campus Ride-Sharing System

CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS campus_locations (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    bengali_name VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    rickshaw_stand BOOLEAN NOT NULL DEFAULT FALSE,
    description TEXT
);

CREATE TABLE IF NOT EXISTS vehicles (
    id VARCHAR(36) PRIMARY KEY,
    driver_id VARCHAR(36) UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rickshaw_number VARCHAR(100) UNIQUE NOT NULL,
    capacity INT NOT NULL DEFAULT 3,
    is_online BOOLEAN NOT NULL DEFAULT FALSE,
    current_location_id VARCHAR(50) REFERENCES campus_locations(id),
    current_latitude DOUBLE PRECISION,
    current_longitude DOUBLE PRECISION,
    last_location_update TIMESTAMP,
    location_source VARCHAR(50) NOT NULL DEFAULT 'UNKNOWN'
);

CREATE TABLE IF NOT EXISTS driver_trips (
    id VARCHAR(36) PRIMARY KEY,
    vehicle_id VARCHAR(36) NOT NULL REFERENCES vehicles(id),
    driver_id VARCHAR(36) NOT NULL REFERENCES users(id),
    status VARCHAR(50) NOT NULL DEFAULT 'AVAILABLE',
    current_location_id VARCHAR(50) REFERENCES campus_locations(id),
    occupied_seats INT NOT NULL DEFAULT 0,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    total_distance_meters DOUBLE PRECISION DEFAULT 0.0
);

CREATE TABLE IF NOT EXISTS trip_stops (
    id VARCHAR(36) PRIMARY KEY,
    trip_id VARCHAR(36) NOT NULL REFERENCES driver_trips(id) ON DELETE CASCADE,
    location_id VARCHAR(50) NOT NULL REFERENCES campus_locations(id),
    stop_order INT NOT NULL,
    boarding_count INT NOT NULL DEFAULT 0,
    alighting_count INT NOT NULL DEFAULT 0,
    passenger_request_id VARCHAR(36),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING'
);

CREATE TABLE IF NOT EXISTS ride_requests (
    id VARCHAR(36) PRIMARY KEY,
    passenger_id VARCHAR(36) NOT NULL REFERENCES users(id),
    driver_id VARCHAR(36) REFERENCES users(id),
    vehicle_id VARCHAR(36) REFERENCES vehicles(id),
    driver_trip_id VARCHAR(36) REFERENCES driver_trips(id),
    pickup_location_id VARCHAR(50) NOT NULL REFERENCES campus_locations(id),
    destination_location_id VARCHAR(50) NOT NULL REFERENCES campus_locations(id),
    passenger_count INT NOT NULL DEFAULT 1,
    status VARCHAR(50) NOT NULL DEFAULT 'REQUESTED',
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_at TIMESTAMP,
    completed_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    detour_ratio DOUBLE PRECISION DEFAULT 0.0,
    estimated_fare_bdt DOUBLE PRECISION DEFAULT 20.0,
    rejection_reason TEXT
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_vehicles_online ON vehicles(is_online);
CREATE INDEX IF NOT EXISTS idx_driver_trips_status ON driver_trips(status);
CREATE INDEX IF NOT EXISTS idx_ride_requests_passenger ON ride_requests(passenger_id);
CREATE INDEX IF NOT EXISTS idx_ride_requests_driver ON ride_requests(driver_id);
CREATE INDEX IF NOT EXISTS idx_ride_requests_status ON ride_requests(status);

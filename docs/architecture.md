# JU Smart Ride: Architecture & Technical Specification

## 1. Executive Summary
**JU Smart Ride** is a campus transportation and multi-passenger ride-sharing ecosystem engineered for Jahangirnagar University (JU), Savar, Dhaka, Bangladesh. The system coordinates rickshaws with passenger requests, featuring real Java Data Structures and Algorithms (DSA) for route optimization, fair queuing, detour-constrained ride sharing, and real-time tracking.

## 2. System Architecture
The application is structured into three clean decoupled layers:

```
┌────────────────────────────────────────────────────────┐
│     Mobile Client (React Native + Expo Router v3)     │
│   - Leaflet/OSM Campus Map                             │
│   - Trie Prefix Autocomplete                           │
│   - Passenger Booking & Live Tracking                  │
│   - Driver Stand Status & Ride Management             │
└──────────────────────────┬─────────────────────────────┘
                           │ HTTP REST / STOMP WebSocket
                           ▼
┌────────────────────────────────────────────────────────┐
│       Backend Server (Java 17 + Spring Boot 3)         │
│   - Security & JWT Authentication                      │
│   - Dijkstra Shortest Path Engine                      │
│   - Trie Location Search Index                         │
│   - Fair Queueing Dispatcher (PendingRideQueue)        │
│   - Deterministic Ride-Matching Engine                 │
│   - Pessimistic Seat Locking (Concurrency Safe)        │
└──────────────────────────┬─────────────────────────────┘
                           │ JDBC / Flyway Migrations
                           ▼
┌────────────────────────────────────────────────────────┐
│             PostgreSQL 16 Database                     │
│   - Users & Roles (Passenger, Driver, Admin)           │
│   - Vehicles & Stand Status                            │
│   - Driver Trips & Scheduled Stops                     │
│   - Ride Requests with State Machine Lifecycle         │
└────────────────────────────────────────────────────────┘
```

## 3. Concurrency & Overbooking Prevention
- Rickshaws have a hard seat capacity limit of 3 passengers.
- Ride request acceptance uses database row-level locking (`PESSIMISTIC_WRITE`) on both the `Vehicle` and `RideRequest` records.
- Overbooking cannot occur: even during simultaneous acceptance requests, transactions serialize and reject any excess passenger counts.

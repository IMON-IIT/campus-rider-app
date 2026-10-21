# JU Smart Ride: Java DSA Campus Ride-Sharing & Route Optimizer

[![Java](https://img.shields.io/badge/Java-17-blue.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![React Native](https://img.shields.io/badge/React%20Native-0.74-informational.svg)](https://reactnative.dev/)
[![Expo](https://img.shields.io/badge/Expo-SDK%2051-black.svg)](https://expo.dev/)
[![Tests](https://img.shields.io/badge/JUnit%205-19%20Passing-success.svg)](https://junit.org/junit5/)

**JU Smart Ride** is a production-grade, campus-focused ride-sharing mobile application engineered specifically for **Jahangirnagar University (JU)** in Savar, Dhaka, Bangladesh. The platform connects university students, faculty, and authorized visitors with licensed campus rickshaws through real Data Structures and Algorithms (DSA).

---

## 🚀 Key Features

1. **Deterministic Java DSA Shortest-Path Engine:**
   - Real Dijkstra shortest path implementation running on the Java backend.
   - Computes turn-by-turn road paths, distance in meters, and travel times across 25 verified JU campus landmarks and 66 road segments.
2. **Instant Trie Search & Autocomplete:**
   - High-performance prefix tree indexing Bengali and English campus landmarks, halls, and academic buildings.
3. **Detour-Constrained Multi-Passenger Ride Sharing:**
   - Evaluates shared rides dynamically: ensures new passengers can join existing routes without exceeding vehicle capacity (max 3 seats) or exceeding strict detour ratio constraints ($\le 45\%$).
4. **Pessimistic Seat Concurrency & Overbooking Prevention:**
   - Database row-level locking (`PESSIMISTIC_WRITE`) guarantees zero overbooking even under simultaneous concurrent ride requests.
5. **Interactive Campus Map & Live Tracking:**
   - Built-in OpenStreetMap Leaflet visualization displaying real campus landmark coordinates, rickshaw stands, route polylines, and driver locations.
6. **Role-Based Experience:**
   - **Student / Passenger:** Search routes, view candidate shared rides, submit ride requests, track live trip progress.
   - **Rickshaw Driver:** Set stand availability, view incoming requests, accept/reject rides, update trip status (`ARRIVING`, `IN_PROGRESS`, `COMPLETED`).

---

## 📱 Mobile Device Execution (ADB)

To launch the full stack and run directly on your connected Android device:

```bash
# 1. Ensure your Android device is connected via USB with USB Debugging enabled:
adb devices

# 2. Reverse port forward backend (8080) and Metro bundler (8081):
adb reverse tcp:8080 tcp:8080
adb reverse tcp:8081 tcp:8081

# 3. Start PostgreSQL and Spring Boot backend:
docker start ju-postgres || docker run -d --name ju-postgres -e POSTGRES_DB=jusmartride -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:16-alpine
java -XX:TieredStopAtLevel=1 -jar backend/target/ju-smart-ride-backend-1.0.0-SNAPSHOT.jar

# 4. Start Expo mobile client on Android device:
cd mobile
pnpm run android
```

Or run the all-in-one script:
```bash
./scripts/run-dev.sh
```

---

## 📍 How a Ride Works (GPS flow)

1. **Passenger** opens the app → it asks for location permission → the map shows a blue dot (you) and every online rickshaw within 3 km.
2. Pickup is auto-set to the campus landmark nearest your GPS. Choose a destination (search, or tap a landmark on the map) → Dijkstra route, distance, time and fare appear.
3. **Tap a 🛺** (on the map or in the list) to request that specific driver, or send to **any nearby rickshaw** (first driver to accept gets it).
4. **Driver** goes online → the phone streams live GPS to the server every ~10 m / 5 s → sees requests with distance to pickup → Accept / Reject.
5. Passenger's ride screen tracks the rickshaw live (distance + ETA) through `ACCEPTED → IN_PROGRESS → COMPLETED`.

If a phone is more than 2.5 km from JU campus (e.g. testing from home), the passenger uses a demo position at Dairy Gate and the driver picks a stand manually, so the campus map still works.

## 📱 Testing With Only One Phone

A ride needs a passenger and a driver at the same time. Options:

- **Phone + driver simulator (easiest):** use the phone as the passenger and run a fake driver from the laptop:
  ```bash
  python3 scripts/simulate-driver.py            # driver2 (Mokbul) waits at Bottola
  ```
  Request a ride on the phone → the script accepts it, "drives" to you sending GPS updates, starts and completes the trip. Watch the rickshaw move on the phone.
- **Phone + Android emulator:** run the passenger on one and the driver on the other (both point to the same backend).
- **One phone, switch accounts:** request as passenger → Logout → log in as driver → Accept → Logout → log in as passenger to see the accepted ride.

## 👥 Default Demo Credentials

| Role | Email | Password | Details |
| :--- | :--- | :--- | :--- |
| **Passenger** | `passenger@juniv.edu` | `password123` | Rafiqul Islam (Student) |
| **Driver 1** | `driver1@juniv.edu` | `password123` | Abdul Karim Mama (`JU-RICK-101`, Dairy Gate Stand) |
| **Driver 2** | `driver2@juniv.edu` | `password123` | Mokbul Hossain (`JU-RICK-102`, Bottola Stand) |
| **Driver 3** | `driver3@juniv.edu` | `password123` | Salam Mia (`JU-RICK-103`, Shaheed Minar Stand) |

*(Quick demo buttons are also available directly on the mobile app's Sign In screen for 1-tap testing.)*

---

## 🧪 Automated Tests

Run backend algorithm and lifecycle tests:

```bash
mvn -f backend/pom.xml test
```

- **Algorithms Suite:** Dijkstra shortest path, Trie autocomplete, Graph loader.
- **Matching Suite:** Ride matching engine, Detour calculator, Candidate scorer.
- **Queue Suite:** FIFO pending request dispatcher.

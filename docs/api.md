# JU Smart Ride: REST API & WebSocket Documentation

Base URL: `http://localhost:8080/api/v1`

## Authentication (`/api/v1/auth`)
- `POST /auth/register`: Create user account (PASSENGER, DRIVER).
- `POST /auth/login`: Authenticate with email & password, returns JWT token.

## Campus Road Network (`/api/v1/campus`)
- `GET /campus/locations`: Returns all 25 campus vertices with GPS coordinates and stand flags.
- `GET /campus/locations/search?q={query}&limit={k}`: Trie-powered prefix search.
- `GET /campus/locations/nearest?lat={lat}&lng={lng}`: Snaps a raw GPS point to the closest campus vertex.
- `GET /campus/graph`: Returns graph metadata (vertex count, edge count, connectivity).

## Shortest Path & Routing (`/api/v1/routes`)
- `POST /routes/shortest-path`:
  - Request: `{"sourceId": "dairy_gate", "targetId": "bottola"}`
  - Response: Dijkstra shortest path result with ordered node IDs, segments, total meters, and time.
- `POST /routes/ride-options`:
  - Request: `{"pickupId": "dairy_gate", "destinationId": "bottola", "passengerCount": 1}`
  - Response: Evaluated candidate shared rickshaws ranked by detour score.

## Nearby Drivers (`/api/v1/drivers`)
- `GET /drivers/nearby?lat={lat}&lng={lng}&radius={m}`: Online rickshaws within `radius` meters (default 3000) of a GPS point, closest first, with distance, ETA and free seats.

## Passenger Rides (`/api/v1/rides/requests`)
- `POST /rides/requests`: Submit a ride request.
  - Body: `{"pickupLocationId", "destinationLocationId", "passengerCount", "selectedVehicleId"?, "passengerLatitude"?, "passengerLongitude"?}`
  - With `selectedVehicleId` the request goes only to that rickshaw; without it, it is broadcast to every online driver (first to accept wins).
  - Fare is computed on the server from the Dijkstra distance. A passenger can have only one active request.
- `GET /rides/requests/me`: Get current passenger's active and past ride requests.
- `GET /rides/requests/{id}`: Get specific ride request status.
- `POST /rides/requests/{id}/cancel`: Cancel a pending ride request.

## Driver Operations (`/api/v1/driver`)
- `POST /driver/availability`: Toggle online/offline status and current stand.
- `GET /driver/requests`: Requests sent to this driver plus open broadcast requests (hidden once this driver ignores them), sorted by road distance to pickup.
- `POST /driver/requests/{id}/accept`: Concurrency-safe acceptance (locks vehicle and request).
- `POST /driver/requests/{id}/reject`: Targeted request → status `REJECTED`. Broadcast request → hidden from this driver only, stays open for others.
- `POST /driver/location`: Live GPS update `{"latitude", "longitude"}`; the server snaps it to the nearest campus vertex and broadcasts to active passengers.
- `POST /driver/trips/{tripId}/status`: Transition trip status (`ARRIVING`, `IN_PROGRESS`, `COMPLETED`).
- `GET /driver/me`: Vehicle, active trip summary and the trip's active passenger requests.

## WebSocket STOMP Channels
- Endpoint: `/ws`
- Subscriptions:
  - `/topic/rides/{requestId}`: Real-time status changes for a ride request.
  - `/topic/driver-location/{driverId}`: Live GPS position broadcast.

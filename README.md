# Smart Campus Ride-Sharing and Route Optimizer

A Java Swing desktop application for rickshaw ride-sharing on the Jahangirnagar
University (JU) campus. It is a Data Structures and Algorithms project: every
feature in the window is powered by an algorithm that is written out in the
source code.

| Feature in the UI                     | Data structure / algorithm            | Source file                                   |
|---------------------------------------|---------------------------------------|-----------------------------------------------|
| Campus routes and map visualization   | Weighted graph + Google Maps (optional) | `src/main/java/dsa/CampusGraph.java`, `src/main/java/ui/GoogleMapsPanel.java` |
| Find Shortest Route                   | Dijkstra with `PriorityQueue`         | `src/main/java/dsa/Dijkstra.java`             |
| Search Location (type to search)      | Trie (prefix tree)                    | `src/main/java/dsa/LocationTrie.java`         |
| Pending Requests Queue, FIFO policy   | Queue (`ArrayDeque`)                  | `src/main/java/dsa/RideQueue.java`            |
| Pending Requests Queue, PRIORITY      | Min-heap (`PriorityQueue`)            | `src/main/java/dsa/RideQueue.java`            |
| Match Next Request                    | Nearest available rickshaw (Dijkstra) | `src/main/java/service/RideMatchingService.java` |
| Driver offer fallback                 | Nearest untried available rickshaw     | `src/main/java/service/RideService.java` |
| Shared-trip matching                  | Route insertion + Dijkstra detour check | `src/main/java/service/RideMatchingService.java` |
| Seat booking                          | Synchronized capacity and offer reservations | `src/main/java/service/RideService.java` |
| Simulated movement                    | Swing Timer advances graph edges       | `src/main/java/ui/DriverDashboard.java` |
| Start / Complete / Cancel Ride        | State machine                         | `src/main/java/model/RideStatus.java`         |
| Account passwords                     | Salted PBKDF2 (JDK cryptography)      | `src/main/java/service/AccountService.java`   |
| Saving and loading                    | CSV text files                        | `src/main/java/storage/CsvStorage.java`       |

No database or web server is used. Both dashboards use Google Maps as the
default map when a Google API key is configured; otherwise they default to the
local campus graph visualization, which shows the simulated rickshaw position
and route. JUnit is used only for tests.

Passenger and driver dashboards opened with **Open Another Session** share one
in-memory service and CSV data directory. The application does not currently
coordinate simultaneous writes or reloads across separate Java processes; run
one application process per data directory.

## Requirements

- Java 25 or newer (JDK)
- Maven 3.9 or newer

## Build, test and run

Run these from the project folder:

```bash
mvn clean package
java -cp "target\campus-ride.jar;target\dependency\*" Main
```

Other useful commands:

```bash
mvn test
mvn -DskipTests package
java -Dcampusride.dataDir=C:\campus-rider-data -cp "target\campus-ride.jar;target\dependency\*" Main
```

The Google Maps tab uses JavaFX WebView and the Google Maps JavaScript API.
To enable and open it by default in Windows PowerShell, configure a Google API
key outside the repository and start the application:

```powershell
$env:GOOGLE_MAPS_API_KEY = "your-restricted-key"
java -cp "target\campus-ride.jar;target\dependency\*" Main
```

Enable the Maps JavaScript API and Geocoding API with billing for that key.
Restrict the key to those APIs and monitor its usage. A Maps JavaScript key is
sent to Google by the embedded browser and is visible at runtime; it is not a
server secret. Do not commit it. With no key, the optional tab shows an explicit configuration message instead
of a pretend map, and the offline campus graph is selected automatically. The
Google map geocodes campus location names and may not precisely match the
application's schematic campus graph or roads.

On start the console prints the data folder that is being used, for example:

```
Data folder: /home/user/campus-rider-app/data
Loaded 25 locations, 33 roads, 5 rickshaws, 0 rides.
```

## Using the application

**Login and account registration**

1. Select Passenger or Driver, then log in with the registered mobile number
   and password.
2. Use *Create Account* to register. Driver accounts also record a unique
   rickshaw ID, initial location, destination and seat counts.
3. Passwords are stored as salted PBKDF2 hashes in `data/passengers.csv` and
   `data/drivers.csv`, never as plain text. Driver rickshaw details are also
   saved in `data/rickshaws.csv` so the existing matching service can use them.
4. If you forget your password, choose *Forgot password?* and enter the
   account's registered full name and mobile number (or local account ID),
   then choose a new password. This offline demonstration only matches those
   locally stored details; it does not send an OTP or verify ownership of a
   mobile number. It is not production-grade identity verification.
5. A driver dashboard is bound to the logged-in driver's rickshaw. Use *Open
   Another Session* to open a second account while leaving this dashboard and
   its simulation active. Active requests or passenger rides must be resolved
   before logging that driver out.

**Passenger and driver dashboards**

1. A driver selects their current location, destination and seat counts, then
   presses **Start Trip**. Going online is required to receive bookings. The
   simulated vehicle does not move until a driver accepts a passenger request.
2. A passenger chooses pickup, destination and seat count, then searches. Only
   online active trips with a compatible remaining route and sufficient seats
   are listed.
3. The passenger selects a trip and books. The driver accepts or rejects the
   request; acceptance deducts seats, while rejection leaves seats unchanged
   and tries the next suitable trip.
4. The driver's Swing Timer advances the rickshaw through Dijkstra graph nodes
   on a background worker. Both dashboards animate the marker along the same
   timestamped graph edge with a 16 ms Swing repaint timer, so refreshes do not
   restart the interpolation. At pickup, only the driver can confirm
   **Passenger Onboard**.
5. At a passenger's drop-off, the rickshaw waits for the driver to confirm
   **Complete Passenger Trip**. That releases the passenger's seats; the shared
   rickshaw continues its remaining itinerary for other bookings.
6. Both dashboards refresh ride and position state automatically from the
   shared in-process ride service. The driver's seat summary distinguishes
   total capacity, reserved/booked seats, occupied seats and seats still
   available. A booking remains reserved after acceptance but is counted as
   occupied only after driver-confirmed pickup.
7. The **Live Campus Simulation** tab shows the schematic graph route with
   distinct pickup/destination markers. It is not GPS tracking and node
   positions are not geographic coordinates. The optional Google Maps tab
   requires a key and network access; it geocodes place names, which may not
   match the exact campus graph nodes or roads. The campus CSV does not store
   latitude/longitude, so the graph simulation is the reliable route and
   movement view. Passenger ride cards show a demonstration fare estimate of
   ৳10 per kilometre using the Dijkstra shortest-path graph distance; no payment
   is collected.
8. Passengers can cancel before boarding; reserved seats and itinerary stops
   are then released. A driver can end a trip after all bookings are completed
   or cancelled.

**Route Finder tab**

1. Type in *Search Location*. Matching places appear as you type (Trie search).
   Names, single words ("gate") and short names ("tsc", "cse", "mmh") all work.
2. Select a match and press *Use as Start* or *Use as Destination*,
   or choose them directly in the drop-down lists.
3. Press *Find Shortest Route*. The full route is shown stop by stop with each
   road's name and length, followed by the total distance.

**Rides and Rickshaws tab**

1. Enter a passenger name, choose the passenger type (STUDENT, STAFF or
   EMERGENCY), pickup and destination, then press *Create Ride Request*.
   The request is stored as PENDING and joins the queue.
2. Choose the *Queue policy*:
   - FIFO: first come, first served.
   - PRIORITY: EMERGENCY first, then STAFF, then STUDENT. Requests of the same
     type are served in the order they were created.
   The list shows the pending requests in the exact order they will be matched.
3. Press *Match Next Request*. The first request in the queue gets the nearest
   available rickshaw (shortest road distance to the pickup). If no rickshaw is
   free, the request stays first in the queue.
4. Select a ride in the table and use *Start Ride*, *Complete Ride* or
   *Cancel Ride*. When a ride is completed, its rickshaw becomes available again
   at the destination.

Every result or error is shown in the message line at the bottom of each tab.

## Ride lifecycle

```
Legacy request:  PENDING --> OFFERED --> MATCHED --> IN_PROGRESS --> COMPLETED
                              |             |
                              |             +--> CANCELLED
                              +--> OFFERED to next driver or NO_DRIVER_AVAILABLE

Shared booking:  OFFERED --> MATCHED --> ARRIVED_AT_PICKUP --> IN_PROGRESS --> COMPLETED
                    |             |              |                    |
                    +--> OFFERED to next trip    +--> CANCELLED       +--> (not cancellable)
```

Any other change (for example completing a PENDING ride, or cancelling a ride
that is already in progress) is rejected with an error message.

## Data files

All data is kept in the project's `data/` folder when the project is launched
from its packaged/classes location. The application prints the exact folder
on startup. To use another folder, set `-Dcampusride.dataDir=<path>`:

| File              | Columns                                                                                                        | Changes at runtime |
|-------------------|----------------------------------------------------------------------------------------------------------------|--------------------|
| `locations.csv`   | `id,name,category,rickshawStand,aliases` (aliases separated by `;`)                                            | no                 |
| `roads.csv`       | `from,to,distanceMeters,roadName` (every road is two-way)                                                      | no                 |
| `rickshaws.csv`   | Legacy 4 columns or extended `id,driverName,locationId,status,totalSeats,availableSeats,activeTrip,destinationId,itinerary` | yes |
| `rides.csv`       | Legacy 8-10 columns or 12 columns ending in `requestedSeats,sharedTrip` | yes |
| `passengers.csv`  | `fullName,mobileNumber,passwordHash`                                                                            | yes                |
| `drivers.csv`     | `fullName,mobileNumber,passwordHash,rickshawId,initialLocationId,destinationId,totalSeats,availableSeats`       | yes                |

How storage works:

- On start the campus, ride and account files are read. Missing campus files
  use the sample data inside the jar; missing ride and account files start
  with only their header lines.
- After ride changes, `rides.csv` and `rickshaws.csv` are saved immediately.
  Account registration and destination updates save their corresponding
  account CSV file.
- Saving writes to a temporary file and then renames it over the old file, so
  a crash during saving cannot leave a half-written file.
- Invalid rows (wrong column count, bad numbers, unknown locations, unknown
  rickshaw, duplicate id, ...) are skipped and listed in a warning dialog at
  start. Before anything is overwritten, the original file is copied to
  `<name>.bak` so no data is lost.
- If `locations.csv` contains no valid location, the built-in sample campus is
  used for that session and the file is left untouched.
- Legacy rickshaw and ride CSV rows remain readable. New rickshaw rows persist
  trip stops, location and seat counts; ride rows persist requested seats,
  passenger association, status and rejected drivers.
- Available seats are decremented only on acceptance. Pending offers reserve
  capacity for overbooking checks without changing the displayed seat count.
- Movement/status and CSV writes happen locally; no GPS, application server or
  database is used. Google Maps requires internet access and an API key.
  Driver online/offline state resets when the process restarts.

Ride status and itinerary are persisted, but the passenger-facing update-message
timeline is kept in memory and starts fresh when the app restarts. Back up the
`data/` folder before making manual CSV changes; deleting the whole folder also
deletes registered accounts and any locally saved campus data.

## Project structure

```
pom.xml
src/main/java/
  Main.java                    entry point: loads files, opens the window
  model/                       plain data classes
    Location.java  Road.java  Rickshaw.java  RideRequest.java  TripStop.java
    RideStatus.java (lifecycle rules)  PassengerType.java (priorities)
    PassengerAccount.java  DriverAccount.java
  dsa/                         the data structures and algorithms
    CampusGraph.java  Edge.java  Dijkstra.java  RouteResult.java
    LocationTrie.java  RideQueue.java  QueuePolicy.java
  service/                     application logic
    RouteService.java          builds graph + trie, route text for the UI
    RideMatchingService.java   legacy matching and route-compatible trips
    RideService.java           create/match/start/complete/cancel + offers
    AccountService.java        account registration, login and password hashing
  storage/
    CsvStorage.java            reading and writing the CSV files
  ui/                          Swing user interface
    LoginWindow.java  PassengerDashboard.java  DriverDashboard.java
    CampusMapPanel.java  MainWindow.java  RoutePanel.java  RidePanel.java  MessageLabel.java
src/main/resources/sample/     sample campus data (25 places, 33 roads, 5 rickshaws)
src/test/java/                 JUnit 5 tests
docs/viva-guide.md             explanation of each algorithm for the viva
```

## Tests

`mvn test` runs the full JUnit suite, including route-based trip matching,
seat sharing, rejection fallback, simulated movement and CSV restart tests:

| Test class          | What it checks                                                                 |
|---------------------|---------------------------------------------------------------------------------|
| `CampusGraphTest`   | two-way roads, neighbour lists, invalid weights and locations are rejected     |
| `DijkstraTest`      | correct shortest route (not fewest roads), unreachable, same place, bad input  |
| `LocationTrieTest`  | case-insensitive prefix, word and alias search, no duplicates, empty input     |
| `RideQueueTest`     | FIFO order, priority order, no duplicate entries, switching policy             |
| `RideServiceTest`   | nearest offers, accept/reject, fallback, rejected-driver exclusion, restart restore |
| `AccountServiceTest`| passenger/driver registration, hashed login, invalid password, duplicate mobile, CSV restart |
| `SharedTripServiceTest` | trip start/end, route/detour matching, multi-seat booking, fallback, cancellation, simulation and persistence |
| `CsvStorageTest`    | missing files created, malformed rows skipped with backup, empty files         |
| `PanelTest`         | clicking the real Swing buttons calls the right service methods                |

## Limitations

- The campus graph is fixed by `locations.csv` and `roads.csv`; there is no
  screen for editing places or roads. Google geocoding may not resolve a
  campus-specific building name; the map reports that failure rather than
  fabricating coordinates.
- A route accepts only stops whose pickup/drop-off insertion adds at most
  `max(150 m, 20% of the driver's remaining trip distance)`. This bounds detours
  while allowing small deviations; candidates are ranked by detour, then
  pickup distance, then rickshaw ID.
- Simulation advances one graph edge per 900 ms after an accepted booking,
  while the driver's dashboard is open. The Google map's line connects
  geocoded graph stops and is a visualization, not turn-by-turn road geometry;
  elapsed travel time or fares are not calculated.
- The application is single-user: two copies running on the same `data/`
  folder at the same time would overwrite each other's changes.

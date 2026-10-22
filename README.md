# Smart Campus Ride-Sharing and Route Optimizer

A Java Swing desktop application for rickshaw ride-sharing on the Jahangirnagar
University (JU) campus. It is a Data Structures and Algorithms project: every
feature in the window is powered by an algorithm that is written out in the
source code.

| Feature in the UI                     | Data structure / algorithm            | Source file                                   |
|---------------------------------------|---------------------------------------|-----------------------------------------------|
| Campus road map                       | Weighted graph, adjacency list        | `src/main/java/dsa/CampusGraph.java`          |
| Find Shortest Route                   | Dijkstra with `PriorityQueue`         | `src/main/java/dsa/Dijkstra.java`             |
| Search Location (type to search)      | Trie (prefix tree)                    | `src/main/java/dsa/LocationTrie.java`         |
| Pending Requests Queue, FIFO policy   | Queue (`ArrayDeque`)                  | `src/main/java/dsa/RideQueue.java`            |
| Pending Requests Queue, PRIORITY      | Min-heap (`PriorityQueue`)            | `src/main/java/dsa/RideQueue.java`            |
| Match Next Request                    | Nearest available rickshaw (Dijkstra) | `src/main/java/service/RideMatchingService.java` |
| Start / Complete / Cancel Ride        | State machine                         | `src/main/java/model/RideStatus.java`         |
| Saving and loading                    | CSV text files                        | `src/main/java/storage/CsvStorage.java`       |

No database, no web server and no external libraries are used. The only
dependency is JUnit, and only for the tests.

## Requirements

- Java 17 or newer (JDK)
- Maven 3.6 or newer

## Build, test and run

Run these from the project folder:

```bash
mvn clean package          # compiles, runs all tests, builds target/campus-ride.jar
java -jar target/campus-ride.jar
```

Other useful commands:

```bash
mvn test                   # run the tests only
mvn -DskipTests package    # build the jar without running tests
java -Dcampusride.dataDir=/path/to/folder -jar target/campus-ride.jar   # use another data folder
```

On start the console prints the data folder that is being used, for example:

```
Data folder: /home/user/campus-rider-app/data
Loaded 25 locations, 33 roads, 5 rickshaws, 0 rides.
```

## Using the application

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
PENDING --> MATCHED --> IN_PROGRESS --> COMPLETED
   |           |
   +-----------+--> CANCELLED
```

Any other change (for example completing a PENDING ride, or cancelling a ride
that is already in progress) is rejected with an error message.

## Data files

All data is kept in the `data/` folder (created next to where you run the
program):

| File            | Columns                                                                     | Changes at runtime |
|-----------------|-----------------------------------------------------------------------------|--------------------|
| `locations.csv` | `id,name,category,rickshawStand,aliases` (aliases separated by `;`)         | no                 |
| `roads.csv`     | `from,to,distanceMeters,roadName` (every road is two-way)                   | no                 |
| `rickshaws.csv` | `id,driverName,locationId,status`                                           | yes                |
| `rides.csv`     | `id,passengerName,passengerType,pickupId,destinationId,distanceMeters,status,rickshawId` | yes   |

How storage works:

- On start all four files are read. A missing file is created from the sample
  data inside the jar (`src/main/resources/sample/`); a missing `rides.csv`
  starts with only the header line.
- After every create, match, start, complete or cancel, `rides.csv` and
  `rickshaws.csv` are saved immediately.
- Saving writes to a temporary file and then renames it over the old file, so
  a crash during saving cannot leave a half-written file.
- Invalid rows (wrong column count, bad numbers, unknown locations, unknown
  rickshaw, duplicate id, ...) are skipped and listed in a warning dialog at
  start. Before anything is overwritten, the original file is copied to
  `<name>.bak` so no data is lost.
- If `locations.csv` contains no valid location, the built-in sample campus is
  used for that session and the file is left untouched.
- A rickshaw's BUSY or AVAILABLE status is rebuilt from the rides on load
  (busy means it has a MATCHED or IN_PROGRESS ride), so the two files cannot
  disagree.
- Commas in passenger names are replaced with spaces because the comma is the
  column separator.

To reset all rides, close the program and delete the `data/` folder.

## Project structure

```
pom.xml
src/main/java/
  Main.java                    entry point: loads files, opens the window
  model/                       plain data classes
    Location.java  Road.java  Rickshaw.java  RideRequest.java
    RideStatus.java (lifecycle rules)  PassengerType.java (priorities)
  dsa/                         the data structures and algorithms
    CampusGraph.java  Edge.java  Dijkstra.java  RouteResult.java
    LocationTrie.java  RideQueue.java  QueuePolicy.java
  service/                     application logic
    RouteService.java          builds graph + trie, route text for the UI
    RideMatchingService.java   chooses the nearest available rickshaw
    RideService.java           create/match/start/complete/cancel + saving
  storage/
    CsvStorage.java            reading and writing the CSV files
  ui/                          Swing user interface
    MainWindow.java  RoutePanel.java  RidePanel.java  MessageLabel.java
src/main/resources/sample/     sample campus data (25 places, 33 roads, 5 rickshaws)
src/test/java/                 JUnit 5 tests
docs/viva-guide.md             explanation of each algorithm for the viva
```

## Tests

`mvn test` runs 32 JUnit tests:

| Test class          | What it checks                                                                 |
|---------------------|---------------------------------------------------------------------------------|
| `CampusGraphTest`   | two-way roads, neighbour lists, invalid weights and locations are rejected     |
| `DijkstraTest`      | correct shortest route (not fewest roads), unreachable, same place, bad input  |
| `LocationTrieTest`  | case-insensitive prefix, word and alias search, no duplicates, empty input     |
| `RideQueueTest`     | FIFO order, priority order, no duplicate entries, switching policy             |
| `RideServiceTest`   | matching only free rickshaws, nearest wins, invalid transitions, restart restore |
| `CsvStorageTest`    | missing files created, malformed rows skipped with backup, empty files         |
| `PanelTest`         | clicking the real Swing buttons calls the right service methods                |

## Limitations

- The campus map is fixed by `locations.csv` and `roads.csv`; there is no
  screen for editing places or roads.
- One rickshaw carries one ride at a time (no seat sharing between ride requests).
- Distances are road lengths in meters; travel time and fares are not calculated.
- The application is single-user: two copies running on the same `data/`
  folder at the same time would overwrite each other's changes.

# Viva Preparation Guide

Short explanations of every data structure and algorithm in the project, with
the file to open if the examiner asks to see the code.

Notation: V = number of locations (25), E = number of roads (33),
R = number of rickshaws, n = number of pending requests.

---

## 1. Graph

**What it is.** A set of vertices (points) connected by edges (lines).
Here a vertex is a campus location and an edge is a road. Each road has a
weight: its length in meters. Roads are two-way, so the graph is
*undirected* and *weighted*.

**Why a graph.** Roads on a campus meet at junctions and form cycles. A list or a
tree cannot model "many ways to get from A to B"; a graph can.

File: `dsa/CampusGraph.java`

## 2. Adjacency list

**What it is.** For every vertex, a list of its edges:

```
dairy_gate    -> [shaheed_minar 116.6, zahir_raihan_auditorium 123.3, ...]
shaheed_minar -> [dairy_gate 116.6, new_arts_building 95.2, ...]
```

Stored as `Map<String, List<Edge>>`. A two-way road is added twice, once in
each direction (`addRoad`).

**Why not an adjacency matrix?** A matrix needs V x V cells (625 here), most of
them empty because each place only touches a few roads. An adjacency list uses
O(V + E) memory and lets Dijkstra loop over just the real neighbours.

| Operation               | Adjacency list | Adjacency matrix |
|-------------------------|----------------|------------------|
| Memory                  | O(V + E)       | O(V^2)           |
| List neighbours of u    | O(degree(u))   | O(V)             |
| Is there a road u-v?    | O(degree(u))   | O(1)             |

Validation: unknown locations, a road to itself, and zero, negative or NaN
lengths are rejected.

## 3. Dijkstra's shortest path

File: `dsa/Dijkstra.java`

**Idea.** Keep a "best known distance" for every location. Repeatedly take the
unvisited location with the smallest distance. Because all road lengths are
positive, nothing found later can make it shorter, so its distance is final.
Then *relax* each neighbour: if going through this location is shorter than
the neighbour's best distance, update it and remember where we came from.

**Steps in the code.**

1. `distance[source] = 0`, push `(source, 0)` into a min `PriorityQueue`.
2. Poll the smallest entry `u`. If `u` was already visited, skip it (an old,
   longer entry). Otherwise mark it visited.
3. If `u` is the destination, stop early.
4. For each road `u -> v` of length `w`: if `distance[u] + w < distance[v]`,
   set `distance[v]`, set `previous[v] = u`, and push `(v, new distance)`.
5. Rebuild the route by following `previous` back from the destination, then
   reverse the list.

**Special cases.** Same start and destination: route of one stop, 0 m.
Unknown location: a "not found" result with a message. Unreachable
destination: the queue empties before the destination is reached, so it has
no distance, and the result says no road connects them.

**Complexity.** Every push and poll on the heap costs O(log V); each road can
cause at most one push. Time O((V + E) log V), memory O(V + E).

**Why not BFS?** BFS finds the route with the fewest roads, not the shortest
distance. The test `findsTheShortestRouteNotTheFewestRoads` shows the
difference: A-B-D is 2 roads and 9 m, A-C-B-D is 3 roads and 8 m.

**Why it fails with negative weights.** A location's distance is treated as
final when it is polled. A negative road found later could lower it, which the
algorithm never revisits. Road lengths are never negative, so this is safe.

**Tie-breaking.** If two entries have the same distance, the one with the
smaller id is polled first, so the same input always produces the same route.

## 4. Trie (prefix tree)

File: `dsa/LocationTrie.java`

**What it is.** A tree in which each edge is one character. The path from the
root to a node spells a prefix. Each node keeps its children in a
`TreeMap<Character, TrieNode>` and a list of the locations whose key ends there.

**Insert.** For each location we insert its full name, each word of the name,
and each alias, all in lower case. That is how "gate" finds Dairy Gate,
Prantik Gate and Bishmile Gate, and "tsc" finds Central Cafeteria (TSC).

**Search.** Walk down one character at a time. If a character is missing,
there are no matches. Otherwise do a depth-first walk of the subtree below
that node and collect all locations. A map keyed by location id removes
duplicates, because one place can be reached by several keys.

**Complexity.** Insert O(L) for a key of length L. Search O(P + N): P to walk
the prefix, N for the nodes in the subtree. The time does not depend on how
many other places start with different letters.

**Why not loop over every name with `startsWith`?** That checks every location
on every key press, O(V x L). A trie only looks at the branch for the typed
prefix, and it is the standard structure for autocomplete.

Empty or blank input returns no results, and only locations that exist in the
graph are ever inserted, so search can only return real places.

## 5. Queue (FIFO)

File: `dsa/RideQueue.java` (policy FIFO)

A queue is First In, First Out: the earliest request is processed first, which
is fair. It is implemented with `ArrayDeque`: `offer` adds at the back and
`poll` removes from the front, both O(1).

## 6. PriorityQueue (binary min-heap)

File: `dsa/RideQueue.java` (policy PRIORITY)

A `PriorityQueue` is a binary heap: the smallest element by the comparator is
always at the top. The comparator is:

1. Passenger type priority: EMERGENCY = 1, STAFF = 2, STUDENT = 3 (smaller first).
2. For the same type, the smaller request id (the earlier request) first.

Rule 2 makes the order deterministic and keeps fairness within each type.
`offer` and `poll` are O(log n), `peek` is O(1).

Note: iterating over a `PriorityQueue` does not give sorted order. To show the
queue in the UI, `inProcessingOrder()` polls a copy of it.

**Same request never matched twice.** `matchNextRequest` removes the request
from the queue with `poll()` before marking it MATCHED. `add` also refuses a
request that is already queued. A cancelled request is removed with `remove()`.

## 7. Ride matching

File: `service/RideMatchingService.java`, used by `RideService.matchNextRequest`

1. `peek` the next request (using the active queue policy).
2. For every rickshaw that is AVAILABLE, run Dijkstra from the rickshaw's
   location to the pickup.
3. Choose the smallest distance; if two are equal, the smaller rickshaw id.
4. If there is a match: `poll` the request, set it to MATCHED, store the
   rickshaw id, set the rickshaw to BUSY, save both files.
5. If there is no free rickshaw, the request stays at the front of the queue
   and an error is shown.

A BUSY rickshaw is skipped in step 2, so it can never receive a second active
ride. Cost: O(R x (V + E) log V) per match.

## 8. Ride lifecycle (state machine)

File: `model/RideStatus.java`

```
PENDING -> MATCHED -> IN_PROGRESS -> COMPLETED
PENDING -> CANCELLED,  MATCHED -> CANCELLED
```

`RideRequest.moveTo` checks `canMoveTo` and throws an exception for any other
change. The UI catches it and shows the error. COMPLETED and CANCELLED are
final. Completing a ride frees the rickshaw at the destination; cancelling a
MATCHED ride frees the rickshaw; cancelling a PENDING ride removes it from the
queue.

## 9. Why files instead of a database

- The data is small (25 places, 33 roads, a few rickshaws, the rides) and is
  used by one person on one computer. A database server would add setup work
  but no benefit.
- The focus of the project is data structures. With CSV files all searching,
  sorting and matching is done by our own Java code, not by SQL queries.
- CSV files can be opened and checked in any text editor or spreadsheet,
  which makes debugging and demonstrating easy.
- Safety measures: write to a temporary file then rename (no half-written
  files), skip invalid rows instead of crashing, and keep a `.bak` copy of any
  file that had invalid rows.

Trade-off: files do not support many users writing at the same time, and they
have no transactions or queries. For a multi-user system a database would be
the right choice.

## 10. Complexity summary

| Operation                       | Structure          | Time                |
|---------------------------------|--------------------|---------------------|
| Add road                        | Adjacency list     | O(1)                |
| Neighbours of a location        | Adjacency list     | O(degree)           |
| Shortest route                  | Dijkstra + heap    | O((V + E) log V)    |
| Insert key into trie            | Trie               | O(L)                |
| Prefix search                   | Trie               | O(P + N)            |
| Add / take request (FIFO)       | ArrayDeque         | O(1)                |
| Add / take request (PRIORITY)   | PriorityQueue      | O(log n)            |
| Cancel a pending request        | either queue       | O(n)                |
| Match one request               | Dijkstra per rickshaw | O(R (V + E) log V) |
| Save rides                      | CSV file           | O(number of rides)  |

## 11. Likely questions

- *Why use a PriorityQueue in Dijkstra?* To get the closest unvisited location
  in O(log V) instead of scanning all V locations each time (O(V^2) overall).
- *Why can a location appear in the heap several times?* Java's
  `PriorityQueue` cannot lower the key of an existing entry, so we push a new
  entry and skip old ones when they are polled (the `visited` check).
- *How do you show the full route, not just the distance?* The `previous` map
  stores the predecessor of each location; following it back from the
  destination and reversing gives the route.
- *How is the trie case-insensitive?* Keys and search text are both converted
  to lower case before use.
- *What happens if two rickshaws are equally close?* The smaller id wins, so
  results are repeatable.
- *What if the data file is damaged?* Invalid rows are skipped, the original
  is copied to `.bak`, and a warning lists what was skipped.

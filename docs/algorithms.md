# JU Smart Ride: Algorithms and Data Structures Documentation

## 1. Weighted Campus Graph (`CampusGraph`)
The road network of Jahangirnagar University is modeled as an undirected, weighted graph:
$$G = (V, E)$$
- **$V$ (Vertices):** 25 verified campus landmark locations and rickshaw stands.
- **$E$ (Edges):** 66 bi-directional paved and unpaved corridors connecting gates, academic halls, residential dorms, and service centers.
- **Weights:** Measured road distances in meters derived from OpenStreetMap survey coordinates, along with estimated traversal speeds for rickshaws (~12 km/h or 3.33 m/s).

## 2. Dijkstra's Shortest Path Algorithm (`DijkstraShortestPath`)
Used to determine the globally optimal shortest route between any pickup and destination point.
- **Data Structure:** Minimum Priority Queue (`java.util.PriorityQueue`) ordered by accumulated distance from source.
- **Time Complexity:** $O((V + E) \log V)$
- **Space Complexity:** $O(V + E)$
- **Validation:** Handles unreachable components, same-node requests ($0\text{m}$ cost), and multi-hop turn paths.

## 3. Trie Search Index (`TrieSearch`)
Provides prefix-based autocomplete for university landmarks and abbreviations.
- **Data Structure:** Multi-way Tree where each node maintains transitions for both English Latin characters and Bengali Unicode code points.
- **Features:** Case-insensitive prefix search, tokenized word-level matching, and fast Top-$K$ retrieval.

## 4. Fair Queueing (`PendingRideQueue`)
- First-In, First-Out (FIFO) queue for matching unassigned passenger requests to prevent driver starvation or biased request dispatching.

## 5. Detour-Constrained Multi-Passenger Ride Matching (`RideMatchingService`)
Evaluates whether a passenger request $(P_{new}, D_{new})$ can be inserted into an active rickshaw trip without violating capacity or detour limits:
1. **Seat Capacity Check:** Ensures occupancy does not exceed vehicle capacity (max 3 seats) on any intermediate segment.
2. **Detour Metric:**
   $$\text{Detour Ratio} = \frac{\text{New Total Distance} - \text{Original Distance}}{\text{Direct Shortest Distance}(P_{new}, D_{new})} \le 0.45$$
3. **Multi-Criteria Candidate Ranking:** Prioritizes vehicles with minimal extra distance, low ETA, and optimal seat utilization.

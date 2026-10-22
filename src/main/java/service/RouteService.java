package service;

import dsa.CampusGraph;
import dsa.Dijkstra;
import dsa.Edge;
import dsa.LocationTrie;
import dsa.RouteResult;
import model.Location;
import model.Road;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds the campus graph and the search trie, and answers route and search questions. */
public class RouteService {
    private final CampusGraph graph = new CampusGraph();
    private final LocationTrie trie = new LocationTrie();

    /** Invalid locations or roads are skipped and described in the warnings list. */
    public RouteService(List<Location> locations, List<Road> roads, List<String> warnings) {
        for (Location location : locations) {
            try {
                graph.addLocation(location);
                trie.addLocation(location);
            } catch (IllegalArgumentException e) {
                warnings.add("Skipped location: " + e.getMessage());
            }
        }
        for (Road road : roads) {
            try {
                graph.addRoad(road.getFromId(), road.getToId(), road.getDistanceMeters(), road.getName());
            } catch (IllegalArgumentException e) {
                warnings.add("Skipped road: " + e.getMessage());
            }
        }
    }

    public CampusGraph getGraph() {
        return graph;
    }

    /** All locations sorted by name, for drop-down lists. */
    public List<Location> getLocationsSorted() {
        List<Location> list = new ArrayList<>(graph.getAllLocations());
        list.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return list;
    }

    public boolean isValidLocation(String id) {
        return graph.hasLocation(id);
    }

    public String getLocationName(String id) {
        Location location = graph.getLocation(id);
        return location == null ? id : location.getName();
    }

    /** Trie prefix search. */
    public List<Location> searchLocations(String prefix) {
        return trie.search(prefix);
    }

    /** Dijkstra shortest path. */
    public RouteResult findRoute(String fromId, String toId) {
        return Dijkstra.findShortestPath(graph, fromId, toId);
    }

    /** Text shown in the UI: every stop, the road and length of every leg, and the total. */
    public String describeRoute(RouteResult route) {
        if (!route.isFound()) {
            return "No route: " + route.getMessage();
        }
        List<String> path = route.getPath();
        StringBuilder text = new StringBuilder();
        if (path.size() == 1) {
            text.append("Start and destination are the same place: ")
                    .append(getLocationName(path.get(0))).append("\n");
        } else {
            text.append("Route with ").append(path.size() - 1).append(" road(s):\n\n");
            text.append("  1. ").append(getLocationName(path.get(0))).append("\n");
            for (int i = 1; i < path.size(); i++) {
                Edge road = graph.getRoad(path.get(i - 1), path.get(i));
                text.append(String.format(Locale.ROOT, "       via %s, %.1f m%n",
                        road.getRoadName(), road.getDistanceMeters()));
                text.append("  ").append(i + 1).append(". ").append(getLocationName(path.get(i))).append("\n");
            }
        }
        text.append(String.format(Locale.ROOT, "%nTotal distance: %.1f m (%.2f km)%n",
                route.getTotalDistanceMeters(), route.getTotalDistanceMeters() / 1000));
        return text.toString();
    }
}

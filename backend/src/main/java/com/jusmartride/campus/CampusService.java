package com.jusmartride.campus;

import com.jusmartride.algorithms.TrieSearch;
import com.jusmartride.common.GeoUtils;
import com.jusmartride.graph.CampusGraph;
import com.jusmartride.graph.Edge;
import com.jusmartride.graph.GraphLoader;
import com.jusmartride.graph.Vertex;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class CampusService {

    private static final Logger log = LoggerFactory.getLogger(CampusService.class);

    private final CampusLocationRepository campusLocationRepository;
    private final GraphLoader graphLoader;
    private final CampusGraph campusGraph = new CampusGraph();
    private final TrieSearch trieSearch = new TrieSearch();

    public CampusService(CampusLocationRepository campusLocationRepository, GraphLoader graphLoader) {
        this.campusLocationRepository = campusLocationRepository;
        this.graphLoader = graphLoader;
    }

    @PostConstruct
    @Transactional
    public void init() {
        try {
            log.info("Loading JU Campus Road Network and Trie search index...");
            graphLoader.load(campusGraph, trieSearch);
            log.info("Loaded {} vertices and {} edges into CampusGraph",
                    campusGraph.getVertexCount(), campusGraph.getEdgeCount());

            // Sync with DB
            for (Vertex v : campusGraph.getAllVertices()) {
                if (!campusLocationRepository.existsById(v.getId())) {
                    CampusLocation loc = new CampusLocation(
                            v.getId(),
                            v.getName(),
                            v.getBengaliName(),
                            v.getCategory(),
                            v.getLatitude(),
                            v.getLongitude(),
                            v.isRickshawStand(),
                            v.getCategory() + " on JU campus"
                    );
                    campusLocationRepository.save(loc);
                }
            }
            log.info("Synchronized campus locations with PostgreSQL database");
        } catch (Exception e) {
            log.error("Failed to initialize campus road network from JSON", e);
        }
    }

    public CampusGraph getCampusGraph() {
        return campusGraph;
    }

    public TrieSearch getTrieSearch() {
        return trieSearch;
    }

    public List<Vertex> search(String query, int limit) {
        return trieSearch.search(query, limit);
    }

    public Collection<Vertex> getAllVertices() {
        return campusGraph.getAllVertices();
    }

    public Vertex getVertex(String id) {
        return campusGraph.getVertex(id);
    }

    /**
     * Snaps a raw GPS coordinate to the closest campus road-network vertex (linear scan, 25 vertices).
     */
    public Vertex findNearestVertex(double latitude, double longitude) {
        Vertex nearest = null;
        double best = Double.POSITIVE_INFINITY;
        for (Vertex v : campusGraph.getAllVertices()) {
            double d = GeoUtils.haversineMeters(latitude, longitude, v.getLatitude(), v.getLongitude());
            if (d < best) {
                best = d;
                nearest = v;
            }
        }
        return nearest;
    }

    public Collection<Edge> getAllEdges() {
        return campusGraph.getAllEdges();
    }

    public Map<String, Object> getGraphMetadata() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("version", campusGraph.getVersion());
        meta.put("vertexCount", campusGraph.getVertexCount());
        meta.put("edgeCount", campusGraph.getEdgeCount());
        meta.put("isConnected", campusGraph.isConnected());
        meta.put("vertices", campusGraph.getAllVertices());
        meta.put("edges", campusGraph.getAllEdges());
        return meta;
    }
}

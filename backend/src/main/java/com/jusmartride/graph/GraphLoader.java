package com.jusmartride.graph;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jusmartride.algorithms.TrieSearch;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads campus landmark nodes and road segments from JSON resources into CampusGraph and TrieSearch.
 * Decoupled from database, enabling instant in-memory routing and standalone tests.
 */
@Component
public class GraphLoader {

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    public GraphLoader(ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    public void load(CampusGraph graph, TrieSearch trie) throws Exception {
        loadFromClasspath(graph, trie, "classpath:data/campus-locations.json", "classpath:data/campus-roads.json");
    }

    public void loadFromClasspath(CampusGraph graph, TrieSearch trie, String locationsPath, String roadsPath) throws Exception {
        graph.clear();
        if (trie != null) {
            trie.clear();
        }

        Resource locRes = resourceLoader.getResource(locationsPath);
        Resource roadRes = resourceLoader.getResource(roadsPath);

        try (InputStream locIn = locRes.getInputStream();
             InputStream roadIn = roadRes.getInputStream()) {
            loadFromStreams(graph, trie, locIn, roadIn);
        }
    }

    public void loadFromStreams(CampusGraph graph, TrieSearch trie, InputStream locationsStream, InputStream roadsStream) throws Exception {
        JsonNode locArray = objectMapper.readTree(locationsStream);
        for (JsonNode node : locArray) {
            String id = node.get("id").asText();
            String name = node.get("name").asText();
            String bengaliName = node.has("bengaliName") ? node.get("bengaliName").asText() : name;
            double lat = node.get("latitude").asDouble();
            double lon = node.get("longitude").asDouble();
            String category = node.has("category") ? node.get("category").asText() : "LOCATION";
            boolean rickshawStand = node.has("rickshawStand") && node.get("rickshawStand").asBoolean();

            List<String> aliases = new ArrayList<>();
            if (node.has("aliases")) {
                for (JsonNode a : node.get("aliases")) {
                    aliases.add(a.asText());
                }
            }

            Vertex vertex = new Vertex(id, name, bengaliName, lat, lon, category, aliases, rickshawStand);
            graph.addVertex(vertex);
            if (trie != null) {
                trie.indexLocation(vertex);
            }
        }

        JsonNode roadArray = objectMapper.readTree(roadsStream);
        for (JsonNode node : roadArray) {
            String id = node.get("id").asText();
            String sourceId = node.get("sourceId").asText();
            String targetId = node.get("targetId").asText();
            String roadName = node.has("roadName") ? node.get("roadName").asText() : "Campus Road";
            double dist = node.get("distanceMeters").asDouble();
            long time = node.has("estimatedTimeSeconds") ? node.get("estimatedTimeSeconds").asLong() : Math.round(dist / 3.33);
            boolean bidir = !node.has("bidirectional") || node.get("bidirectional").asBoolean();
            boolean accessible = !node.has("rickshawAccessible") || node.get("rickshawAccessible").asBoolean();
            String surface = node.has("surface") ? node.get("surface").asText() : "Paved Asphalt";
            String confidence = node.has("confidence") ? node.get("confidence").asText() : "VERIFIED";

            Edge edge = new Edge(id, sourceId, targetId, roadName, dist, time, bidir, accessible, surface, confidence);
            graph.addEdge(edge);
        }
    }
}

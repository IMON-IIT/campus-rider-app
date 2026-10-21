package com.jusmartride.algorithms;

import com.jusmartride.graph.Vertex;

import java.text.Normalizer;
import java.util.*;

/**
 * Fast Trie-based prefix search and autocomplete for Jahangirnagar University campus locations.
 * Supports:
 * - Case-insensitive English prefix matching
 * - Bengali Unicode prefix matching
 * - Alias and acronym matching (e.g. "CSE", "MMH", "ডেইরি", "বটতলা")
 * - Substring token indexing for multi-word landmark names (e.g., "Gate" matches "Dairy Gate" & "Prantik Gate")
 */
public class TrieSearch {

    private final TrieNode root = new TrieNode();
    private final List<Vertex> allLocations = new ArrayList<>();

    public TrieSearch() {
    }

    public synchronized void indexLocation(Vertex vertex) {
        if (vertex == null) return;
        allLocations.add(vertex);

        // Index full name
        insertPhrase(vertex.getName(), vertex);

        // Index Bengali name
        if (vertex.getBengaliName() != null && !vertex.getBengaliName().isBlank()) {
            insertPhrase(vertex.getBengaliName(), vertex);
        }

        // Index aliases
        if (vertex.getAliases() != null) {
            for (String alias : vertex.getAliases()) {
                insertPhrase(alias, vertex);
            }
        }
    }

    public synchronized void indexAll(Collection<Vertex> vertices) {
        clear();
        for (Vertex v : vertices) {
            indexLocation(v);
        }
    }

    public synchronized void clear() {
        root.getChildren().clear();
        root.getMatchingLocations().clear();
        allLocations.clear();
    }

    private void insertPhrase(String phrase, Vertex vertex) {
        if (phrase == null || phrase.isBlank()) return;

        String normalized = normalize(phrase);
        insertSingleWord(normalized, vertex);

        // Also index each individual word if multi-word phrase
        String[] tokens = normalized.split("\\s+");
        if (tokens.length > 1) {
            for (String token : tokens) {
                if (token.length() >= 2) {
                    insertSingleWord(token, vertex);
                }
            }
        }
    }

    private void insertSingleWord(String word, Vertex vertex) {
        if (word.isEmpty()) return;

        TrieNode curr = root;
        for (char ch : word.toCharArray()) {
            curr = curr.computeIfAbsent(ch);
            curr.addLocation(vertex);
        }
        curr.setEndOfWord(true);
    }

    /**
     * Autocomplete search by prefix.
     *
     * @param query search query
     * @param limit maximum results to return
     * @return ordered list of unique matching campus vertices
     */
    public List<Vertex> search(String query, int limit) {
        if (limit <= 0) limit = 10;

        if (query == null || query.trim().isEmpty()) {
            // Return top default locations (rickshaw stands first, then alphabetical)
            List<Vertex> defaults = new ArrayList<>(allLocations);
            defaults.sort((a, b) -> {
                if (a.isRickshawStand() != b.isRickshawStand()) {
                    return a.isRickshawStand() ? -1 : 1;
                }
                return a.getName().compareToIgnoreCase(b.getName());
            });
            return defaults.subList(0, Math.min(defaults.size(), limit));
        }

        String normalized = normalize(query.trim());
        TrieNode curr = root;
        for (char ch : normalized.toCharArray()) {
            curr = curr.getChild(ch);
            if (curr == null) {
                return Collections.emptyList();
            }
        }

        Set<Vertex> matches = curr.getMatchingLocations();
        List<Vertex> result = new ArrayList<>(matches);

        // Sort by relevance:
        // 1. Exact name match first
        // 2. Starts with query
        // 3. Rickshaw stand status
        // 4. Alphabetical
        final String qLower = normalized;
        result.sort((a, b) -> {
            boolean aExact = normalize(a.getName()).equals(qLower) || normalize(a.getBengaliName()).equals(qLower);
            boolean bExact = normalize(b.getName()).equals(qLower) || normalize(b.getBengaliName()).equals(qLower);
            if (aExact != bExact) return aExact ? -1 : 1;

            boolean aStarts = normalize(a.getName()).startsWith(qLower) || normalize(a.getBengaliName()).startsWith(qLower);
            boolean bStarts = normalize(b.getName()).startsWith(qLower) || normalize(b.getBengaliName()).startsWith(qLower);
            if (aStarts != bStarts) return aStarts ? -1 : 1;

            if (a.isRickshawStand() != b.isRickshawStand()) {
                return a.isRickshawStand() ? -1 : 1;
            }
            return a.getName().compareToIgnoreCase(b.getName());
        });

        if (result.size() > limit) {
            return result.subList(0, limit);
        }
        return result;
    }

    private String normalize(String input) {
        if (input == null) return "";
        // Decompose unicode and lowercase for ASCII
        String norm = Normalizer.normalize(input.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFC);
        // Replace punctuation with space
        return norm.replaceAll("[.,/#!$%^&*;:{}=\\-_`~()?\\'\"]", "").trim();
    }
}

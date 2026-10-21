package com.jusmartride.algorithms;

import com.jusmartride.graph.Vertex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Trie-Based Campus Search Autocomplete Tests")
class TrieSearchTest {

    private TrieSearch trie;
    private Vertex dairyGate;
    private Vertex bottola;
    private Vertex shaheedMinar;
    private Vertex cse;

    @BeforeEach
    void setUp() {
        trie = new TrieSearch();

        dairyGate = new Vertex("dairy_gate", "Dairy Gate", "ডেইরি গেইট", 23.8798, 90.2693,
                "GATE", List.of("dairy", "main gate", "highway entrance"), true);

        bottola = new Vertex("bottola", "Bottola", "বটতলা", 23.8844, 90.2588,
                "FOOD", List.of("bot tola", "food hub"), true);

        shaheedMinar = new Vertex("shaheed_minar", "Shaheed Minar", "শহীদ মিনার", 23.8797, 90.2682,
                "LANDMARK", List.of("minar", "monument", "shohid minar"), true);

        cse = new Vertex("cse_building", "CSE Department", "সিএসই ভবন", 23.8774, 90.2663,
                "ACADEMIC", List.of("cse", "computer science"), true);

        trie.indexAll(List.of(dairyGate, bottola, shaheedMinar, cse));
    }

    @Test
    @DisplayName("Case-insensitive English prefix matching")
    void testSearch_EnglishPrefixCaseInsensitive() {
        List<Vertex> resultsUpper = trie.search("DAIRY", 5);
        assertFalse(resultsUpper.isEmpty());
        assertEquals("dairy_gate", resultsUpper.get(0).getId());

        List<Vertex> resultsMixed = trie.search("dAiR", 5);
        assertFalse(resultsMixed.isEmpty());
        assertEquals("dairy_gate", resultsMixed.get(0).getId());
    }

    @Test
    @DisplayName("Bangla Unicode prefix search")
    void testSearch_BanglaUnicodePrefix() {
        List<Vertex> results = trie.search("বট", 5);
        assertFalse(results.isEmpty());
        assertEquals("bottola", results.get(0).getId());

        List<Vertex> shaheedResults = trie.search("শহীদ", 5);
        assertFalse(shaheedResults.isEmpty());
        assertEquals("shaheed_minar", shaheedResults.get(0).getId());
    }

    @Test
    @DisplayName("Alias and acronym search")
    void testSearch_AliasAndAcronym() {
        List<Vertex> cseResults = trie.search("cse", 5);
        assertFalse(cseResults.isEmpty());
        assertEquals("cse_building", cseResults.get(0).getId());

        List<Vertex> minarResults = trie.search("minar", 5);
        assertFalse(minarResults.isEmpty());
        assertEquals("shaheed_minar", minarResults.get(0).getId());
    }

    @Test
    @DisplayName("Sub-token search in multi-word names")
    void testSearch_SubTokenMatching() {
        List<Vertex> gateResults = trie.search("Gate", 5);
        assertFalse(gateResults.isEmpty());
        assertTrue(gateResults.stream().anyMatch(v -> v.getId().equals("dairy_gate")));
    }

    @Test
    @DisplayName("Empty and blank queries return default popular locations")
    void testSearch_EmptyQueryReturnsDefaults() {
        List<Vertex> defaults = trie.search("", 10);
        assertEquals(4, defaults.size());

        List<Vertex> whitespace = trie.search("   ", 10);
        assertEquals(4, whitespace.size());
    }

    @Test
    @DisplayName("Unknown query returns empty list")
    void testSearch_UnknownQuery() {
        List<Vertex> results = trie.search("nonexistentplace999", 5);
        assertTrue(results.isEmpty());
    }
}

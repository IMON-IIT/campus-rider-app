package com.jusmartride.algorithms;

import com.jusmartride.graph.Vertex;

import java.util.*;

/**
 * Node in the Campus Trie supporting Unicode characters (English and Bengali).
 */
public class TrieNode {
    private final Map<Character, TrieNode> children = new HashMap<>();
    private final Set<Vertex> matchingLocations = new LinkedHashSet<>();
    private boolean isEndOfWord = false;

    public TrieNode() {
    }

    public Map<Character, TrieNode> getChildren() {
        return children;
    }

    public TrieNode getChild(char ch) {
        return children.get(ch);
    }

    public TrieNode computeIfAbsent(char ch) {
        return children.computeIfAbsent(ch, c -> new TrieNode());
    }

    public Set<Vertex> getMatchingLocations() {
        return matchingLocations;
    }

    public void addLocation(Vertex vertex) {
        if (vertex != null) {
            matchingLocations.add(vertex);
        }
    }

    public boolean isEndOfWord() {
        return isEndOfWord;
    }

    public void setEndOfWord(boolean endOfWord) {
        isEndOfWord = endOfWord;
    }
}

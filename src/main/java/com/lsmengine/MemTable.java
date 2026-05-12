package com.lsmengine;

import java.util.*;

public class MemTable {

    // Sentinel value representing a deleted key
    public static final String TOMBSTONE = "__TOMBSTONE__";
    // Flush threshold: 1 KB for testing (production engines use ~64 MB)
    private static final int MAX_SIZE_BYTES = 1024;

    // Sorted map keeps keys in ascending order automatically
    private final TreeMap<String, String> data;
    // Tracks approximate byte size of all stored keys and values
    private int currentSizeBytes;

    public MemTable() {
        this.data = new TreeMap<>();
        this.currentSizeBytes = 0;
    }
    public void put(String key, String value) {
        // Subtract old size if key already exists (prevents double-counting)
        String existing = data.get(key);
        if (existing != null) {
            currentSizeBytes -= (key.length() + existing.length());
        }
        // Insert into sorted tree and track new size
        data.put(key, value);
        currentSizeBytes += (key.length() + value.length());
    }

    public String get(String key) {
        return data.get(key);
    }

    public void delete(String key) {
        // A delete is just a put with the tombstone marker
        put(key, TOMBSTONE);
    }
    public int size() {
        return currentSizeBytes;
    }

    public boolean isFull() {
        // Returns true when stored data reaches the flush threshold
        return currentSizeBytes >= MAX_SIZE_BYTES;
    }
    public List<Map.Entry<String, String>> entries() {
        // Returns all key-value pairs in ascending key order
        return new ArrayList<>(data.entrySet());
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }
}
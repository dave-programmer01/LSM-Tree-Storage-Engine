package com.lsmengine;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class StorageEngine {

    private final Path dataDir;
    private WriteAheadLog wal;
    private MemTable memTable;
    private final List<Path> sstables;
    private int sstableCounter;

    public StorageEngine(Path dataDir) throws IOException {
        this.dataDir = dataDir;
        Files.createDirectories(dataDir);
        this.memTable = new MemTable();
        this.sstables = new ArrayList<>();
        this.sstableCounter = 0;

        // Discover any SSTables from previous runs
        loadExistingSSTables();

        // Open the WAL and replay any unflushed entries
        this.wal = new WriteAheadLog(dataDir);
        replayWAL();
    }

    private void loadExistingSSTables() throws IOException {
        if (!Files.exists(dataDir)) return;

        // Find all SSTable files, sorted by name (oldest first)
        List<Path> existingFiles = Files.list(dataDir)
                .filter(p -> p.getFileName().toString().startsWith("sstable_"))
                .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                .collect(Collectors.toList());

        sstables.addAll(existingFiles);

        // Set counter to continue after the last existing file
        if (!existingFiles.isEmpty()) {
            String lastFile = existingFiles.get(existingFiles.size() - 1)
                    .getFileName().toString();
            String numberPart = lastFile.replace("sstable_", "").replace(".dat", "");
            sstableCounter = Integer.parseInt(numberPart) + 1;
        }
    }

    private void replayWAL() throws IOException {
        // Restore MemTable state from WAL entries that survived a crash
        List<Map.Entry<String, String>> entries = wal.replay();
        for (Map.Entry<String, String> entry : entries) {
            memTable.put(entry.getKey(), entry.getValue());
        }
        if (!entries.isEmpty()) {
            System.out.println("Recovered " + entries.size() + " entries from WAL");
        }
    }
    public void put(String key, String value) throws IOException {
        // Durability first: WAL ensures this write survives a crash
        wal.append(key, value);
        memTable.put(key, value);
        checkFlush();
    }

    public void delete(String key) throws IOException {
        // Deletes are writes too: store a tombstone marker
        wal.append(key, MemTable.TOMBSTONE);
        memTable.delete(key);
        checkFlush();
    }

    private void checkFlush() throws IOException {
        if (memTable.isFull()) {
            flushMemTable();
        }
    }

    private void flushMemTable() throws IOException {
        if (memTable.isEmpty()) return;

        // Generate a unique filename with zero-padded counter
        String fileName = String.format("sstable_%05d.dat", sstableCounter++);
        Path sstablePath = dataDir.resolve(fileName);

        // Write sorted MemTable contents to a new SSTable file
        SSTable.flush(memTable.entries(), sstablePath);
        sstables.add(sstablePath);

        // WAL is no longer needed since data is safely in the SSTable
        wal.clear();
        memTable = new MemTable();

        System.out.println("Flushed MemTable to " + fileName);
    }

    public void close() throws IOException {
        wal.close();
    }
    public String get(String key) throws IOException {
        // Check MemTable first (fastest, most recent data)
        String value = memTable.get(key);
        if (value != null) {
            if (value.equals(MemTable.TOMBSTONE)) {
                return null;  // Key was deleted
            }
            return value;
        }

        // Search SSTables from newest to oldest
        for (int i = sstables.size() - 1; i >= 0; i--) {
            value = SSTable.get(key, sstables.get(i));
            if (value != null) {
                if (value.equals(MemTable.TOMBSTONE)) {
                    return null;  // Found a tombstone in an SSTable
                }
                return value;
            }
        }

        // Key does not exist anywhere
        return null;
    }
}
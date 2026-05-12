package com.lsmengine;

import java.io.IOException;
import java.nio.file.Path;

public class Main {

    // Directory where the engine stores WAL and SSTable files
    private static final Path DATA_DIR = Path.of("data");

    public static void main(String[] args) throws IOException {
        // Route to recovery mode if "recover" argument is passed
        if (args.length > 0 && args[0].equals("recover")) {
            recoverAndRead();
        } else {
            writeAndCrash();
        }
    }

    private static void writeAndCrash() throws IOException {
        StorageEngine engine = new StorageEngine(DATA_DIR);

        // Write four key-value pairs to the engine
        System.out.println("=== Writing data ===");
        engine.put("name", "Alice");
        engine.put("city", "Tokyo");
        engine.put("language", "Java");
        engine.put("project", "LSM-Engine");

        // Read them back to confirm they are in memory
        System.out.println("\n=== Reading back ===");
        System.out.println("name = " + engine.get("name"));
        System.out.println("city = " + engine.get("city"));
        System.out.println("language = " + engine.get("language"));
        System.out.println("project = " + engine.get("project"));

        // Close without flushing MemTable to SSTable
        System.out.println("\n=== Simulating crash (not flushing MemTable) ===");
        System.out.println("Data is in WAL but NOT in an SSTable yet.");
        System.out.println("Run with 'recover' argument to see WAL recovery in action.");

        engine.close();
    }
    private static void recoverAndRead() throws IOException {
        System.out.println("=== Opening engine (WAL recovery will happen) ===");
        // Creating a new StorageEngine triggers replayWAL() in the constructor
        StorageEngine engine = new StorageEngine(DATA_DIR);

        // Read back the same keys that were written before the crash
        System.out.println("\n=== Reading data after recovery ===");
        System.out.println("name = " + engine.get("name"));
        System.out.println("city = " + engine.get("city"));
        System.out.println("language = " + engine.get("language"));
        System.out.println("project = " + engine.get("project"));

        engine.close();
        System.out.println("\n=== Recovery successful! All data intact. ===");
    }
}
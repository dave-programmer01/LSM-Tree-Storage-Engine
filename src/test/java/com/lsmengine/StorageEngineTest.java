package com.lsmengine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageEngineTest {

    @TempDir
    Path dataDir;

    @Test
    void persistsValuesAndDeletesAcrossRestart() throws IOException {
        StorageEngine engine = new StorageEngine(dataDir);
        engine.put("user:1", "Alice");
        engine.put("user:2", "Bob");
        engine.delete("user:2");
        engine.close();

        StorageEngine reopened = new StorageEngine(dataDir);
        assertEquals("Alice", reopened.get("user:1"));
        assertNull(reopened.get("user:2"));
        reopened.close();
    }

    @Test
    void replaysUnflushedWalAfterRestart() throws IOException {
        StorageEngine engine = new StorageEngine(dataDir);
        engine.put("name", "Alice");
        engine.put("city", "Tokyo");
        engine.closeWithoutFlush();

        StorageEngine recovered = new StorageEngine(dataDir);
        assertEquals("Alice", recovered.get("name"));
        assertEquals("Tokyo", recovered.get("city"));
        recovered.close();
    }

    @Test
    void flushesAutomaticallyWhenMemTableReachesThreshold() throws IOException {
        StorageEngine engine = new StorageEngine(dataDir);
        for (int i = 0; i < 100; i++) {
            engine.put("key-" + i, "value-" + i + "-with-enough-data");
        }
        engine.close();

        try (var files = Files.list(dataDir)) {
            assertTrue(files.anyMatch(path ->
                    path.getFileName().toString().startsWith("sstable_")));
        }

        StorageEngine reopened = new StorageEngine(dataDir);
        assertEquals("value-99-with-enough-data", reopened.get("key-99"));
        reopened.close();
    }

    @Test
    void rejectsOperationsAfterClose() throws IOException {
        StorageEngine engine = new StorageEngine(dataDir);
        engine.close();

        assertThrows(IOException.class, () -> engine.put("key", "value"));
        assertThrows(IOException.class, () -> engine.get("key"));
    }
}

# LSM-Engine

A minimalist Log-Structured Merge-tree (LSM) storage engine implemented in Java. This project demonstrates the core concepts of modern key-value stores, including durability via Write-Ahead Logging (WAL), in-memory buffering with MemTables, and persistent sorted storage using SSTables.

## 🚀 Features

- **MemTable**: In-memory SkipList-like storage (using `TreeMap`) for fast writes and lookups.
- **Write-Ahead Log (WAL)**: Ensures durability by logging every write operation to disk before updating the MemTable.
- **SSTable (Sorted String Table)**: Persistent, immutable files storing sorted key-value pairs.
- **Crash Recovery**: Automatically replays the WAL on startup to restore data not yet flushed to SSTables.
- **Tombstones**: Supports deletions by marking keys with a special tombstone value.
- **Tiered Lookup**: Searches for keys in the MemTable first, then traverses SSTables from newest to oldest.

## 📁 Project Structure

```text
lsm-engine/
├── src/main/java/com/lsmengine/
│   ├── Main.java             # Entry point with demo scenarios
│   ├── StorageEngine.java    # Orchestrates WAL, MemTable, and SSTables
│   ├── MemTable.java         # In-memory data structure
│   ├── SSTable.java          # On-disk sorted storage logic
│   └── WriteAheadLog.java    # Durability layer
├── pom.xml                   # Maven configuration
└── data/                     # (Generated) Directory for WAL and SSTables
```

## 🛠️ How It Works

1.  **Write Path**:
    - Every `put` or `delete` is first appended to the `WriteAheadLog`.
    - The data is then updated in the `MemTable`.
    - When the `MemTable` reaches a certain threshold, it is flushed to a new `SSTable` file on disk, and the WAL is cleared.

2.  **Read Path**:
    - The engine first checks the `MemTable`.
    - If not found (or if a tombstone is encountered), it searches through `SSTables` in reverse chronological order.

3.  **Recovery**:
    - On initialization, the `StorageEngine` checks for existing WAL files and replays any entries into the `MemTable` to ensure no data is lost from a previous crash.

## 🚦 Getting Started

### Prerequisites
- JDK 11 or higher
- Maven

### Build
```bash
mvn clean compile
```

### Run the Demo

**1. Write data and simulate a crash:**
This will write several keys and "crash" before flushing the MemTable to disk.
```bash
mvn exec:java -Dexec.mainClass="com.lsmengine.Main"
```

**2. Recover data:**
This will restart the engine, replay the WAL, and successfully read back the data.
```bash
mvn exec:java -Dexec.mainClass="com.lsmengine.Main" -Dexec.args="recover"
```

## 📝 Example Usage

```java
StorageEngine engine = new StorageEngine(Path.of("data"));

// Write data
engine.put("user:123", "Alice");

// Read data
String value = engine.get("user:123");
System.out.println(value); // Alice

// Delete data
engine.delete("user:123");

engine.close();
```

## 📜 License
This project is for educational purposes. Feel free to use and modify it!

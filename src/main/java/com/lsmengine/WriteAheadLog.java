package com.lsmengine;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WriteAheadLog {

    private final Path walPath;

    private FileChannel channel;

    public  WriteAheadLog(Path dataDir) throws IOException {
        this.walPath = dataDir.resolve("wal.log");
        Files.createDirectories(dataDir);

        this.channel = FileChannel.open(walPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.READ);

    }

    public void append(String key, String value) throws IOException {

        byte[] keyBytes = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] valueBytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);


        ByteBuffer buffer = ByteBuffer.allocate(4 + keyBytes.length + 4 + valueBytes.length);
        buffer.putInt(keyBytes.length);
        buffer.put(keyBytes);
        buffer.putInt(valueBytes.length);
        buffer.put(valueBytes);
        buffer.flip();


        channel.write(buffer);
        channel.force(true);
    }

    public List<Map.Entry<String, String>> replay() throws IOException {
        List<Map.Entry<String, String>> entries = new ArrayList<>();

        // If the WAL file doesn't exist or is empty, nothing to replay
        if (!Files.exists(walPath) || Files.size(walPath) == 0) {
            return entries;
        }

        // Open a separate read-only channel to scan the file
        try (FileChannel readChannel = FileChannel.open(walPath, StandardOpenOption.READ)) {
            ByteBuffer sizeBuffer = ByteBuffer.allocate(4);

            // Read entries until we reach end-of-file
            while (readChannel.position() < readChannel.size()) {
                // Read the key length (4 bytes)
                sizeBuffer.clear();
                int bytesRead = readChannel.read(sizeBuffer);
                if (bytesRead < 4) break;
                sizeBuffer.flip();
                int keyLen = sizeBuffer.getInt();

                // Read the key bytes
                ByteBuffer keyBuffer = ByteBuffer.allocate(keyLen);
                bytesRead = readChannel.read(keyBuffer);
                if (bytesRead < keyLen) break;
                keyBuffer.flip();
                String key = new String(keyBuffer.array(), java.nio.charset.StandardCharsets.UTF_8);

                // Read the value length (4 bytes)
                sizeBuffer.clear();
                bytesRead = readChannel.read(sizeBuffer);
                if (bytesRead < 4) break;
                sizeBuffer.flip();
                int valueLen = sizeBuffer.getInt();

                // Read the value bytes
                ByteBuffer valueBuffer = ByteBuffer.allocate(valueLen);
                bytesRead = readChannel.read(valueBuffer);
                if (bytesRead < valueLen) break;
                valueBuffer.flip();
                String value = new String(valueBuffer.array(), java.nio.charset.StandardCharsets.UTF_8);

                entries.add(new AbstractMap.SimpleEntry<>(key, value));
            }
        }

        return entries;
    }

    public void clear() throws IOException {
        // Close the current channel before deleting the file
        channel.close();
        Files.deleteIfExists(walPath);
        // Reopen a fresh channel for future writes
        this.channel = FileChannel.open(walPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND);
    }

    public void close() throws IOException {
        channel.close();
    }
}

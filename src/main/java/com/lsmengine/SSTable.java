package com.lsmengine;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class SSTable {

    public static void flush(List<Map.Entry<String, String>> sortedEntries, Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {

            // Track each key's byte offset for the index
            Map<String, Long> index = new LinkedHashMap<>();
            long offset = 0;

            // Write each key-value pair sequentially
            for (Map.Entry<String, String> entry : sortedEntries) {
                byte[] keyBytes = entry.getKey().getBytes(StandardCharsets.UTF_8);
                byte[] valueBytes = entry.getValue().getBytes(StandardCharsets.UTF_8);

                // Record this key's position before writing
                index.put(entry.getKey(), offset);

                // Format: [4-byte key length][key bytes][4-byte value length][value bytes]
                ByteBuffer buffer = ByteBuffer.allocate(4 + keyBytes.length + 4 + valueBytes.length);
                buffer.putInt(keyBytes.length);
                buffer.put(keyBytes);
                buffer.putInt(valueBytes.length);
                buffer.put(valueBytes);
                buffer.flip();

                writeFully(channel, buffer);
                offset += 4 + keyBytes.length + 4 + valueBytes.length;
            }

            // Remember where the index section starts
            long indexOffset = offset;

            // Write the number of index entries
            ByteBuffer countBuffer = ByteBuffer.allocate(4);
            countBuffer.putInt(index.size());
            countBuffer.flip();
            writeFully(channel, countBuffer);

            // Write each index entry: [4-byte key length][key bytes][8-byte offset]
            for (Map.Entry<String, Long> indexEntry : index.entrySet()) {
                byte[] keyBytes = indexEntry.getKey().getBytes(StandardCharsets.UTF_8);
                ByteBuffer indexBuffer = ByteBuffer.allocate(4 + keyBytes.length + 8);
                indexBuffer.putInt(keyBytes.length);
                indexBuffer.put(keyBytes);
                indexBuffer.putLong(indexEntry.getValue());
                indexBuffer.flip();
                writeFully(channel, indexBuffer);
            }

            // Write the footer: 8 bytes pointing to where the index starts
            ByteBuffer footerBuffer = ByteBuffer.allocate(8);
            footerBuffer.putLong(indexOffset);
            footerBuffer.flip();
            writeFully(channel, footerBuffer);

            // Ensure everything reaches disk
            channel.force(true);
        }
    }
    public static String get(String key, Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            long fileSize = channel.size();

            // Read the footer to find where the index starts
            ByteBuffer footerBuffer = ByteBuffer.allocate(8);
            channel.position(fileSize - 8);
            readFully(channel, footerBuffer);
            footerBuffer.flip();
            long indexOffset = footerBuffer.getLong();

            // Jump to the index section
            channel.position(indexOffset);

            // Read how many entries are in the index
            ByteBuffer countBuffer = ByteBuffer.allocate(4);
            readFully(channel, countBuffer);
            countBuffer.flip();
            int entryCount = countBuffer.getInt();

            // Scan the index for the target key
            long dataOffset = -1;
            ByteBuffer sizeBuffer = ByteBuffer.allocate(4);

            for (int i = 0; i < entryCount; i++) {
                sizeBuffer.clear();
                readFully(channel, sizeBuffer);
                sizeBuffer.flip();
                int keyLen = sizeBuffer.getInt();

                ByteBuffer keyBuffer = ByteBuffer.allocate(keyLen);
                readFully(channel, keyBuffer);
                keyBuffer.flip();
                String indexKey = new String(keyBuffer.array(), StandardCharsets.UTF_8);

                ByteBuffer offsetBuffer = ByteBuffer.allocate(8);
                readFully(channel, offsetBuffer);
                offsetBuffer.flip();
                long entryOffset = offsetBuffer.getLong();

                // Stop scanning once we find the target key
                if (indexKey.equals(key)) {
                    dataOffset = entryOffset;
                    break;
                }
            }

            // Key not found in this SSTable
            if (dataOffset == -1) {
                return null;
            }

            // Jump to the data entry and read the value
            channel.position(dataOffset);

            sizeBuffer.clear();
            readFully(channel, sizeBuffer);
            sizeBuffer.flip();
            int keyLen = sizeBuffer.getInt();

            // Skip past the key bytes
            ByteBuffer keyBuffer = ByteBuffer.allocate(keyLen);
            readFully(channel, keyBuffer);

            // Read the value length and value bytes
            sizeBuffer.clear();
            readFully(channel, sizeBuffer);
            sizeBuffer.flip();
            int valueLen = sizeBuffer.getInt();

            ByteBuffer valueBuffer = ByteBuffer.allocate(valueLen);
            readFully(channel, valueBuffer);
            valueBuffer.flip();

            return new String(valueBuffer.array(), StandardCharsets.UTF_8);
        }
    }

    private static void writeFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    private static void readFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
                throw new IOException("Unexpected end of SSTable");
            }
        }
    }
}
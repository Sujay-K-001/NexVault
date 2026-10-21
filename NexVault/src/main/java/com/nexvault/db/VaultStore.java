package com.nexvault.db;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Persistence boundary. Implementations only ever see ciphertext, never keys or plaintext secrets. */
public interface VaultStore extends AutoCloseable {
    record Row(String id, long createdAt, long updatedAt, byte[] data) {}
    record ActivityRow(long id, long ts, byte[] data) {}

    Optional<byte[]> getMeta(String key);
    void putMeta(String key, byte[] value);

    List<Row> listEntries();
    void putEntry(Row row);
    boolean deleteEntry(String id);

    void addActivity(long ts, byte[] data, int keep);
    List<ActivityRow> recentActivity(int limit);
    List<ActivityRow> allActivity();

    /** Atomically replaces meta values and re-encrypted entry/activity payloads (master password change). */
    void rekey(Map<String, byte[]> meta, Map<String, byte[]> entryData, Map<Long, byte[]> activityData);

    /** Erases the vault (entries, activity, keys/metadata). Settings such as the theme are kept. */
    void wipe();

    Optional<String> getSetting(String key);
    void putSetting(String key, String value);

    @Override void close();
}

package com.nexvault.db;

import java.util.*;

/** Non-persistent store used by the automated tests and the --dev-memory-store flag. Never use for real data. */
public final class InMemoryStore implements VaultStore {
    private final Map<String, byte[]> meta = new HashMap<>();
    private final Map<String, Row> entries = new LinkedHashMap<>();
    private final TreeMap<Long, ActivityRow> activity = new TreeMap<>();
    private final Map<String, String> settings = new HashMap<>();
    private long nextId = 1;

    @Override public synchronized Optional<byte[]> getMeta(String k) { return Optional.ofNullable(meta.get(k)).map(byte[]::clone); }
    @Override public synchronized void putMeta(String k, byte[] v) { meta.put(k, v.clone()); }
    @Override public synchronized List<Row> listEntries() { return new ArrayList<>(entries.values()); }
    @Override public synchronized void putEntry(Row r) { entries.put(r.id(), new Row(r.id(), r.createdAt(), r.updatedAt(), r.data().clone())); }
    @Override public synchronized boolean deleteEntry(String id) { return entries.remove(id) != null; }

    @Override public synchronized void addActivity(long ts, byte[] data, int keep) {
        long id = nextId++;
        activity.put(id, new ActivityRow(id, ts, data.clone()));
        while (activity.size() > keep) activity.pollFirstEntry();
    }

    @Override public synchronized List<ActivityRow> recentActivity(int limit) {
        List<ActivityRow> out = new ArrayList<>();
        for (ActivityRow r : activity.descendingMap().values()) { if (out.size() >= limit) break; out.add(r); }
        return out;
    }

    @Override public synchronized List<ActivityRow> allActivity() { return recentActivity(Integer.MAX_VALUE); }

    @Override public synchronized void rekey(Map<String, byte[]> m, Map<String, byte[]> e, Map<Long, byte[]> a) {
        m.forEach(this::putMeta);
        e.forEach((id, d) -> { Row r = entries.get(id); if (r != null) entries.put(id, new Row(id, r.createdAt(), r.updatedAt(), d.clone())); });
        a.forEach((id, d) -> { ActivityRow r = activity.get(id); if (r != null) activity.put(id, new ActivityRow(id, r.ts(), d.clone())); });
    }

    @Override public synchronized void wipe() { meta.clear(); entries.clear(); activity.clear(); }

    @Override public synchronized Optional<String> getSetting(String k) { return Optional.ofNullable(settings.get(k)); }
    @Override public synchronized void putSetting(String k, String v) { settings.put(k, v); }
    @Override public void close() { }
}

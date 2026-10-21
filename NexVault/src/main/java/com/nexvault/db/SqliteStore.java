package com.nexvault.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.*;
import java.util.*;

/** SQLite-backed store (requires the sqlite-jdbc driver on the classpath). */
public final class SqliteStore implements VaultStore {
    private final Connection db;

    public SqliteStore(Path file) {
        try {
            Class.forName("org.sqlite.JDBC");
            db = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
            try (Statement s = db.createStatement()) {
                s.execute("PRAGMA secure_delete = ON");   // overwrite deleted content with zeros
                s.execute("PRAGMA synchronous = FULL");
                s.execute("PRAGMA foreign_keys = ON");
                for (String stmt : loadSchema()) s.execute(stmt);
            }
            try { Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------")); }
            catch (UnsupportedOperationException | IOException ignored) { /* Windows: inherits user-profile ACLs */ }
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("SQLite driver (sqlite-jdbc) not found on the classpath. Run run-vault.bat to fetch it.", e);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not open the vault database: " + e.getMessage(), e);
        }
    }

    private static List<String> loadSchema() {
        try (InputStream in = SqliteStore.class.getResourceAsStream("/schema.sql")) {
            if (in == null) throw new IllegalStateException("schema.sql missing from the application resources");
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            List<String> out = new ArrayList<>();
            for (String part : sql.split(";")) {
                StringBuilder b = new StringBuilder();
                for (String line : part.split("\n")) if (!line.trim().startsWith("--")) b.append(line).append('\n');
                if (!b.toString().isBlank()) out.add(b.toString().trim());
            }
            return out;
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    private static IllegalStateException fail(SQLException e) { return new IllegalStateException("Database error: " + e.getMessage(), e); }

    @Override public synchronized Optional<byte[]> getMeta(String key) {
        try (PreparedStatement p = db.prepareStatement("SELECT value FROM meta WHERE key = ?")) {
            p.setString(1, key);
            try (ResultSet r = p.executeQuery()) { return r.next() ? Optional.of(r.getBytes(1)) : Optional.empty(); }
        } catch (SQLException e) { throw fail(e); }
    }

    @Override public synchronized void putMeta(String key, byte[] value) {
        try (PreparedStatement p = db.prepareStatement("INSERT INTO meta(key, value) VALUES(?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            p.setString(1, key); p.setBytes(2, value); p.executeUpdate();
        } catch (SQLException e) { throw fail(e); }
    }

    @Override public synchronized List<Row> listEntries() {
        List<Row> out = new ArrayList<>();
        try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT id, created_at, updated_at, data FROM entries ORDER BY created_at")) {
            while (r.next()) out.add(new Row(r.getString(1), r.getLong(2), r.getLong(3), r.getBytes(4)));
        } catch (SQLException e) { throw fail(e); }
        return out;
    }

    @Override public synchronized void putEntry(Row row) {
        try (PreparedStatement p = db.prepareStatement(
                "INSERT INTO entries(id, created_at, updated_at, data) VALUES(?, ?, ?, ?) "
                        + "ON CONFLICT(id) DO UPDATE SET updated_at = excluded.updated_at, data = excluded.data")) {
            p.setString(1, row.id()); p.setLong(2, row.createdAt()); p.setLong(3, row.updatedAt()); p.setBytes(4, row.data());
            p.executeUpdate();
        } catch (SQLException e) { throw fail(e); }
    }

    @Override public synchronized boolean deleteEntry(String id) {
        try (PreparedStatement p = db.prepareStatement("DELETE FROM entries WHERE id = ?")) {
            p.setString(1, id);
            return p.executeUpdate() > 0;
        } catch (SQLException e) { throw fail(e); }
    }

    @Override public synchronized void addActivity(long ts, byte[] data, int keep) {
        try (PreparedStatement p = db.prepareStatement("INSERT INTO activity(ts, data) VALUES(?, ?)")) {
            p.setLong(1, ts); p.setBytes(2, data); p.executeUpdate();
        } catch (SQLException e) { throw fail(e); }
        try (PreparedStatement p = db.prepareStatement("DELETE FROM activity WHERE id NOT IN (SELECT id FROM activity ORDER BY id DESC LIMIT ?)")) {
            p.setInt(1, keep); p.executeUpdate();
        } catch (SQLException e) { throw fail(e); }
    }

    @Override public synchronized List<ActivityRow> recentActivity(int limit) {
        List<ActivityRow> out = new ArrayList<>();
        try (PreparedStatement p = db.prepareStatement("SELECT id, ts, data FROM activity ORDER BY id DESC LIMIT ?")) {
            p.setInt(1, limit);
            try (ResultSet r = p.executeQuery()) { while (r.next()) out.add(new ActivityRow(r.getLong(1), r.getLong(2), r.getBytes(3))); }
        } catch (SQLException e) { throw fail(e); }
        return out;
    }

    @Override public synchronized List<ActivityRow> allActivity() {
        return recentActivity(Integer.MAX_VALUE);
    }

    @Override public synchronized void rekey(Map<String, byte[]> meta, Map<String, byte[]> entryData, Map<Long, byte[]> activityData) {
        try {
            db.setAutoCommit(false);
            try {
                for (var m : meta.entrySet()) putMeta(m.getKey(), m.getValue());
                try (PreparedStatement p = db.prepareStatement("UPDATE entries SET data = ? WHERE id = ?")) {
                    for (var e : entryData.entrySet()) { p.setBytes(1, e.getValue()); p.setString(2, e.getKey()); p.addBatch(); }
                    p.executeBatch();
                }
                try (PreparedStatement p = db.prepareStatement("UPDATE activity SET data = ? WHERE id = ?")) {
                    for (var e : activityData.entrySet()) { p.setBytes(1, e.getValue()); p.setLong(2, e.getKey()); p.addBatch(); }
                    p.executeBatch();
                }
                db.commit();
            } catch (SQLException | RuntimeException e) {
                db.rollback();
                throw e;
            } finally {
                db.setAutoCommit(true);
            }
        } catch (SQLException e) { throw fail(e); }
    }

    @Override public synchronized void wipe() {
        try {
            db.setAutoCommit(false);
            try (Statement st = db.createStatement()) {
                st.executeUpdate("DELETE FROM entries");
                st.executeUpdate("DELETE FROM activity");
                st.executeUpdate("DELETE FROM meta");
                db.commit();
            } catch (SQLException e) { db.rollback(); throw e; }
            finally { db.setAutoCommit(true); }
        } catch (SQLException e) { throw fail(e); }
    }

    @Override public synchronized Optional<String> getSetting(String key) {
        try (PreparedStatement p = db.prepareStatement("SELECT value FROM settings WHERE key = ?")) {
            p.setString(1, key);
            try (ResultSet r = p.executeQuery()) { return r.next() ? Optional.of(r.getString(1)) : Optional.empty(); }
        } catch (SQLException e) { throw fail(e); }
    }

    @Override public synchronized void putSetting(String key, String value) {
        try (PreparedStatement p = db.prepareStatement("INSERT INTO settings(key, value) VALUES(?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            p.setString(1, key); p.setString(2, value); p.executeUpdate();
        } catch (SQLException e) { throw fail(e); }
    }

    @Override public synchronized void close() {
        try { db.close(); } catch (SQLException ignored) { }
    }
}

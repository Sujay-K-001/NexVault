package com.nexvault.vault;

import com.nexvault.crypto.CryptoService;
import com.nexvault.db.VaultStore;
import com.nexvault.util.Json;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.LongSupplier;

/**
 * Business logic: master-password setup, unlock/lock, entry CRUD, security audit and settings.
 * Decrypted passwords and notes are never cached: only searchable metadata and a keyed fingerprint
 * (for reuse detection) live in memory while the vault is unlocked. Everything is wiped on lock.
 */
public final class VaultService {
    public static final List<String> CATEGORIES =
            List.of("Social", "Email", "Finance", "Shopping", "Work", "Entertainment", "Development", "Other");
    public static final int STALE_DAYS = 180;
    private static final int ACTIVITY_KEEP = 500;
    private static final byte[] ACTIVITY_AAD = "nexvault:activity".getBytes(StandardCharsets.UTF_8);

    public record Meta(String id, String service, String username, String url, String category, long createdAt,
                       long updatedAt, int strength, String fp, boolean hasNotes) {}

    public record Input(String service, String username, String password, String url, String notes, String category) {}

    private final VaultStore store;
    private final CryptoService crypto;
    private final LongSupplier clock;

    private byte[] key;
    private byte[] fpKey;
    private final Map<String, Meta> index = new LinkedHashMap<>();
    private final Map<String, byte[]> blobs = new HashMap<>();
    private int corrupt;
    private long lastActivity;
    private SecurityEvents events = SecurityEvents.NONE;
    private long lockoutMs = 3_600_000L;
    public static final int MAX_ATTEMPTS = 5, ALERT_AFTER = 3;
    private Runnable onLock = () -> { };

    public VaultService(VaultStore store, CryptoService crypto, LongSupplier clock) {
        this.store = store;
        this.crypto = crypto;
        this.clock = clock;
    }

    public VaultService(VaultStore store, CryptoService crypto) { this(store, crypto, System::currentTimeMillis); }

    public synchronized void setEvents(SecurityEvents e) { this.events = e == null ? SecurityEvents.NONE : e; }
    /** Test/dev hook: shortens the lockout so it can be observed without waiting an hour. */
    public synchronized void setLockoutMillis(long ms) { this.lockoutMs = ms; }

    public synchronized void setOnLock(Runnable r) { this.onLock = r == null ? () -> { } : r; }

    // ------------------------------------------------------------------ state

    public synchronized boolean isInitialized() {
        return store.getMeta("wrapped_dek").isPresent() && store.getMeta("salt").isPresent();
    }

    public synchronized boolean isUnlocked() { return key != null; }

    private void requireUnlocked() {
        if (key == null) throw new VaultException(423, "The vault is locked.", true);
        lastActivity = clock.getAsLong();
    }

    /** Throws 423 unless unlocked; counts as user activity. */
    public synchronized void assertUnlocked() { requireUnlocked(); }

    public synchronized void touch() {
        if (key != null) lastActivity = clock.getAsLong();
    }

    public synchronized long secondsUntilLock() {
        if (key == null) return 0;
        long limit = autoLockMinutes() * 60_000L;
        return Math.max(0, (limit - (clock.getAsLong() - lastActivity) + 999) / 1000);
    }

    /** Called periodically by a scheduler; locks the vault after the inactivity timeout. */
    public synchronized void checkAutoLock() {
        if (key != null && clock.getAsLong() - lastActivity >= autoLockMinutes() * 60_000L) lock("Vault auto-locked (inactivity)");
    }

    // ------------------------------------------------------- master password
    //
    // Key hierarchy: a random 256-bit data key (DEK) encrypts the vault. The DEK is stored wrapped (AES-256-GCM)
    // under a key derived from the master password (PBKDF2WithHmacSHA256, 200k iterations, unique salt), and a
    // second time under a key derived from the one-time recovery key. Changing the master password only re-wraps
    // the DEK, so the recovery key keeps working.

    private static final int ITER = CryptoService.DEFAULT_ITERATIONS;
    private static final byte[] DEK_AAD = "nexvault:dek".getBytes(StandardCharsets.UTF_8);
    private static final byte[] RECOVERY_AAD = "nexvault:dek-recovery".getBytes(StandardCharsets.UTF_8);
    private static final byte[] BREACH_AAD = "nexvault:breach".getBytes(StandardCharsets.UTF_8);
    private static final String B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private static byte[] b(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    private void validateMaster(char[] pw) {
        if (pw == null || pw.length < 10) throw new VaultException(400, "The master password must be at least 10 characters.");
        if (pw.length > 256) throw new VaultException(400, "The master password must be at most 256 characters.");
        PasswordTools.Strength s = PasswordTools.strength(new String(pw));
        if (s.score() < 2) {
            String why = s.feedback().isEmpty() ? "" : " " + s.feedback().get(0);
            throw new VaultException(400, "That master password is too weak (" + s.label().toLowerCase() + ")." + why);
        }
    }

    /** Creates the vault and returns the one-time recovery key (shown to the user once). */
    public synchronized String setup(char[] password) {
        if (isInitialized()) throw new VaultException(409, "A vault already exists.");
        validateMaster(password);
        byte[] salt = crypto.randomBytes(CryptoService.SALT_BYTES);
        byte[] kek = crypto.deriveKey(password, salt, ITER);
        byte[] dek = crypto.randomBytes(CryptoService.KEY_BYTES);
        Map<String, byte[]> meta = new HashMap<>();
        meta.put("version", b("2"));
        meta.put("kdf", b(CryptoService.KDF));
        meta.put("kdf_iter", b(Integer.toString(ITER)));
        meta.put("salt", salt);
        meta.put("wrapped_dek", crypto.encrypt(kek, dek, DEK_AAD));
        CryptoService.wipe(kek);
        String recovery = addRecovery(meta, dek);
        store.rekey(meta, Map.of(), Map.of());   // one transaction: the vault either exists completely or not at all
        startSession(dek);
        log("Vault created", "");
        return recovery;
    }

    private String addRecovery(Map<String, byte[]> meta, byte[] dek) {
        byte[] raw = crypto.randomBytes(20);   // 160 bits
        String key = encodeKey(raw);
        CryptoService.wipe(raw);
        char[] norm = normalizeKey(key.toCharArray());
        byte[] rsalt = crypto.randomBytes(CryptoService.SALT_BYTES);
        byte[] rk = crypto.deriveKey(norm, rsalt, ITER);
        meta.put("recovery_salt", rsalt);
        meta.put("recovery_dek", crypto.encrypt(rk, dek, RECOVERY_AAD));
        CryptoService.wipe(rk);
        CryptoService.wipe(norm);
        return key;
    }

    static String encodeKey(byte[] raw) {
        StringBuilder sb = new StringBuilder();
        int buf = 0, bits = 0, n = 0;
        for (byte x : raw) {
            buf = (buf << 8) | (x & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(B32.charAt((buf >> (bits - 5)) & 31));
                bits -= 5;
                buf &= (1 << bits) - 1;
                if (++n % 4 == 0 && n < 32) sb.append('-');
            }
        }
        return sb.toString();
    }

    /** Uppercases, drops separators/whitespace and forgives look-alikes (0->O, 1->I, 8->B). */
    static char[] normalizeKey(char[] in) {
        char[] out = new char[in.length];
        int n = 0;
        for (char c : in) {
            char u = Character.toUpperCase(c);
            if (u == '0') u = 'O'; else if (u == '1') u = 'I'; else if (u == '8') u = 'B';
            if (B32.indexOf(u) >= 0) out[n++] = u;
        }
        char[] r = Arrays.copyOf(out, n);
        CryptoService.wipe(out);
        return r;
    }

    // ---- persisted attempt counter and lockout (survives restarts: stored in the settings table)

    private long longSetting(String k) {
        try { return Long.parseLong(store.getSetting(k).orElse("0")); } catch (NumberFormatException e) { return 0; }
    }

    public synchronized int failedAttempts() { return (int) longSetting("failedAttempts"); }

    /** Remaining lockout in whole seconds (0 = not locked out). Also clears an expired lockout. */
    public synchronized long lockoutSeconds() {
        long until = longSetting("lockoutUntil");
        if (until == 0) return 0;
        long start = longSetting("lockoutStart"), now = clock.getAsLong();
        long remaining = now < start ? until - start : until - now;   // system clock set back: still serve the full period
        if (remaining <= 0) { clearAttempts(); return 0; }
        return (remaining + 999) / 1000;
    }

    private void clearAttempts() {
        store.putSetting("failedAttempts", "0");
        store.putSetting("lockoutUntil", "0");
        store.putSetting("lockoutStart", "0");
    }

    private static String duration(long secs) { return secs >= 3600 ? (secs / 3600) + " hour" + (secs >= 7200 ? "s" : "") : secs >= 60 ? (secs / 60) + " minutes" : secs + " seconds"; }

    private void throttle() {
        long s = lockoutSeconds();
        if (s > 0) throw new VaultException(429, "The vault is locked for security. Try again later.").with("lockoutSeconds", s).with("attempts", failedAttempts());
    }

    /** Records one more consecutive failure, applies the lockout at 5, and raises the alert (e-mail) after the 3rd. */
    private VaultException failure(String message) {
        int f = failedAttempts() + 1;
        store.putSetting("failedAttempts", Integer.toString(f));
        boolean locked = f >= MAX_ATTEMPTS;
        long secs = 0;
        if (locked) {
            long now = clock.getAsLong();
            store.putSetting("lockoutStart", Long.toString(now));
            store.putSetting("lockoutUntil", Long.toString(now + lockoutMs));
            secs = (lockoutMs + 999) / 1000;
        }
        if (f > ALERT_AFTER) {
            try { events.failedAttempts(f, locked, secs); } catch (RuntimeException ignored) { }
        }
        if (locked) return new VaultException(429, "Too many incorrect attempts. The vault is locked for " + duration(secs) + ".").with("lockoutSeconds", secs).with("attempts", f);
        return new VaultException(401, message).with("attempts", f).with("attemptsLeft", MAX_ATTEMPTS - f);
    }

    /** Returns the data key if the password is right, otherwise null. */
    private byte[] unwrapWith(char[] password) {
        byte[] salt = store.getMeta("salt").orElseThrow();
        int iter = Integer.parseInt(new String(store.getMeta("kdf_iter").orElseThrow(), StandardCharsets.UTF_8));
        byte[] kek = crypto.deriveKey(password, salt, iter);
        try {
            return crypto.decrypt(kek, store.getMeta("wrapped_dek").orElseThrow(), DEK_AAD);
        } catch (GeneralSecurityException e) {
            return null;
        } finally {
            CryptoService.wipe(kek);
        }
    }

    public synchronized void unlock(char[] password) {
        if (!isInitialized()) throw new VaultException(409, "No vault exists yet.");
        throttle();
        if (key != null) lock(null);
        byte[] dek = unwrapWith(password);
        if (dek == null) throw failure("Incorrect master password.");
        clearAttempts();
        startSession(dek);
        log("Vault unlocked", "");
    }

    private void startSession(byte[] dek) {
        key = dek;
        fpKey = crypto.randomBytes(32);
        index.clear();
        blobs.clear();
        corrupt = 0;
        for (VaultStore.Row r : store.listEntries()) {
            try {
                Map<String, Object> d = open(r.id(), r.data());
                index.put(r.id(), toMeta(r.id(), r.createdAt(), r.updatedAt(), d));
                blobs.put(r.id(), r.data());
            } catch (GeneralSecurityException | RuntimeException e) {
                corrupt++;
            }
        }
        loadBreach();
        lastActivity = clock.getAsLong();
    }

    public synchronized void lock(String reason) {
        if (key == null) return;
        if (reason != null) log(reason.startsWith("Vault") ? reason : "Vault locked", "");
        CryptoService.wipe(key);
        CryptoService.wipe(fpKey);
        key = null;
        fpKey = null;
        index.clear();
        blobs.clear();
        breached.clear();
        try { onLock.run(); } catch (RuntimeException ignored) { }
    }

    private void rewrap(char[] newMaster, byte[] dek) {
        byte[] salt = crypto.randomBytes(CryptoService.SALT_BYTES);
        byte[] kek = crypto.deriveKey(newMaster, salt, ITER);
        Map<String, byte[]> meta = new HashMap<>();
        meta.put("salt", salt);
        meta.put("kdf_iter", b(Integer.toString(ITER)));
        meta.put("wrapped_dek", crypto.encrypt(kek, dek, DEK_AAD));
        CryptoService.wipe(kek);
        store.rekey(meta, Map.of(), Map.of());
    }

    public synchronized void changeMaster(char[] current, char[] next) {
        requireUnlocked();
        byte[] check = unwrapWith(current);
        if (check == null) throw new VaultException(401, "Your current master password is incorrect.");
        CryptoService.wipe(check);
        if (Arrays.equals(current, next)) throw new VaultException(400, "The new master password must be different.");
        validateMaster(next);
        rewrap(next, key);
        log("Master password changed", "");
    }

    // ---- recovery options

    public synchronized boolean hasRecovery() { return store.getMeta("recovery_dek").isPresent(); }

    /** Replaces the recovery key (the old one stops working). Requires the master password. */
    public synchronized String regenerateRecoveryKey(char[] master) {
        requireUnlocked();
        byte[] check = unwrapWith(master);
        if (check == null) throw new VaultException(401, "Incorrect master password.");
        CryptoService.wipe(check);
        Map<String, byte[]> meta = new HashMap<>();
        String k = addRecovery(meta, key);
        store.rekey(meta, Map.of(), Map.of());
        log("Recovery key replaced", "");
        return k;
    }

    /** Forgot the master password: unlock with the recovery key and choose a new master password. */
    public synchronized void recover(char[] recoveryKey, char[] newMaster) {
        if (!isInitialized()) throw new VaultException(409, "No vault exists yet.");
        throttle();
        if (!hasRecovery()) throw new VaultException(400, "This vault has no recovery key.");
        char[] norm = normalizeKey(recoveryKey);
        byte[] dek = null;
        if (norm.length == 32) {
            byte[] rk = crypto.deriveKey(norm, store.getMeta("recovery_salt").orElseThrow(), ITER);
            try { dek = crypto.decrypt(rk, store.getMeta("recovery_dek").orElseThrow(), RECOVERY_AAD); }
            catch (GeneralSecurityException ignored) { /* wrong key */ }
            finally { CryptoService.wipe(rk); }
        }
        CryptoService.wipe(norm);
        if (dek == null) throw failure("That recovery key is not valid.");
        try { validateMaster(newMaster); } catch (VaultException e) { CryptoService.wipe(dek); throw e; }
        if (key != null) lock(null);
        rewrap(newMaster, dek);
        clearAttempts();
        startSession(dek);
        log("Master password reset with recovery key", "");
    }

    /** Last resort: erases the whole vault so the user can start fresh. */
    public synchronized void resetVault(String confirm) {
        throttle();
        if (!"ERASE".equals(confirm)) throw new VaultException(400, "Type ERASE to confirm.");
        lock(null);
        store.wipe();
        clearAttempts();
    }

    public synchronized String hint() { return store.getSetting("masterHint").orElse(""); }

    // ---- breach monitoring state (encrypted at rest)

    private final Map<String, Integer> breached = new HashMap<>();
    private long breachChecked;

    private void requireKey() {
        if (key == null) throw new VaultException(423, "The vault is locked.", true);
    }

    private void loadBreach() {
        breached.clear();
        breachChecked = 0;
        store.getMeta("breach").ifPresent(blob -> {
            try {
                byte[] p = crypto.decrypt(key, blob, BREACH_AAD);
                Map<String, Object> m = Json.parseObject(new String(p, StandardCharsets.UTF_8));
                CryptoService.wipe(p);
                if (m.get("checkedAt") instanceof Number n) breachChecked = n.longValue();
                if (m.get("results") instanceof Map<?, ?> r) {
                    for (Map.Entry<?, ?> e : r.entrySet()) {
                        String id = String.valueOf(e.getKey());
                        if (e.getValue() instanceof Number n && index.containsKey(id)) breached.put(id, n.intValue());
                    }
                }
            } catch (GeneralSecurityException | RuntimeException ignored) { }
        });
    }

    private void saveBreach() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("checkedAt", breachChecked);
        m.put("results", breached);
        byte[] plain = Json.write(m).getBytes(StandardCharsets.UTF_8);
        store.putMeta("breach", crypto.encrypt(key, plain, BREACH_AAD));
        CryptoService.wipe(plain);
    }

    /** SHA-1 (uppercase hex) of every password, keyed by entry id. Used only to build k-anonymity lookups. */
    public synchronized Map<String, String> sha1Hashes() {
        requireKey();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            Map<String, String> out = new LinkedHashMap<>();
            for (String id : index.keySet()) {
                String pw = s(decrypted(id), "password");
                out.put(id, HexFormat.of().withUpperCase().formatHex(md.digest(pw.getBytes(StandardCharsets.UTF_8))));
            }
            return out;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public synchronized Map<String, Object> recordBreaches(Map<String, Integer> counts) {
        requireKey();
        breached.clear();
        counts.forEach((id, c) -> { if (c > 0 && index.containsKey(id)) breached.put(id, c); });
        breachChecked = clock.getAsLong();
        saveBreach();
        log("Breach scan", breached.isEmpty() ? "No exposed passwords" : breached.size() + " exposed");
        return breachStatus();
    }

    public synchronized Map<String, Object> breachStatus() {
        requireKey();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, Integer> e : breached.entrySet()) {
            Meta m = index.get(e.getKey());
            if (m == null) continue;
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", m.id()); o.put("service", m.service()); o.put("username", m.username()); o.put("url", m.url()); o.put("count", e.getValue());
            list.add(o);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("checkedAt", breachChecked);
        out.put("scanned", index.size());
        out.put("breached", list);
        out.put("auto", breachAuto());
        return out;
    }

    public synchronized boolean breachDue() {
        return key != null && breachAuto() && !index.isEmpty() && clock.getAsLong() - breachChecked > 7 * 86_400_000L;
    }

    public synchronized boolean breachAuto() { return "1".equals(store.getSetting("breachAuto").orElse("0")); }

    // ---------------------------------------------------------------- entries

    private static byte[] aad(String id) { return ("nexvault:entry:" + id).getBytes(StandardCharsets.UTF_8); }

    private Map<String, Object> open(String id, byte[] blob) throws GeneralSecurityException {
        byte[] plain = crypto.decrypt(key, blob, aad(id));
        try {
            return Json.parseObject(new String(plain, StandardCharsets.UTF_8));
        } finally {
            CryptoService.wipe(plain);
        }
    }

    private static String s(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? "" : v.toString();
    }

    private Meta toMeta(String id, long created, long updated, Map<String, Object> d) {
        String pw = s(d, "password");
        int strength = PasswordTools.strength(pw).score();
        String fp = pw.isEmpty() ? "" : HexFormat.of().formatHex(crypto.hmacSha256(fpKey, pw.getBytes(StandardCharsets.UTF_8)));
        return new Meta(id, s(d, "service"), s(d, "username"), s(d, "url"), s(d, "category"), created, updated,
                strength, fp, !s(d, "notes").isEmpty());
    }

    private Input validate(Input in, boolean passwordRequired) {
        String service = in.service() == null ? "" : in.service().trim();
        if (service.isEmpty()) throw new VaultException(400, "Please enter a service name.");
        if (service.length() > 100) throw new VaultException(400, "The service name is too long (max 100 characters).");
        String username = in.username() == null ? "" : in.username().trim();
        if (username.length() > 200) throw new VaultException(400, "The username is too long (max 200 characters).");
        String pw = in.password();
        if (passwordRequired && (pw == null || pw.isEmpty())) throw new VaultException(400, "Please enter a password.");
        if (pw != null && pw.length() > 1024) throw new VaultException(400, "The password is too long (max 1024 characters).");
        String notes = in.notes() == null ? "" : in.notes();
        if (notes.length() > 5000) throw new VaultException(400, "Notes are too long (max 5000 characters).");
        String category = CATEGORIES.contains(in.category()) ? in.category() : "Other";
        return new Input(service, username, pw, normalizeUrl(in.url()), notes, category);
    }

    static String normalizeUrl(String raw) {
        String u = raw == null ? "" : raw.trim();
        if (u.isEmpty()) return "";
        if (u.length() > 500) throw new VaultException(400, "The website address is too long.");
        if (!u.contains("://")) u = "https://" + u;
        try {
            URI uri = new URI(u);
            String sch = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(sch.equals("http") || sch.equals("https")) || uri.getHost() == null) throw new URISyntaxExceptionHolder();
        } catch (Exception e) {
            throw new VaultException(400, "Enter a valid website address starting with http:// or https://.");
        }
        return u;
    }

    private static final class URISyntaxExceptionHolder extends RuntimeException { }

    private byte[] seal(String id, Input in) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("service", in.service());
        m.put("username", in.username());
        m.put("password", in.password());
        m.put("url", in.url());
        m.put("notes", in.notes());
        m.put("category", in.category());
        byte[] plain = Json.write(m).getBytes(StandardCharsets.UTF_8);
        try {
            return crypto.encrypt(key, plain, aad(id));
        } finally {
            CryptoService.wipe(plain);
        }
    }

    public synchronized String create(Input raw) {
        requireUnlocked();
        Input in = validate(raw, true);
        String id = UUID.randomUUID().toString();
        long now = clock.getAsLong();
        byte[] blob = seal(id, in);
        store.putEntry(new VaultStore.Row(id, now, now, blob));
        blobs.put(id, blob);
        index.put(id, toMeta(id, now, now, asMap(in)));
        log("Added entry", in.service());
        return id;
    }

    public synchronized void update(String id, Input raw) {
        requireUnlocked();
        Meta old = index.get(id);
        if (old == null) throw new VaultException(404, "That entry no longer exists.");
        Input in = validate(raw, false);
        if (in.password() == null || in.password().isEmpty()) {
            in = new Input(in.service(), in.username(), revealPassword(id), in.url(), in.notes(), in.category());
        }
        long now = clock.getAsLong();
        byte[] blob = seal(id, in);
        store.putEntry(new VaultStore.Row(id, old.createdAt(), now, blob));
        blobs.put(id, blob);
        index.put(id, toMeta(id, old.createdAt(), now, asMap(in)));
        if (breached.remove(id) != null) saveBreach();
        log("Edited entry", in.service());
    }

    public synchronized void delete(String id) {
        requireUnlocked();
        Meta m = index.get(id);
        if (m == null || !store.deleteEntry(id)) throw new VaultException(404, "That entry no longer exists.");
        index.remove(id);
        blobs.remove(id);
        if (breached.remove(id) != null) saveBreach();
        log("Deleted entry", m.service());
    }

    private static Map<String, Object> asMap(Input in) {
        Map<String, Object> m = new HashMap<>();
        m.put("service", in.service()); m.put("username", in.username()); m.put("password", in.password());
        m.put("url", in.url()); m.put("notes", in.notes()); m.put("category", in.category());
        return m;
    }

    private Map<String, Object> decrypted(String id) {
        byte[] blob = blobs.get(id);
        if (blob == null) throw new VaultException(404, "That entry no longer exists.");
        try {
            return open(id, blob);
        } catch (GeneralSecurityException e) {
            throw new VaultException(500, "This entry could not be decrypted (data may be damaged).");
        }
    }

    /** Entry details without the password. */
    public synchronized Map<String, Object> get(String id) {
        requireUnlocked();
        Meta m = index.get(id);
        if (m == null) throw new VaultException(404, "That entry no longer exists.");
        Map<String, Object> d = decrypted(id);
        Map<String, Object> out = metaMap(m, reuseCounts());
        out.put("notes", s(d, "notes"));
        return out;
    }

    public synchronized String revealPassword(String id) {
        requireUnlocked();
        if (!index.containsKey(id)) throw new VaultException(404, "That entry no longer exists.");
        return s(decrypted(id), "password");
    }

    public synchronized String usernameOf(String id) {
        requireUnlocked();
        Meta m = index.get(id);
        if (m == null) throw new VaultException(404, "That entry no longer exists.");
        return m.username();
    }

    /** Records a sensitive action (copy/reveal) in the activity log. */
    public synchronized void note(String action, String id) {
        requireUnlocked();
        Meta m = index.get(id);
        if (m != null) log(action, m.service());
    }

    private Map<String, Integer> reuseCounts() {
        Map<String, Integer> c = new HashMap<>();
        for (Meta m : index.values()) if (!m.fp().isEmpty()) c.merge(m.fp(), 1, Integer::sum);
        return c;
    }

    private boolean isStale(Meta m) { return clock.getAsLong() - m.updatedAt() > STALE_DAYS * 86_400_000L; }

    private Map<String, Object> metaMap(Meta m, Map<String, Integer> reuse) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", m.id());
        o.put("service", m.service());
        o.put("username", m.username());
        o.put("url", m.url());
        o.put("category", m.category());
        o.put("createdAt", m.createdAt());
        o.put("updatedAt", m.updatedAt());
        o.put("strength", m.strength());
        o.put("weak", m.strength() <= 1);
        o.put("reused", !m.fp().isEmpty() && reuse.getOrDefault(m.fp(), 0) > 1);
        o.put("stale", isStale(m));
        o.put("breached", breached.containsKey(m.id()));
        o.put("breachCount", breached.getOrDefault(m.id(), 0));
        o.put("hasNotes", m.hasNotes());
        return o;
    }

    public synchronized List<Map<String, Object>> list(String q, String category, String sort) {
        requireUnlocked();
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        Map<String, Integer> reuse = reuseCounts();
        List<Meta> out = new ArrayList<>();
        for (Meta m : index.values()) {
            if (category != null && !category.isEmpty() && !category.equalsIgnoreCase("all") && !m.category().equalsIgnoreCase(category)) continue;
            if (!needle.isEmpty() && !(m.service().toLowerCase(Locale.ROOT).contains(needle)
                    || m.username().toLowerCase(Locale.ROOT).contains(needle)
                    || m.url().toLowerCase(Locale.ROOT).contains(needle)
                    || m.category().toLowerCase(Locale.ROOT).contains(needle))) continue;
            out.add(m);
        }
        Comparator<Meta> byName = Comparator.comparing((Meta m) -> m.service().toLowerCase(Locale.ROOT)).thenComparing(Meta::id);
        switch (sort == null ? "name" : sort) {
            case "recent" -> out.sort(Comparator.comparingLong(Meta::updatedAt).reversed().thenComparing(byName));
            case "created" -> out.sort(Comparator.comparingLong(Meta::createdAt).reversed().thenComparing(byName));
            case "strength" -> out.sort(Comparator.comparingInt(Meta::strength).thenComparing(byName));
            default -> out.sort(byName);
        }
        List<Map<String, Object>> res = new ArrayList<>();
        for (Meta m : out) res.add(metaMap(m, reuse));
        return res;
    }

    // ------------------------------------------------------ audit / dashboard

    public synchronized Map<String, Object> audit() {
        requireUnlocked();
        Map<String, Integer> reuse = reuseCounts();
        List<Map<String, Object>> weak = new ArrayList<>(), stale = new ArrayList<>(), exposed = new ArrayList<>();
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Meta m : index.values()) {
            Map<String, Object> mm = metaMap(m, reuse);
            if (m.strength() <= 1) weak.add(mm);
            if (isStale(m)) stale.add(mm);
            if (breached.containsKey(m.id())) exposed.add(mm);
            if ((boolean) mm.get("reused")) groups.computeIfAbsent(m.fp(), x -> new ArrayList<>()).add(mm);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("weak", weak);
        out.put("breached", exposed);
        out.put("reusedGroups", new ArrayList<>(groups.values()));
        out.put("stale", stale);
        out.put("staleDays", STALE_DAYS);
        return out;
    }

    public synchronized Map<String, Object> dashboard() {
        requireUnlocked();
        Map<String, Integer> reuse = reuseCounts();
        int total = index.size(), weak = 0, reused = 0, stale = 0, exposed = 0;
        Map<String, Integer> byCat = new LinkedHashMap<>();
        for (String c : CATEGORIES) byCat.put(c, 0);
        List<Map<String, Object>> attention = new ArrayList<>();
        for (Meta m : index.values()) {
            byCat.merge(m.category(), 1, Integer::sum);
            Map<String, Object> mm = metaMap(m, reuse);
            List<String> issues = new ArrayList<>();
            if ((boolean) mm.get("breached")) { exposed++; issues.add("Found in a data breach"); }
            if ((boolean) mm.get("weak")) { weak++; issues.add("Weak password"); }
            if ((boolean) mm.get("reused")) { reused++; issues.add("Reused password"); }
            if ((boolean) mm.get("stale")) { stale++; issues.add("Not changed in " + STALE_DAYS + "+ days"); }
            if (!issues.isEmpty()) {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("id", m.id()); a.put("service", m.service()); a.put("username", m.username()); a.put("url", m.url()); a.put("issues", issues);
                attention.add(a);
            }
        }
        attention.sort((a, b) -> ((List<?>) b.get("issues")).size() - ((List<?>) a.get("issues")).size());
        Integer score = null;
        String label = "No data";
        if (total > 0) {
            double v = 100 - 45.0 * weak / total - 30.0 * reused / total - 10.0 * stale / total - 40.0 * exposed / total;
            score = (int) Math.max(0, Math.min(100, Math.round(v)));
            label = score >= 85 ? "Excellent" : score >= 70 ? "Good" : score >= 50 ? "Fair" : "At risk";
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("weak", weak);
        out.put("reused", reused);
        out.put("stale", stale);
        out.put("breached", exposed);
        out.put("breachCheckedAt", breachChecked);
        out.put("hasRecovery", hasRecovery());
        out.put("corrupt", corrupt);
        out.put("score", score);
        out.put("scoreLabel", label);
        out.put("byCategory", byCat);
        out.put("attention", attention.size() > 6 ? attention.subList(0, 6) : attention);
        out.put("attentionCount", attention.size());
        out.put("activity", recentActivity(8));
        return out;
    }

    private List<Map<String, Object>> recentActivity(int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (VaultStore.ActivityRow r : store.recentActivity(limit)) {
            try {
                byte[] plain = crypto.decrypt(key, r.data(), ACTIVITY_AAD);
                Map<String, Object> d = Json.parseObject(new String(plain, StandardCharsets.UTF_8));
                CryptoService.wipe(plain);
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("ts", r.ts()); o.put("action", s(d, "action")); o.put("label", s(d, "label"));
                out.add(o);
            } catch (GeneralSecurityException | RuntimeException ignored) { }
        }
        return out;
    }

    private void log(String action, String label) {
        if (key == null) return;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("action", action);
        m.put("label", label);
        byte[] plain = Json.write(m).getBytes(StandardCharsets.UTF_8);
        store.addActivity(clock.getAsLong(), crypto.encrypt(key, plain, ACTIVITY_AAD), ACTIVITY_KEEP);
        CryptoService.wipe(plain);
    }

    // --------------------------------------------------------------- settings

    public synchronized int autoLockMinutes() { return intSetting("autoLockMinutes", 5, 1, 120); }
    public synchronized int clipboardSeconds() { return intSetting("clipboardSeconds", 20, 5, 300); }

    private int intSetting(String k, int def, int min, int max) {
        try {
            int v = Integer.parseInt(store.getSetting(k).orElse(Integer.toString(def)));
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException e) { return def; }
    }

    public synchronized Map<String, Object> settings() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("theme", store.getSetting("theme").orElse("light"));
        m.put("autoLockMinutes", autoLockMinutes());
        m.put("clipboardSeconds", clipboardSeconds());
        m.put("sidebarCollapsed", "1".equals(store.getSetting("sidebarCollapsed").orElse("0")));
        m.put("breachAuto", breachAuto());
        m.put("logosEnabled", !"0".equals(store.getSetting("logosEnabled").orElse("1")));
        m.put("motion", store.getSetting("motion").orElse("full"));
        m.put("sound", !"0".equals(store.getSetting("sound").orElse("1")));
        return m;
    }

    public synchronized void updateSettings(Map<String, Object> in) {
        if (in.containsKey("theme")) {
            String t = String.valueOf(in.get("theme"));
            if (!t.equals("dark") && !t.equals("light")) throw new VaultException(400, "Unknown theme.");
            store.putSetting("theme", t);
        }
        if (in.containsKey("autoLockMinutes")) store.putSetting("autoLockMinutes", rangeInt(in.get("autoLockMinutes"), 1, 120, "Auto-lock time"));
        if (in.containsKey("clipboardSeconds")) store.putSetting("clipboardSeconds", rangeInt(in.get("clipboardSeconds"), 5, 300, "Clipboard clear time"));
        if (in.containsKey("sidebarCollapsed")) store.putSetting("sidebarCollapsed", Boolean.TRUE.equals(in.get("sidebarCollapsed")) ? "1" : "0");
        if (in.containsKey("breachAuto")) store.putSetting("breachAuto", Boolean.TRUE.equals(in.get("breachAuto")) ? "1" : "0");
        if (in.containsKey("logosEnabled")) store.putSetting("logosEnabled", Boolean.TRUE.equals(in.get("logosEnabled")) ? "1" : "0");
        if (in.containsKey("sound")) store.putSetting("sound", Boolean.TRUE.equals(in.get("sound")) ? "1" : "0");
        if (in.containsKey("motion")) {
            String mo = String.valueOf(in.get("motion"));
            if (!List.of("full", "reduced", "off").contains(mo)) throw new VaultException(400, "Unknown animation mode.");
            store.putSetting("motion", mo);
        }
        if (in.containsKey("masterHint")) {
            requireUnlocked();
            String hint = String.valueOf(in.get("masterHint")).trim();
            if (hint.length() > 100) throw new VaultException(400, "The hint must be at most 100 characters.");
            store.putSetting("masterHint", hint);
        }
        if (key != null) lastActivity = clock.getAsLong();
    }

    private static String rangeInt(Object v, int min, int max, String name) {
        if (!(v instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue()) || n.intValue() < min || n.intValue() > max)
            throw new VaultException(400, name + " must be between " + min + " and " + max + ".");
        return Integer.toString(n.intValue());
    }
}

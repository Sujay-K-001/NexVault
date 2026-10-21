package com.nexvault;

import com.nexvault.crypto.CryptoService;
import com.nexvault.db.InMemoryStore;
import com.nexvault.db.VaultStore;
import com.nexvault.util.ClipboardService;
import com.nexvault.util.Json;
import com.nexvault.logo.DevLogos;
import com.nexvault.logo.LogoService;
import com.nexvault.mail.AlertService;
import com.nexvault.mail.SecretStore;
import com.nexvault.mail.SmtpClient;
import com.nexvault.vault.ProfileService;
import com.nexvault.vault.BreachService;
import com.nexvault.vault.PasswordTools;
import com.nexvault.vault.VaultException;
import com.nexvault.vault.VaultService;
import com.nexvault.web.ApiServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Dependency-free test runner: java -cp build/classes:build/test-classes com.nexvault.TestMain */
public final class TestMain {
    static int passed, failed;

    static void check(boolean ok, String name) {
        if (ok) { passed++; System.out.println("  ok   " + name); }
        else { failed++; System.out.println("  FAIL " + name); }
    }

    static void expectVault(int status, String name, Runnable r) {
        try { r.run(); check(false, name + " (no exception)"); }
        catch (VaultException e) { check(e.status() == status, name + " -> " + e.status() + " " + e.getMessage()); }
    }

    static char[] c(String s) { return s.toCharArray(); }
    static VaultService.Input in(String svc, String user, String pw, String cat) {
        return new VaultService.Input(svc, user, pw, "", "", cat);
    }

    public static void main(String[] a) throws Exception {
        crypto();
        json();
        strength();
        vault();
        recoveryAndBreach();
        securityFeatures();
        http();
        System.out.println("\n" + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    static void crypto() throws Exception {
        System.out.println("[crypto]");
        CryptoService cs = new CryptoService();
        byte[] salt = cs.randomBytes(32);
        long t0 = System.nanoTime();
        byte[] k1 = cs.deriveKey(c("correct horse"), salt, 200_000);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        check(k1.length == 32, "PBKDF2WithHmacSHA256 yields 256-bit key (" + ms + " ms for 200k iterations)");
        check(Arrays.equals(k1, cs.deriveKey(c("correct horse"), salt, 200_000)), "KDF deterministic for same password+salt");
        check(!Arrays.equals(k1, cs.deriveKey(c("correct horsf"), salt, 200_000)), "different password -> different key");
        check(!Arrays.equals(k1, cs.deriveKey(c("correct horse"), cs.randomBytes(32), 200_000)), "different salt -> different key");
        byte[] aad = "ctx".getBytes(StandardCharsets.UTF_8);
        byte[] msg = "secret data".getBytes(StandardCharsets.UTF_8);
        byte[] ct = cs.encrypt(k1, msg, aad);
        check(ct.length == 12 + msg.length + 16, "blob layout IV(12)+ciphertext+tag(16)");
        check(Arrays.equals(msg, cs.decrypt(k1, ct, aad)), "AES-256-GCM round trip");
        check(!Arrays.equals(ct, cs.encrypt(k1, msg, aad)), "fresh random IV per encryption");
        byte[] bad = ct.clone(); bad[20] ^= 1;
        try { cs.decrypt(k1, bad, aad); check(false, "tampered ciphertext rejected"); } catch (GeneralSecurityException e) { check(true, "tampered ciphertext rejected"); }
        try { cs.decrypt(k1, ct, "other".getBytes()); check(false, "wrong AAD rejected"); } catch (GeneralSecurityException e) { check(true, "wrong AAD rejected"); }
        byte[] wrongKey = cs.deriveKey(c("nope nope nope"), salt, 1000);
        try { cs.decrypt(wrongKey, ct, aad); check(false, "wrong key rejected"); } catch (GeneralSecurityException e) { check(true, "wrong key rejected"); }
    }

    static void json() {
        System.out.println("[json]");
        Map<String, Object> m = Json.parseObject("{\"a\":\"x\\\"y\\u00e9\",\"n\":[1,2.5,true,null],\"o\":{\"k\":\"v\"}}");
        check("x\"y\u00e9".equals(m.get("a")), "string escapes");
        check(Json.write(m).equals("{\"a\":\"x\\\"y\u00e9\",\"n\":[1,2.5,true,null],\"o\":{\"k\":\"v\"}}"), "round trip write");
        try { Json.parse("{\"a\":"); check(false, "malformed rejected"); } catch (Json.JsonException e) { check(true, "malformed rejected"); }
        byte[] body = "{\"password\":\"p\\\"w\\u00fc \ud83d\ude00\",\"next\":\"x\"}".getBytes(StandardCharsets.UTF_8);
        Map<String, char[]> s = Json.parseSecrets(body);
        check(new String(s.get("password")).equals("p\"w\u00fc \ud83d\ude00") && new String(s.get("next")).equals("x"), "parseSecrets decodes into char[]");
        check(Arrays.equals(body, new byte[body.length]), "parseSecrets zeroes the request body");
    }

    static void strength() {
        System.out.println("[password tools]");
        check(PasswordTools.strength("password").score() == 0, "'password' is very weak");
        check(PasswordTools.strength("Password123!").score() <= 1, "'Password123!' is weak (common base word)");
        check(PasswordTools.strength("qwerty12345").score() <= 1, "keyboard pattern is weak");
        check(PasswordTools.strength("abc12").score() == 0, "short password very weak");
        check(PasswordTools.strength("tR9#vLq2$Zx!").score() >= 3, "random 12-char mixed is strong");
        check(PasswordTools.strength("correct horse battery staple").score() >= 3, "long passphrase strong");
        CryptoService cs = new CryptoService();
        String g = PasswordTools.generate(new PasswordTools.Options(24, true, true, true, true, false), cs.random());
        check(g.length() == 24 && g.chars().anyMatch(Character::isUpperCase) && g.chars().anyMatch(Character::isLowerCase)
                && g.chars().anyMatch(Character::isDigit) && g.chars().anyMatch(ch -> PasswordTools.SYMBOLS.indexOf(ch) >= 0), "generator honours length and all classes");
        boolean allOk = true;
        for (int i = 0; i < 200; i++) {
            String d = PasswordTools.generate(new PasswordTools.Options(8, false, true, true, false, true), cs.random());
            if (d.length() != 8 || d.chars().anyMatch(ch -> "Il1O0o|".indexOf(ch) >= 0 || Character.isUpperCase(ch) || PasswordTools.SYMBOLS.indexOf(ch) >= 0)) allOk = false;
            if (d.chars().noneMatch(Character::isDigit) || d.chars().noneMatch(Character::isLowerCase)) allOk = false;
        }
        check(allOk, "generator: selected classes only, no ambiguous chars, always one of each (200 samples)");
        check(!g.equals(PasswordTools.generate(new PasswordTools.Options(24, true, true, true, true, false), cs.random())), "generator output differs per call");
        try { PasswordTools.generate(new PasswordTools.Options(20, false, false, false, false, false), cs.random()); check(false, "no classes rejected"); }
        catch (IllegalArgumentException e) { check(true, "no classes rejected"); }
        try { PasswordTools.generate(new PasswordTools.Options(4, true, true, true, true, false), cs.random()); check(false, "short length rejected"); }
        catch (IllegalArgumentException e) { check(true, "short length rejected"); }
    }

    static void vault() throws Exception {
        System.out.println("[vault service]");
        AtomicLong now = new AtomicLong(1_700_000_000_000L);
        VaultStore store = new InMemoryStore();
        CryptoService cs = new CryptoService();
        VaultService v = new VaultService(store, cs, now::get);
        check(!v.isInitialized(), "fresh vault is not initialised");
        expectVault(400, "short master password rejected", () -> v.setup(c("short")));
        expectVault(400, "weak master password rejected", () -> v.setup(c("password123")));
        v.setup(c("Tr1cky-Master-Pass!"));
        check(v.isInitialized() && v.isUnlocked(), "setup creates and unlocks the vault");
        expectVault(409, "second setup rejected", () -> v.setup(c("Another-Strong-Pass-9")));
        check(new String(store.getMeta("kdf").get()).equals("PBKDF2WithHmacSHA256") && new String(store.getMeta("kdf_iter").get()).equals("200000")
                && store.getMeta("salt").get().length == 32, "KDF parameters stored (PBKDF2WithHmacSHA256, 200000, 32-byte salt)");

        String id1 = v.create(new VaultService.Input("GitHub", "dev@example.com", "tR9#vLq2$Zx!aa", "github.com", "my notes", "Development"));
        String id2 = v.create(in("Netflix", "me@example.com", "SharedPw-2024-xyz!", "Entertainment"));
        String id3 = v.create(in("Spotify", "me@example.com", "SharedPw-2024-xyz!", "Entertainment"));
        String id4 = v.create(in("OldForum", "me", "letmein", "Social"));
        check(v.list("", "all", "name").size() == 4, "four entries listed");
        check(v.list("git", null, null).size() == 1, "search by service");
        check(v.list("EXAMPLE.com", null, null).size() == 3, "search is case-insensitive over username");
        check(v.list("", "Entertainment", null).size() == 2, "category filter");
        check("https://github.com".equals(v.get(id1).get("url")) && "my notes".equals(v.get(id1).get("notes")), "url normalised, notes returned");
        check(!v.get(id1).containsKey("password"), "detail view does not include the password");
        check("tR9#vLq2$Zx!aa".equals(v.revealPassword(id1)), "password revealed on demand");
        expectVault(400, "empty service rejected", () -> v.create(in("", "u", "pw", "Other")));
        expectVault(400, "empty password rejected", () -> v.create(in("X", "u", "", "Other")));
        expectVault(400, "javascript: url rejected", () -> v.create(new VaultService.Input("X", "u", "pw1", "javascript:alert(1)", "", "Other")));

        for (var e : store.listEntries()) {
            String raw = new String(e.data(), StandardCharsets.ISO_8859_1);
            check(!raw.contains("GitHub") && !raw.contains("tR9#") && !raw.contains("example.com") && !raw.contains("Netflix"), "stored entry blob contains no plaintext");
        }
        for (var r : store.allActivity()) check(!new String(r.data(), StandardCharsets.ISO_8859_1).contains("GitHub"), "activity log is encrypted");

        Map<String, Object> d = v.dashboard();
        check((int) d.get("total") == 4 && (int) d.get("weak") == 1 && (int) d.get("reused") == 2 && (int) d.get("stale") == 0, "dashboard stats: total/weak/reused/stale");
        int score = (Integer) d.get("score");
        check(score == (int) Math.round(100 - 45.0 / 4 - 30.0 * 2 / 4), "security score formula (" + score + ")");
        @SuppressWarnings("unchecked") List<Map<String, Object>> act = (List<Map<String, Object>>) d.get("activity");
        check(!act.isEmpty() && "Added entry".equals(act.get(0).get("action")) && "OldForum".equals(act.get(0).get("label")), "recent activity newest first");
        @SuppressWarnings("unchecked") List<List<?>> groups = (List<List<?>>) v.audit().get("reusedGroups");
        check(groups.size() == 1 && groups.get(0).size() == 2, "audit groups reused passwords");

        v.update(id4, new VaultService.Input("OldForum", "me", "n3w-Str0ng!Pass#99xQ", "", "", "Social"));
        check(!"letmein".equals(v.revealPassword(id4)), "update changes password");
        v.update(id1, new VaultService.Input("GitHub 2", "dev2", null, "", "", "Work"));
        check("tR9#vLq2$Zx!aa".equals(v.revealPassword(id1)) && "GitHub 2".equals(v.get(id1).get("service")), "update without password keeps the old one");
        v.delete(id3);
        check(v.list("", null, null).size() == 3 && (int) v.dashboard().get("reused") == 0, "delete removes entry and reuse flag");
        expectVault(404, "get deleted entry -> 404", () -> v.get(id3));

        now.addAndGet(181L * 86_400_000L);
        check((int) v.dashboard().get("stale") == 3, "entries older than 180 days flagged stale");
        now.addAndGet(-181L * 86_400_000L);

        v.lock("Vault locked");
        check(!v.isUnlocked(), "lock");
        expectVault(423, "list while locked -> 423", () -> v.list("", null, null));
        expectVault(423, "reveal while locked -> 423", () -> v.revealPassword(id1));
        expectVault(401, "wrong master password rejected", () -> v.unlock(c("wrong-password-1")));
        v.unlock(c("Tr1cky-Master-Pass!"));
        check(v.list("", null, null).size() == 3 && "tR9#vLq2$Zx!aa".equals(v.revealPassword(id1)), "data persists across lock/unlock");

        VaultService v2 = new VaultService(store, cs, now::get);
        check(v2.isInitialized() && !v2.isUnlocked(), "a new service instance sees the stored vault, locked");
        v2.unlock(c("Tr1cky-Master-Pass!"));
        check(v2.list("", null, null).size() == 3, "second instance decrypts same data");
        v2.lock(null);

        // lockout: 5 consecutive failures -> 1 hour
        VaultService v3 = new VaultService(store, cs, now::get);
        for (int i = 0; i < 4; i++) { final int n = i; expectVault(401, "wrong password attempt " + (n + 1) + " is simply rejected", () -> v3.unlock(c("bad-guess-number-" + n))); }
        expectVault(429, "5th consecutive failure locks the vault", () -> v3.unlock(c("bad-guess-number-5")));
        expectVault(429, "even the correct password is refused during lockout", () -> v3.unlock(c("Tr1cky-Master-Pass!")));
        now.addAndGet(3_601_000);
        v3.unlock(c("Tr1cky-Master-Pass!"));
        check(v3.isUnlocked() && v3.failedAttempts() == 0, "unlock works after the lockout and the counter resets");

        // auto-lock
        v3.updateSettings(Map.of("autoLockMinutes", 2L));
        now.addAndGet(60_000);
        v3.checkAutoLock();
        check(v3.isUnlocked() && v3.secondsUntilLock() == 60, "not locked before timeout; countdown " + v3.secondsUntilLock() + "s");
        now.addAndGet(60_000);
        v3.checkAutoLock();
        check(!v3.isUnlocked(), "auto-lock after inactivity");
        v3.unlock(c("Tr1cky-Master-Pass!"));
        now.addAndGet(100_000);
        v3.touch();
        now.addAndGet(100_000);
        v3.checkAutoLock();
        check(v3.isUnlocked(), "activity resets the inactivity timer");
        expectVault(400, "invalid auto-lock rejected", () -> v3.updateSettings(Map.of("autoLockMinutes", 0L)));

        // change master password
        expectVault(401, "change master: wrong current", () -> v3.changeMaster(c("nope-nope-nope-1"), c("Brand-New-Master-77!")));
        expectVault(400, "change master: weak new", () -> v3.changeMaster(c("Tr1cky-Master-Pass!"), c("password123")));
        byte[] oldSalt = store.getMeta("salt").get();
        v3.changeMaster(c("Tr1cky-Master-Pass!"), c("Brand-New-Master-77!"));
        check(!Arrays.equals(oldSalt, store.getMeta("salt").get()), "new salt generated on master change");
        v3.lock(null);
        expectVault(401, "old master password no longer works", () -> v3.unlock(c("Tr1cky-Master-Pass!")));
        now.addAndGet(120_000);
        v3.unlock(c("Brand-New-Master-77!"));
        check(v3.list("", null, null).size() == 3 && "tR9#vLq2$Zx!aa".equals(v3.revealPassword(id1)), "all entries re-encrypted under the new master password");
        check(!v3.dashboard().get("activity").toString().equals("[]"), "activity log survives re-keying");

        // corruption/tamper detection
        VaultStore.Row r = store.listEntries().get(0);
        byte[] tampered = r.data().clone(); tampered[tampered.length - 1] ^= 0x55;
        store.putEntry(new VaultStore.Row(r.id(), r.createdAt(), r.updatedAt(), tampered));
        v3.lock(null);
        v3.unlock(c("Brand-New-Master-77!"));
        check((int) v3.dashboard().get("corrupt") == 1 && v3.list("", null, null).size() == 2, "tampered entry detected and skipped");
        // moving a blob between ids must fail (AAD binding)
        VaultStore st2 = new InMemoryStore();
        VaultService v4 = new VaultService(st2, cs, now::get);
        v4.setup(c("Another-Strong-Pass-9"));
        String ea = v4.create(in("A", "u", "pw-aaaaaaaa1", "Other")); String eb = v4.create(in("B", "u", "pw-bbbbbbbb2", "Other"));
        var rows = st2.listEntries();
        st2.putEntry(new VaultStore.Row(rows.get(0).id(), rows.get(0).createdAt(), rows.get(0).updatedAt(), rows.get(1).data()));
        v4.lock(null); v4.unlock(c("Another-Strong-Pass-9"));
        check((int) v4.dashboard().get("corrupt") == 1, "swapping ciphertext between rows is detected (AAD binds id)");
    }

    static void recoveryAndBreach() throws Exception {
        System.out.println("[recovery, hint, reset]");
        AtomicLong now = new AtomicLong(1_700_000_000_000L);
        VaultStore store = new InMemoryStore();
        CryptoService cs = new CryptoService();
        VaultService v = new VaultService(store, cs, now::get);
        String rk = v.setup(c("Orchid-Master-Pass-2026!"));
        check(rk.matches("([A-Z2-7]{4}-){7}[A-Z2-7]{4}"), "recovery key format XXXX-XXXX-... (32 chars, 160 bits): " + rk);
        check(v.hasRecovery(), "vault has a recovery key after setup");
        check(store.getMeta("wrapped_dek").isPresent() && store.getMeta("recovery_dek").isPresent(), "data key stored wrapped twice (master + recovery)");
        String id = v.create(in("Bank", "me", "tR9#vLq2$Zx!aaQ", "Finance"));
        v.lock("Vault locked");
        expectVault(401, "wrong recovery key rejected", () -> v.recover(c("AAAA-BBBB-CCCC-DDDD-EEEE-FFFF-GGGG-HHHH"), c("Fresh-Master-Pass-77!")));
        expectVault(401, "garbage recovery key rejected", () -> v.recover(c("nope"), c("Fresh-Master-Pass-77!")));
        now.addAndGet(120_000);
        expectVault(400, "recovery with a weak new master rejected", () -> v.recover(rk.toCharArray(), c("password123")));
        v.recover(rk.toLowerCase().replace("-", " ").toCharArray(), c("Fresh-Master-Pass-77!"));
        check(v.isUnlocked() && "tR9#vLq2$Zx!aaQ".equals(v.revealPassword(id)), "recovery key (typed loosely) unlocks, data intact");
        v.lock("Vault locked");
        expectVault(401, "old master password dead after recovery reset", () -> v.unlock(c("Orchid-Master-Pass-2026!")));
        now.addAndGet(120_000);
        v.unlock(c("Fresh-Master-Pass-77!"));
        check(v.isUnlocked(), "new master password works");
        v.changeMaster(c("Fresh-Master-Pass-77!"), c("Third-Master-Pass-88!"));
        v.lock("Vault locked");
        v.recover(rk.toCharArray(), c("Fourth-Master-Pass-99!"));
        check(v.isUnlocked() && v.list("", null, null).size() == 1, "recovery key still valid after a master-password change");
        expectVault(401, "regenerate needs the master password", () -> v.regenerateRecoveryKey(c("wrong-wrong-wrong")));
        String rk2 = v.regenerateRecoveryKey(c("Fourth-Master-Pass-99!"));
        check(!rk2.equals(rk), "new recovery key differs");
        v.lock("Vault locked");
        expectVault(401, "replaced recovery key no longer works", () -> v.recover(rk.toCharArray(), c("Fifth-Master-Pass-11!")));
        now.addAndGet(300_000);
        v.recover(rk2.toCharArray(), c("Fifth-Master-Pass-11!"));
        check(v.isUnlocked(), "replacement recovery key works");
        v.updateSettings(Map.of("masterHint", "my first dog"));
        v.lock("Vault locked");
        check("my first dog".equals(v.hint()), "hint readable while locked");
        expectVault(423, "setting a hint requires an unlocked vault", () -> v.updateSettings(Map.of("masterHint", "x")));
        expectVault(400, "reset needs the ERASE phrase", () -> v.resetVault("yes"));
        v.resetVault("ERASE");
        check(!v.isInitialized() && !v.hasRecovery() && store.listEntries().isEmpty(), "reset vault erases everything");
        v.setup(c("Brand-New-Vault-Pass-5!"));
        check(v.list("", null, null).isEmpty(), "a new vault can be created after reset");

        System.out.println("[breach monitoring]");
        VaultStore st = new InMemoryStore();
        VaultService bv = new VaultService(st, cs, now::get);
        bv.setup(c("Breach-Test-Master-42!"));
        String a = bv.create(in("Forum", "u", "letmein", "Social"));
        String b = bv.create(in("Safe", "u", "tR9#vLq2$Zx!aaQ", "Other"));
        BreachService bs = new BreachService(BreachService.devFetcher("letmein"));
        Map<String, Object> res = bs.scan(bv);
        @SuppressWarnings("unchecked") List<Map<String, Object>> list = (List<Map<String, Object>>) res.get("breached");
        check(list.size() == 1 && a.equals(list.get(0).get("id")) && ((Number) list.get(0).get("count")).intValue() > 0, "scan flags only the exposed password");
        check((boolean) bv.get(a).get("breached") && !(boolean) bv.get(b).get("breached"), "entries carry the breached flag");
        check((int) bv.dashboard().get("breached") == 1, "dashboard counts breached entries");
        for (var r : st.listEntries()) check(!new String(r.data(), StandardCharsets.ISO_8859_1).contains("letmein"), "stored data has no plaintext");
        check(!new String(st.getMeta("breach").get(), StandardCharsets.ISO_8859_1).contains(a), "breach results are encrypted at rest");
        bv.lock("Vault locked"); bv.unlock(c("Breach-Test-Master-42!"));
        check((boolean) bv.get(a).get("breached"), "breach results survive lock/unlock");
        bv.update(a, new VaultService.Input("Forum", "u", "n3w-Str0ng!Pass#99xQ", "", "", "Social"));
        check(!(boolean) bv.get(a).get("breached"), "changing the password clears its breach flag");
        BreachService failing = new BreachService(p -> { throw new java.io.IOException("offline"); });
        try { failing.scan(bv); check(false, "offline scan error"); } catch (VaultException e) { check(e.status() == 502, "offline scan -> 502 with friendly message"); }
        check(BreachService.parse("ABC:3\r\nDEF:0\r\nbad\r\n").size() == 1, "range parser ignores padding (count 0) and junk");
        now.addAndGet(8L * 86_400_000L);
        bv.updateSettings(Map.of("breachAuto", true));
        check(bv.breachDue(), "auto monitoring becomes due after 7 days when enabled");
        bv.updateSettings(Map.of("breachAuto", false));
        check(!bv.breachDue(), "auto monitoring off by default / when disabled");
    }

    // ------------------------------------------------------ lockout / alerts / profile / logos
    record Mail(String to, String subject, String body, String user, String pass) {}

    static void securityFeatures() throws Exception {
        System.out.println("[lockout policy]");
        AtomicLong now = new AtomicLong(1_700_000_000_000L);
        VaultStore store = new InMemoryStore();
        CryptoService cs = new CryptoService();
        VaultService v = new VaultService(store, cs, now::get);
        List<String> events = new ArrayList<>();
        v.setEvents((attempts, locked, secs) -> events.add(attempts + ":" + locked + ":" + secs));
        v.setup(c("Lockout-Test-Master-42!"));
        v.lock("Vault locked");
        for (int i = 1; i <= 3; i++) { final int n = i; expectVault(401, "attempt " + n + " -> 401, no e-mail yet", () -> v.unlock(c("wrong-wrong-" + n))); }
        check(events.isEmpty(), "no alert for the first 3 incorrect attempts");
        try { v.unlock(c("wrong-wrong-4")); check(false, "4th attempt rejected"); }
        catch (VaultException e) { check(e.status() == 401 && Integer.valueOf(1).equals(e.extra().get("attemptsLeft")), "4th attempt: 401 with 1 attempt left"); }
        check(events.equals(List.of("4:false:0")), "alert raised after MORE than 3 incorrect attempts (4th): " + events);
        VaultService restarted = new VaultService(store, cs, now::get);
        restarted.setEvents((a2, l2, s2) -> events.add(a2 + ":" + l2 + ":" + s2));
        try { restarted.unlock(c("wrong-wrong-5")); check(false, "5th attempt rejected"); }
        catch (VaultException e) { check(e.status() == 429 && ((Number) e.extra().get("lockoutSeconds")).longValue() == 3600, "5th attempt (counter survived a restart) locks the vault for 3600 s"); }
        check(events.get(1).equals("5:true:3600"), "lockout alert raised: " + events.get(1));
        check(restarted.lockoutSeconds() == 3600 && restarted.failedAttempts() == 5, "lockout is active");
        expectVault(429, "correct password refused while locked out", () -> restarted.unlock(c("Lockout-Test-Master-42!")));
        expectVault(429, "recovery refused while locked out", () -> restarted.recover(c("AAAA-BBBB-CCCC-DDDD-EEEE-FFFF-GGGG-HHHH"), c("Another-Master-Pass-9!")));
        expectVault(429, "erase-vault refused while locked out", () -> restarted.resetVault("ERASE"));
        now.addAndGet(1_800_000);
        VaultService again = new VaultService(store, cs, now::get);
        check(again.lockoutSeconds() == 1800, "after a restart 30 min later 1800 s remain (cannot be bypassed): " + again.lockoutSeconds());
        expectVault(429, "still locked after the restart", () -> again.unlock(c("Lockout-Test-Master-42!")));
        long before = now.get();
        now.set(before - 10_000_000L);    // user sets the system clock back
        check(again.lockoutSeconds() >= 1800, "setting the clock back does not shorten the lockout");
        now.set(before + 1_800_001L);
        again.unlock(c("Lockout-Test-Master-42!"));
        check(again.isUnlocked() && again.lockoutSeconds() == 0 && again.failedAttempts() == 0, "unlock works once the hour has passed");
        again.lock("Vault locked");
        try { again.unlock(c("x-wrong-1")); } catch (VaultException ignored) { }
        try { again.unlock(c("x-wrong-2")); } catch (VaultException ignored) { }
        check(again.failedAttempts() == 2, "failed attempts are counted");
        again.unlock(c("Lockout-Test-Master-42!"));
        check(again.failedAttempts() == 0, "a successful unlock resets the consecutive counter");

        System.out.println("[alert e-mail + SMTP]");
        VaultStore st = new InMemoryStore();
        ProfileService prof = new ProfileService(st);
        java.nio.file.Path tmp = java.nio.file.Files.createTempDirectory("nv-sec");
        SecretStore secrets = SecretStore.forPlatform(tmp);
        List<Mail> sent = new ArrayList<>();
        AlertService al = new AlertService(st, prof, secrets, (cfg, to, subj, body) -> sent.add(new Mail(to, subj, body, cfg.user(), cfg.password())), now::get, false);
        al.failedAttempts(4, false, 0);
        check(sent.isEmpty() && "skipped".equals(al.lastAlert().get("status")), "no e-mail when nothing is configured (recorded as skipped)");
        prof.update("Master", "master@example.org");
        Map<String, char[]> cfg = new HashMap<>();
        cfg.put("host", c("smtp.example.org")); cfg.put("port", c("587")); cfg.put("security", c("STARTTLS")); cfg.put("user", c("alerts@example.org")); cfg.put("password", c("S3cret-App-Pass!"));
        al.update(cfg);
        check(al.config().get("passSet").equals(true) && !Json.write(al.config()).contains("S3cret-App-Pass!"), "SMTP password is never returned by the API");
        check(!st.getSetting("smtpPass").get().contains("S3cret-App-Pass!") && !st.getSetting("smtpPass").get().isEmpty(), "SMTP password is stored protected, not in plaintext");
        al.failedAttempts(4, false, 0);
        al.failedAttempts(5, true, 3600);
        check(sent.size() == 2 && sent.get(0).to().equals("master@example.org") && sent.get(0).pass().equals("S3cret-App-Pass!"), "alert e-mail goes to the master address using the stored credentials");
        check(sent.get(1).subject().contains("locked for 1 hour") && sent.get(0).subject().contains("security alert"), "subjects: repeated attempts / lockout");
        String all = sent.get(0).body() + sent.get(1).body() + sent.get(0).subject();
        check(!all.contains("S3cret") && !all.toLowerCase().contains("wrong-wrong") && !all.contains("GitHub") && all.contains("never contains your passwords"), "alert text contains no credentials or vault data");
        // HTML alternative: detailed, escaped, free of credentials and vault data
        List<String> htmls = new ArrayList<>();
        AlertService.Sender both = new AlertService.Sender() {
            @Override public void send(SmtpClient.Config c, String to, String subject, String body) { htmls.add("PLAIN-ONLY"); }
            @Override public void send(SmtpClient.Config c, String to, String subject, String body, String html) { htmls.add(html); }
        };
        VaultStore st2 = new InMemoryStore();
        ProfileService prof2 = new ProfileService(st2);
        prof2.update("Ana <b>Lee</b>", "ana@example.org");
        AlertService al2 = new AlertService(st2, prof2, secrets, both, now::get, false);
        Map<String, char[]> cfg2 = new HashMap<>(); cfg2.put("host", c("smtp.example.org")); cfg2.put("port", c("587")); cfg2.put("security", c("STARTTLS")); cfg2.put("user", c("a@example.org")); cfg2.put("password", c("S3cret-App-Pass!"));
        al2.update(cfg2);
        al2.failedAttempts(4, false, 0); al2.failedAttempts(5, true, 3600);
        check(htmls.size() == 2 && !htmls.contains("PLAIN-ONLY"), "alerts are sent with an HTML alternative");
        String h0 = htmls.get(0), h1 = htmls.get(1);
        check(h0.contains("4 OF 5") && h1.contains("LOCKED for 1 hour") && h1.contains("What to do next".toUpperCase()), "HTML alert shows the attempt meter, lock status and next steps");
        check(!h0.contains("<b>Lee") && h0.contains("Ana &lt;b&gt;Lee&lt;/b&gt;"), "recipient name is HTML-escaped in the HTML part");
        check(!(h0 + h1).contains("S3cret") && !(h0 + h1).contains("<img") && !(h0 + h1).contains("http://") && !(h0 + h1).contains("https://") && !(h0 + h1).toLowerCase().contains("<script"), "HTML alert has no credentials, images, links or scripts");
        check(sent.get(1).body().contains("WHAT NEXVAULT HAS DONE") && sent.get(1).body().contains("Reference:") && sent.get(1).body().contains("(UTC"), "plain-text alert is detailed: actions taken, reference id, UTC time");
        for (var bad : List.of(Map.of("host", "bad host!", "port", "587", "security", "SSL"), Map.of("host", "smtp.example.org", "port", "70000", "security", "SSL"),
                Map.of("host", "smtp.example.org", "port", "25", "security", "NONE"), Map.of("host", "smtp.example.org", "port", "x", "security", "SSL"))) {
            Map<String, char[]> m = new HashMap<>(); bad.forEach((k, vv) -> m.put(k, vv.toCharArray()));
            try { al.update(m); check(false, "invalid SMTP config rejected " + bad); } catch (VaultException e) { check(e.status() == 400, "invalid SMTP config rejected: " + e.getMessage()); }
        }
        try { prof.update("x", "not-an-email"); check(false, "bad e-mail rejected"); } catch (VaultException e) { check(true, "bad e-mail rejected"); }
        try { prof.update("x", "a@b.co\r\nBcc: evil@x.io"); check(false, "header injection rejected"); } catch (VaultException e) { check(true, "e-mail header injection rejected"); }

        // real SMTP conversation against a loopback test server
        List<String> transcript = Collections.synchronizedList(new ArrayList<>());
        try (java.net.ServerSocket ss = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            Thread t = new Thread(() -> {
                try (java.net.Socket s = ss.accept()) {
                    var in = new java.io.BufferedReader(new java.io.InputStreamReader(s.getInputStream()));
                    var out = s.getOutputStream();
                    java.util.function.Consumer<String> w = x -> { try { out.write((x + "\r\n").getBytes()); out.flush(); } catch (Exception ignored) { } };
                    w.accept("220 test ESMTP");
                    String line; boolean data = false;
                    while ((line = in.readLine()) != null) {
                        transcript.add(line);
                        if (data) { if (line.equals(".")) { data = false; w.accept("250 queued"); } continue; }
                        if (line.startsWith("EHLO")) { out.write("250-test\r\n250 AUTH PLAIN LOGIN\r\n".getBytes()); out.flush(); }
                        else if (line.startsWith("AUTH PLAIN")) w.accept("235 ok");
                        else if (line.startsWith("DATA")) { w.accept("354 go"); data = true; }
                        else if (line.startsWith("QUIT")) { w.accept("221 bye"); break; }
                        else w.accept("250 ok");
                    }
                } catch (Exception ignored) { }
            });
            t.start();
            SmtpClient.send(new SmtpClient.Config("127.0.0.1", ss.getLocalPort(), SmtpClient.Security.NONE, "u@x.io", "pw-123", "u@x.io"), "to@x.io", "Hello", "Body text");
            t.join(3000);
        }
        String tr = String.join("\n", transcript);
        check(tr.contains("MAIL FROM:<u@x.io>") && tr.contains("RCPT TO:<to@x.io>") && tr.contains("Subject: Hello") && tr.contains("AUTH PLAIN ")
                && tr.contains(java.util.Base64.getEncoder().encodeToString("\0u@x.io\0pw-123".getBytes())), "SMTP client speaks AUTH PLAIN / MAIL / RCPT / DATA correctly");
        try { SmtpClient.send(new SmtpClient.Config("smtp.example.org", 25, SmtpClient.Security.NONE, "", "", "a@b.io"), "to@x.io", "s", "b"); check(false, "plaintext SMTP refused"); }
        catch (java.io.IOException e) { check(e.getMessage().contains("only allowed for localhost"), "unencrypted SMTP is refused for non-local hosts"); }
        try { SmtpClient.send(new SmtpClient.Config("127.0.0.1", 1, SmtpClient.Security.NONE, "", "", "a@b.io"), "to@x.io\r\nBcc:x@y.io", "s", "b"); check(false, "injection refused"); }
        catch (java.io.IOException e) { check(e.getMessage().contains("Invalid"), "SMTP header injection refused"); }

        System.out.println("[profile]");
        VaultStore ps = new InMemoryStore();
        ProfileService pr = new ProfileService(ps);
        pr.update("  Ada Lovelace ", "ada@example.org");
        check("Ada Lovelace".equals(pr.name()) && "ada@example.org".equals(pr.email()), "profile name and e-mail saved (trimmed)");
        try { pr.update("x".repeat(61), ""); check(false, "long name rejected"); } catch (VaultException e) { check(true, "overlong name rejected"); }
        pr.setBuiltin("fox");
        check("builtin".equals(pr.summary().get("avatarKind")) && "fox".equals(pr.summary().get("avatarId")), "built-in avatar selectable");
        try { pr.setBuiltin("dragon"); check(false, "unknown avatar rejected"); } catch (VaultException e) { check(true, "unknown built-in avatar rejected"); }
        var bi = new java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var bo = new java.io.ByteArrayOutputStream(); javax.imageio.ImageIO.write(bi, "png", bo);
        String good = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(bo.toByteArray());
        pr.setCustom(good);
        check("custom".equals(pr.get().get("avatarKind")) && good.equals(pr.get().get("avatarData")), "custom avatar upload accepted");
        for (String bad : List.of("data:image/png;base64," + java.util.Base64.getEncoder().encodeToString("<script>alert(1)</script> not an image".getBytes()),
                "data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=", "data:text/html;base64,PGgxPg==", "https://evil.example/x.png",
                "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(new byte[200_000]))) {
            try { pr.setCustom(bad); check(false, "bad avatar rejected"); } catch (VaultException e) { check(e.status() == 400, "invalid avatar rejected: " + bad.substring(0, Math.min(28, bad.length()))); }
        }
        pr.clearAvatar();
        check("none".equals(pr.summary().get("avatarKind")) && "".equals(pr.get().get("avatarData")), "avatar can be removed");

        System.out.println("[logo service]");
        java.nio.file.Path ld = java.nio.file.Files.createTempDirectory("nv-logo");
        int[] calls = {0};
        LogoService.Fetcher counting = (url, max) -> { calls[0]++; return DevLogos.fetcher().get(url, max); };
        LogoService ls = new LogoService(counting, ld);
        var gh = ls.get("GitHub.com", 5000);
        check(gh.isPresent() && gh.get().type().equals("image/png") && gh.get().bytes().length > 100, "logo fetched for a recognised domain");
        int first = calls[0];
        check(ls.get("www.github.com", 5000).isPresent() && calls[0] == first, "second request (www. variant) is served from the cache");
        check(ls.get("unknown-site.org", 5000).isEmpty(), "unknown site -> graceful miss");
        int afterMiss = calls[0];
        check(ls.get("unknown-site.org", 5000).isEmpty() && calls[0] == afterMiss, "misses are cached too (no repeated requests)");
        for (String bad : List.of("localhost", "192.168.1.1", "a_b.com", "http://evil.io", "", "no-dot", "x.y")) check(ls.get(bad, 500).isEmpty(), "rejects non-public/invalid domain: '" + bad + "'");
        check(!LogoService.isPublicHost("localhost") && !LogoService.isPublicHost("127.0.0.1") && !LogoService.isPublicHost("192.168.0.5") && !LogoService.isPublicHost("10.0.0.1") && !LogoService.isPublicHost("169.254.169.254") && !LogoService.isPublicHost("printer.local"), "SSRF guard blocks loopback/private/link-local hosts");
        check(LogoService.validDomain("accounts.google.com") && !LogoService.validDomain("a b.com"), "domain validation");
        check(ls.get("accounts.github.com", 5000).isPresent(), "sub-domain falls back to its parent domain");
        check(ls.clearCache() > 0 && ls.get("github.com", 5000).isPresent() && calls[0] > afterMiss, "cache can be cleared and is refilled");
        try (var files = java.nio.file.Files.list(ld)) { check(files.noneMatch(f -> f.getFileName().toString().contains("github")), "cache files are named by hash, not by domain"); }
    }

    // ------------------------------------------------------------------ HTTP
    static HttpClient client = HttpClient.newHttpClient();
    static String base, token;

    static HttpResponse<String> call(String method, String path, String body, Map<String, String> headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path));
        if (token != null && !(headers != null && headers.containsKey("X-NexVault-Token"))) b.header("X-NexVault-Token", token);
        if (body != null) b.header("Content-Type", "application/json");
        if (headers != null) headers.forEach((k, vv) -> { if (!vv.isEmpty()) b.header(k, vv); });
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    static HttpResponse<String> call(String m, String p, String body) throws Exception { return call(m, p, body, null); }

    @SuppressWarnings("unchecked")
    static Map<String, Object> obj(HttpResponse<String> r) { return (Map<String, Object>) Json.parse(r.body()); }

    static void http() throws Exception {
        System.out.println("[http api]");
        System.setProperty("java.awt.headless", "true");
        VaultStore store = new InMemoryStore();
        CryptoService cs = new CryptoService();
        VaultService v = new VaultService(store, cs);
        java.nio.file.Path tmp = java.nio.file.Files.createTempDirectory("nv-test");
        ProfileService prof = new ProfileService(store);
        AlertService al = new AlertService(store, prof, SecretStore.forPlatform(tmp), SmtpClient::send, System::currentTimeMillis, false);
        v.setEvents(al);
        ApiServer srv = new ApiServer(v, new ClipboardService(), cs, new BreachService(BreachService.devFetcher("letmein")), prof, al, new LogoService(DevLogos.fetcher(), tmp.resolve("logos")), () -> { });
        int port = srv.start(0);
        base = "http://127.0.0.1:" + port;
        token = srv.token();
        try {
            var page = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(base + "/")).build(), HttpResponse.BodyHandlers.ofString());
            check(page.statusCode() == 200 && page.body().contains("NexVault") && page.headers().firstValue("Content-Security-Policy").isPresent(), "serves UI with CSP header");
            for (String f : new String[]{"app.css", "app.js", "favicon.svg"}) {
                var r = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(base + "/" + f)).build(), HttpResponse.BodyHandlers.ofString());
                check(r.statusCode() == 200, "static /" + f);
            }
            check(HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(base + "/..%2f..%2fetc%2fpasswd")).build(), HttpResponse.BodyHandlers.ofString()).statusCode() == 404, "path traversal blocked");
            for (String bad : new String[]{"/fonts/..%2fapp.js", "/fonts/%2e%2e/app.js", "/fonts/sub/x.woff2", "/fonts/", "/Fonts/x.woff2"}) {
                int sc = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(base + bad)).build(), HttpResponse.BodyHandlers.ofString()).statusCode();
                check(sc == 404, "font route rejects " + bad + " (" + sc + ")");
            }
            int fontStatus = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(base + "/fonts/Urbanist-400.woff2")).build(), HttpResponse.BodyHandlers.ofString()).statusCode();
            check(fontStatus == 200 || fontStatus == 404, "font route is served (200 when downloaded, clean 404 when not): " + fontStatus);
            var cssPage = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(base + "/app.css")).build(), HttpResponse.BodyHandlers.ofString());
            check(cssPage.body().contains("font-family: \"Godber\"") && cssPage.body().contains("Space Grotesk") && !cssPage.body().toLowerCase().contains("#a66bff"), "stylesheet declares the three typefaces and the dark theme has no purple accent");

            check(call("GET", "/api/status", null, Map.of("X-NexVault-Token", "wrong")).statusCode() == 401, "wrong token -> 401");
            try (java.net.Socket sock = new java.net.Socket("127.0.0.1", port)) {
                sock.getOutputStream().write(("GET /api/status HTTP/1.1\r\nHost: evil.example:" + port + "\r\nX-NexVault-Token: " + token + "\r\nConnection: close\r\n\r\n").getBytes());
                String first = new java.io.BufferedReader(new java.io.InputStreamReader(sock.getInputStream())).readLine();
                check(first != null && first.contains(" 403"), "foreign Host header rejected (DNS rebinding): " + first);
            }
            check(call("POST", "/api/unlock", "{\"password\":\"x\"}", Map.of("Origin", "http://evil.example")).statusCode() == 403, "foreign Origin rejected (CSRF)");
            var wrongCt = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(base + "/api/unlock")).header("X-NexVault-Token", token)
                    .header("Content-Type", "text/plain").POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
            check(wrongCt.statusCode() == 415, "non-JSON content type rejected");

            var st = obj(call("GET", "/api/status", null));
            check(Boolean.FALSE.equals(st.get("initialized")) && Boolean.FALSE.equals(st.get("unlocked")) && "light".equals(st.get("theme")), "status: fresh vault, default theme is light");
            check(call("GET", "/api/entries", null).statusCode() == 423, "entries before setup -> 423");
            check(call("POST", "/api/setup", "{\"password\":\"short\"}").statusCode() == 400, "setup rejects weak password");
            var setupRes = call("POST", "/api/setup", "{\"password\":\"Http-Master-Pass-42!\"}");
            check(setupRes.statusCode() == 200 && ((String) obj(setupRes).get("recoveryKey")).matches("([A-Z2-7]{4}-){7}[A-Z2-7]{4}"), "setup ok and returns the recovery key once");
            String httpRk = (String) obj(setupRes).get("recoveryKey");
            check(!call("GET", "/api/status", null).body().contains(httpRk), "recovery key is not exposed by /api/status");
            st = obj(call("GET", "/api/status", null));
            check(Boolean.TRUE.equals(st.get("initialized")) && Boolean.TRUE.equals(st.get("unlocked")), "status: unlocked after setup");

            var created = call("POST", "/api/entries", "{\"service\":\"Example\",\"username\":\"u@x.io\",\"password\":\"Zq8!mW3#kLp0vR\",\"url\":\"example.com\",\"notes\":\"n\",\"category\":\"Work\"}");
            check(created.statusCode() == 201, "create entry");
            String id = (String) obj(created).get("id");
            var list = (List<?>) Json.parse(call("GET", "/api/entries?q=exam&category=Work&sort=name", null).body());
            check(list.size() == 1 && !call("GET", "/api/entries", null).body().contains("Zq8!mW3#kLp0vR"), "list/search works and never leaks passwords");
            check(call("GET", "/api/entries/" + id + "/password", null).body().contains("Zq8!mW3#kLp0vR"), "reveal endpoint returns password");
            check(call("PUT", "/api/entries/" + id, "{\"service\":\"Example 2\",\"username\":\"u\",\"category\":\"Email\"}").statusCode() == 200
                    && "Example 2".equals(obj(call("GET", "/api/entries/" + id, null)).get("service")), "update entry");
            check(call("GET", "/api/entries/not-an-id", null).statusCode() == 404, "bad id -> 404");
            check(call("POST", "/api/entries", "{\"service\":\"\",\"password\":\"x\"}").statusCode() == 400, "validation error -> 400");
            check(call("POST", "/api/entries", "{bad json").statusCode() == 400, "malformed JSON -> 400");

            var gen = obj(call("POST", "/api/generate", "{\"length\":32,\"upper\":true,\"lower\":true,\"digits\":true,\"symbols\":false,\"avoidAmbiguous\":true}"));
            check(((String) gen.get("password")).length() == 32 && !((String) gen.get("password")).matches(".*[^A-Za-z0-9].*"), "generate endpoint");
            check(call("POST", "/api/generate", "{\"length\":20,\"upper\":false,\"lower\":false,\"digits\":false,\"symbols\":false}").statusCode() == 400, "generate rejects empty selection");
            check(((Number) obj(call("POST", "/api/strength", "{\"password\":\"password\"}")).get("score")).intValue() == 0, "strength endpoint");

            var clip = call("POST", "/api/clipboard", "{\"entryId\":\"" + id + "\",\"field\":\"password\"}");
            check(clip.statusCode() == 501 && Boolean.TRUE.equals(obj(clip).get("fallback")), "clipboard: headless -> 501 with fallback flag");

            var dash = obj(call("GET", "/api/dashboard", null));
            check(((Number) dash.get("total")).intValue() == 1 && dash.containsKey("activity"), "dashboard endpoint");
            check(call("GET", "/api/audit", null).statusCode() == 200, "audit endpoint");
            check(call("PUT", "/api/settings", "{\"theme\":\"dark\",\"autoLockMinutes\":10,\"clipboardSeconds\":30}").statusCode() == 200
                    && "dark".equals(obj(call("GET", "/api/status", null)).get("theme")), "settings update (switch to dark)");
            check(call("PUT", "/api/settings", "{\"theme\":\"neon\"}").statusCode() == 400, "invalid setting rejected");

            check(call("POST", "/api/master", "{\"current\":\"Http-Master-Pass-42!\",\"next\":\"Changed-Master-Pass-43!\"}").statusCode() == 200, "change master over HTTP");
            var bres = call("POST", "/api/breach/scan", "{}");
            check(bres.statusCode() == 200 && obj(bres).containsKey("breached"), "breach scan endpoint");
            check(call("GET", "/api/breach", null).statusCode() == 200, "breach status endpoint");
            check(call("POST", "/api/recovery/generate", "{\"current\":\"wrong\"}").statusCode() == 401, "recovery regenerate needs master password");
            check(call("PUT", "/api/settings", "{\"masterHint\":\"blue bike\",\"sidebarCollapsed\":true,\"breachAuto\":true}").statusCode() == 200
                    && Boolean.TRUE.equals(obj(call("GET", "/api/status", null)).get("sidebarCollapsed")) && Boolean.TRUE.equals(obj(call("GET", "/api/status", null)).get("hasHint")), "hint / sidebar / breach-auto settings");
            check(call("POST", "/api/lock", "{}").statusCode() == 200 && call("GET", "/api/entries", null).statusCode() == 423, "lock over HTTP");
            check(call("GET", "/api/hint", null).body().contains("blue bike"), "hint available while locked");
            check(call("POST", "/api/recover", "{\"recoveryKey\":\"" + httpRk + "\",\"next\":\"Recovered-Master-Pass-5!\"}").statusCode() == 200
                    && call("GET", "/api/entries", null).statusCode() == 200, "recover over HTTP unlocks the vault");
            check(call("POST", "/api/lock", "{}").statusCode() == 200, "lock again");
            check(call("POST", "/api/unlock", "{\"password\":\"Recovered-Master-Pass-5!\"}").statusCode() == 200, "unlock with recovered master password");
            check(call("POST", "/api/master", "{\"current\":\"Recovered-Master-Pass-5!\",\"next\":\"Changed-Master-Pass-43!\"}").statusCode() == 200, "restore master for the remaining checks");
            check(call("POST", "/api/lock", "{}").statusCode() == 200, "lock again");
            check(call("POST", "/api/unlock", "{\"password\":\"Http-Master-Pass-42!\"}").statusCode() == 401, "old password rejected");
            check(call("POST", "/api/unlock", "{\"password\":\"Changed-Master-Pass-43!\"}").statusCode() == 200, "unlock with new password");
            check(call("DELETE", "/api/entries/" + id, null, Map.of("Content-Type", "application/json")).statusCode() == 200
                    && call("GET", "/api/entries/" + id, null).statusCode() == 404, "delete entry");
        } finally {
            srv.stop();
        }
    }
}

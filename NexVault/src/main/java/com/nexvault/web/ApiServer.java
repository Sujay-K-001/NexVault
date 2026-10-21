package com.nexvault.web;

import com.nexvault.crypto.CryptoService;
import com.nexvault.logo.LogoService;
import com.nexvault.mail.AlertService;
import com.nexvault.vault.ProfileService;
import com.nexvault.util.ClipboardService;
import com.nexvault.util.Json;
import com.nexvault.vault.BreachService;
import com.nexvault.vault.PasswordTools;
import com.nexvault.vault.VaultException;
import com.nexvault.vault.VaultService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Local-only HTTP server (127.0.0.1) that serves the UI and a JSON API.
 * Defences: loopback binding, per-launch random session token, Host/Origin checks (DNS-rebinding and CSRF),
 * JSON-only bodies, strict CSP, no caching.
 */
public final class ApiServer {
    private static final int MAX_BODY = 256 * 1024;
    private static final Pattern ID = Pattern.compile("[0-9a-fA-F-]{36}");
    private static final Pattern STATIC_NAME = Pattern.compile("(fonts/)?[A-Za-z0-9_-]+\\.[A-Za-z0-9]+");
    private static final String CSP = "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
            + "connect-src 'self'; font-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'";

    private final VaultService vault;
    private final ClipboardService clipboard;
    private final CryptoService crypto;
    private final BreachService breach;
    private final ProfileService profile;
    private final AlertService alerts;
    private final LogoService logos;
    private final Runnable onQuit;
    private final AtomicBoolean scanning = new AtomicBoolean();
    private volatile long lastAuto;
    private final String token;
    private HttpServer http;
    private int port;
    private volatile long lastRequest = System.currentTimeMillis();
    private ScheduledExecutorService watchdog;

    public ApiServer(VaultService vault, ClipboardService clipboard, CryptoService crypto, BreachService breach,
                     ProfileService profile, AlertService alerts, LogoService logos, Runnable onQuit) {
        this.vault = vault;
        this.clipboard = clipboard;
        this.crypto = crypto;
        this.breach = breach;
        this.profile = profile;
        this.alerts = alerts;
        this.logos = logos;
        this.onQuit = onQuit;
        this.token = Base64.getUrlEncoder().withoutPadding().encodeToString(crypto.randomBytes(32));
    }

    public String token() { return token; }
    public int port() { return port; }

    public int start(int requestedPort) throws IOException {
        http = HttpServer.create(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), requestedPort), 0);
        port = http.getAddress().getPort();
        http.createContext("/", this::staticFile);
        http.createContext("/api/", this::api);
        http.setExecutor(Executors.newFixedThreadPool(16, r -> {
            Thread t = new Thread(r, "http-worker");
            t.setDaemon(true);
            return t;
        }));
        http.start();
        watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "vault-watchdog");
            t.setDaemon(true);
            return t;
        });
        watchdog.scheduleWithFixedDelay(this::watch, 1, 1, TimeUnit.SECONDS);
        return port;
    }

    public void stop() {
        if (watchdog != null) watchdog.shutdownNow();
        if (http != null) http.stop(0);
    }

    /** Auto-lock on inactivity; lock if the UI window disappears; exit if nobody is connected for a long time. */
    private void watch() {
        try {
            vault.checkAutoLock();
            long idle = System.currentTimeMillis() - lastRequest;
            if (idle > 120_000 && vault.isUnlocked()) vault.lock("Vault locked (window closed)");
            if (idle > 20 * 60_000L) onQuit.run();
            long now = System.currentTimeMillis();
            if (vault.breachDue() && now - lastAuto > 3_600_000L && scanning.compareAndSet(false, true)) {
                lastAuto = now;
                Thread t = new Thread(() -> {
                    try { breach.scan(vault); } catch (RuntimeException ignored) { } finally { scanning.set(false); }
                }, "breach-auto");
                t.setDaemon(true);
                t.start();
            }
        } catch (RuntimeException e) {
            System.err.println("watchdog: " + e.getClass().getSimpleName());
        }
    }

    // ---------------------------------------------------------------- static

    private void staticFile(HttpExchange ex) throws IOException {
        try {
            if (!hostOk(ex)) { send(ex, 403, Map.of("error", "Forbidden host")); return; }
            if (!ex.getRequestMethod().equals("GET")) { send(ex, 405, Map.of("error", "Method not allowed")); return; }
            String path = ex.getRequestURI().getPath();
            String name = path.equals("/") ? "index.html" : path.substring(1);
            if (!STATIC_NAME.matcher(name).matches()) { send(ex, 404, Map.of("error", "Not found")); return; }
            try (InputStream in = ApiServer.class.getResourceAsStream("/web/" + name)) {
                if (in == null) { send(ex, 404, Map.of("error", "Not found")); return; }
                byte[] data = in.readAllBytes();
                ex.getResponseHeaders().set("Content-Type", contentType(name));
                secureHeaders(ex);
                ex.sendResponseHeaders(200, data.length);
                ex.getResponseBody().write(data);
            }
        } finally {
            ex.close();
        }
    }

    private static String contentType(String n) {
        if (n.endsWith(".html")) return "text/html; charset=utf-8";
        if (n.endsWith(".css")) return "text/css; charset=utf-8";
        if (n.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".woff2")) return "font/woff2";
        if (n.endsWith(".woff")) return "font/woff";
        if (n.endsWith(".ttf")) return "font/ttf";
        if (n.endsWith(".otf")) return "font/otf";
        return "application/octet-stream";
    }

    private void secureHeaders(HttpExchange ex) {
        var h = ex.getResponseHeaders();
        h.set("Cache-Control", "no-store");
        h.set("X-Content-Type-Options", "nosniff");
        h.set("Referrer-Policy", "no-referrer");
        h.set("Content-Security-Policy", CSP);
        h.set("Cross-Origin-Opener-Policy", "same-origin");
        h.set("Cross-Origin-Resource-Policy", "same-origin");
    }

    private boolean hostOk(HttpExchange ex) {
        String host = ex.getRequestHeaders().getFirst("Host");
        return ("127.0.0.1:" + port).equals(host) || ("localhost:" + port).equals(host);
    }

    // ------------------------------------------------------------------- API

    private void api(HttpExchange ex) throws IOException {
        try {
            if (!hostOk(ex)) { send(ex, 403, Map.of("error", "Forbidden host")); return; }
            String tok = ex.getRequestHeaders().getFirst("X-NexVault-Token");
            if (tok == null || !MessageDigest.isEqual(tok.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
                send(ex, 401, Map.of("error", "Invalid session. Reopen NexVault with run-vault.bat."));
                return;
            }
            String method = ex.getRequestMethod();
            if (!method.equals("GET")) {
                String origin = ex.getRequestHeaders().getFirst("Origin");
                if (origin != null && !origin.equals("http://127.0.0.1:" + port) && !origin.equals("http://localhost:" + port)) {
                    send(ex, 403, Map.of("error", "Forbidden origin"));
                    return;
                }
                String ct = ex.getRequestHeaders().getFirst("Content-Type");
                if (ct == null || !ct.toLowerCase(Locale.ROOT).startsWith("application/json")) {
                    send(ex, 415, Map.of("error", "Content-Type must be application/json"));
                    return;
                }
            }
            lastRequest = System.currentTimeMillis();
            route(ex, method, ex.getRequestURI().getPath(), query(ex.getRequestURI().getRawQuery()));
        } catch (VaultException e) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("error", e.getMessage());
            if (e.locked()) m.put("locked", true);
            m.putAll(e.extra());
            send(ex, e.status(), m);
        } catch (Json.JsonException | IllegalArgumentException e) {
            send(ex, 400, Map.of("error", e instanceof Json.JsonException ? "Invalid request body." : e.getMessage()));
        } catch (Exception e) {
            System.err.println("Internal error: " + e.getClass().getName() + (e.getMessage() == null ? "" : " - " + e.getMessage()));
            send(ex, 500, Map.of("error", "Something went wrong. Check the NexVault console window for details."));
        } finally {
            ex.close();
        }
    }

    private void route(HttpExchange ex, String method, String path, Map<String, String> q) throws IOException {
        switch (path) {
            case "/api/status" -> { need(method, "GET"); send(ex, 200, status()); return; }
            case "/api/setup" -> {
                need(method, "POST");
                Map<String, char[]> s = Json.parseSecrets(readBody(ex));
                String recovery;
                try { recovery = vault.setup(secret(s, "password")); } finally { wipe(s); }
                Map<String, Object> st = status();
                st.put("recoveryKey", recovery);
                send(ex, 200, st);
                return;
            }
            case "/api/unlock" -> {
                need(method, "POST");
                Map<String, char[]> s = Json.parseSecrets(readBody(ex));
                try { vault.unlock(secret(s, "password")); } finally { wipe(s); }
                send(ex, 200, status());
                return;
            }
            case "/api/master" -> {
                need(method, "POST");
                Map<String, char[]> s = Json.parseSecrets(readBody(ex));
                try { vault.changeMaster(secret(s, "current"), secret(s, "next")); } finally { wipe(s); }
                send(ex, 200, Map.of("ok", true));
                return;
            }
            case "/api/recover" -> {
                need(method, "POST");
                Map<String, char[]> s = Json.parseSecrets(readBody(ex));
                try { vault.recover(secret(s, "recoveryKey"), secret(s, "next")); } finally { wipe(s); }
                send(ex, 200, status());
                return;
            }
            case "/api/recovery/generate" -> {
                need(method, "POST");
                Map<String, char[]> s = Json.parseSecrets(readBody(ex));
                String k;
                try { k = vault.regenerateRecoveryKey(secret(s, "current")); } finally { wipe(s); }
                send(ex, 200, Map.of("recoveryKey", k));
                return;
            }
            case "/api/reset" -> { need(method, "POST"); vault.resetVault(str(body(ex), "confirm")); send(ex, 200, status()); return; }
            case "/api/hint" -> { need(method, "GET"); send(ex, 200, Map.of("hint", vault.hint())); return; }
            case "/api/breach" -> { need(method, "GET"); vault.touch(); send(ex, 200, vault.breachStatus()); return; }
            case "/api/breach/scan" -> { need(method, "POST"); vault.touch(); send(ex, 200, breach.scan(vault)); return; }
            case "/api/profile" -> {
                if (method.equals("PUT")) {
                    vault.assertUnlocked();
                    Map<String, Object> b = body(ex);
                    profile.update(str(b, "name"), str(b, "email"));
                    send(ex, 200, profile.get());
                } else { need(method, "GET"); send(ex, 200, profile.get()); }
                return;
            }
            case "/api/profile/avatar" -> {
                need(method, "POST");
                vault.assertUnlocked();
                Map<String, Object> b = body(ex);
                String kind = str(b, "kind");
                if ("builtin".equals(kind)) profile.setBuiltin(str(b, "id"));
                else if ("custom".equals(kind)) profile.setCustom(str(b, "dataUrl"));
                else if ("none".equals(kind)) profile.clearAvatar();
                else throw new VaultException(400, "Unknown avatar type.");
                send(ex, 200, profile.get());
                return;
            }
            case "/api/alerts" -> {
                vault.assertUnlocked();
                if (method.equals("PUT")) {
                    Map<String, char[]> sec = Json.parseSecrets(readBody(ex));
                    try { alerts.update(sec); } finally { wipe(sec); }
                } else need(method, "GET");
                send(ex, 200, alerts.config());
                return;
            }
            case "/api/alerts/test" -> { need(method, "POST"); vault.assertUnlocked(); alerts.sendTest(); send(ex, 200, alerts.config()); return; }
            case "/api/logo" -> {
                need(method, "GET");
                if (!vault.isUnlocked()) throw new VaultException(423, "The vault is locked.", true);
                Map<String, Object> out = new LinkedHashMap<>();
                if (Boolean.FALSE.equals(vault.settings().get("logosEnabled"))) { out.put("status", "disabled"); send(ex, 200, out); return; }
                String domain = q.get("domain");
                var logo = logos.get(domain, 9000);
                if (logo.isPresent()) {
                    out.put("status", "ok");
                    out.put("dataUrl", "data:" + logo.get().type() + ";base64," + Base64.getEncoder().encodeToString(logo.get().bytes()));
                } else out.put("status", logos.pending(domain) ? "pending" : "none");
                send(ex, 200, out);
                return;
            }
            case "/api/logos/clear" -> { need(method, "POST"); vault.assertUnlocked(); send(ex, 200, Map.of("cleared", logos.clearCache())); return; }
            case "/api/lock" -> { need(method, "POST"); vault.lock("Vault locked"); send(ex, 200, status()); return; }
            case "/api/ping" -> { need(method, "POST"); vault.touch(); send(ex, 200, Map.of("secondsUntilLock", vault.secondsUntilLock())); return; }
            case "/api/quit" -> {
                need(method, "POST");
                send(ex, 200, Map.of("ok", true));
                Executors.newSingleThreadScheduledExecutor().schedule(onQuit, 300, TimeUnit.MILLISECONDS);
                return;
            }
            case "/api/strength" -> {
                need(method, "POST");
                send(ex, 200, strengthMap(PasswordTools.strength(str(body(ex), "password"))));
                return;
            }
            case "/api/generate" -> { need(method, "POST"); send(ex, 200, generate(body(ex))); return; }
            case "/api/categories" -> { need(method, "GET"); send(ex, 200, VaultService.CATEGORIES); return; }
            case "/api/settings" -> {
                if (method.equals("PUT")) { vault.updateSettings(body(ex)); send(ex, 200, vault.settings()); }
                else { need(method, "GET"); send(ex, 200, vault.settings()); }
                return;
            }
            case "/api/dashboard" -> { need(method, "GET"); send(ex, 200, vault.dashboard()); return; }
            case "/api/audit" -> { need(method, "GET"); send(ex, 200, vault.audit()); return; }
            case "/api/clipboard" -> { need(method, "POST"); clipboard(ex, body(ex)); return; }
            case "/api/entries" -> {
                if (method.equals("POST")) {
                    String id = vault.create(input(body(ex)));
                    send(ex, 201, Map.of("id", id));
                } else {
                    need(method, "GET");
                    send(ex, 200, vault.list(q.get("q"), q.get("category"), q.get("sort")));
                }
                return;
            }
            default -> { }
        }
        if (path.startsWith("/api/entries/")) {
            String[] parts = path.substring("/api/entries/".length()).split("/");
            if (parts.length >= 1 && ID.matcher(parts[0]).matches()) {
                String id = parts[0];
                if (parts.length == 1) {
                    switch (method) {
                        case "GET" -> send(ex, 200, vault.get(id));
                        case "PUT" -> { vault.update(id, input(body(ex))); send(ex, 200, Map.of("ok", true)); }
                        case "DELETE" -> { vault.delete(id); send(ex, 200, Map.of("ok", true)); }
                        default -> throw new VaultException(405, "Method not allowed");
                    }
                    return;
                }
                if (parts.length == 2 && parts[1].equals("password")) {
                    need(method, "GET");
                    String pw = vault.revealPassword(id);
                    vault.note("Revealed password", id);
                    send(ex, 200, Map.of("password", pw));
                    return;
                }
            }
        }
        throw new VaultException(404, "Unknown endpoint.");
    }

    private static void need(String actual, String expected) {
        if (!actual.equals(expected)) throw new VaultException(405, "Method not allowed");
    }

    private Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>(vault.settings());
        m.put("initialized", vault.isInitialized());
        m.put("unlocked", vault.isUnlocked());
        m.put("secondsUntilLock", vault.secondsUntilLock());
        m.put("hasRecovery", vault.isInitialized() && vault.hasRecovery());
        m.put("hasHint", !vault.hint().isEmpty());
        m.put("profile", profile.summary());
        m.put("lockoutSeconds", vault.lockoutSeconds());
        m.put("failedAttempts", vault.failedAttempts());
        m.put("maxAttempts", VaultService.MAX_ATTEMPTS);
        m.put("alertsReady", alerts.ready());
        m.put("categories", VaultService.CATEGORIES);
        m.put("version", "1.0.0");
        return m;
    }

    private void clipboard(HttpExchange ex, Map<String, Object> b) throws IOException {
        String id = str(b, "entryId"), field = str(b, "field"), text = str(b, "text");
        String value;
        int clear;
        if (id != null) {
            if ("username".equals(field)) { value = vault.usernameOf(id); clear = 0; }
            else { value = vault.revealPassword(id); clear = vault.clipboardSeconds(); vault.note("Copied password", id); }
        } else {
            if (!vault.isUnlocked()) throw new VaultException(423, "The vault is locked.", true);
            if (text == null || text.isEmpty()) throw new VaultException(400, "Nothing to copy.");
            value = text;
            clear = vault.clipboardSeconds();
        }
        try {
            clipboard.copy(value, clear);
            send(ex, 200, Map.of("ok", true, "clearAfter", clear));
        } catch (ClipboardService.Unavailable e) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("error", e.getMessage());
            m.put("fallback", true);
            m.put("clearAfter", clear);
            send(ex, 501, m);
        }
    }

    private Map<String, Object> generate(Map<String, Object> b) {
        PasswordTools.Options o = new PasswordTools.Options(
                b.get("length") instanceof Number n ? n.intValue() : 20,
                flag(b, "upper", true), flag(b, "lower", true), flag(b, "digits", true), flag(b, "symbols", true),
                flag(b, "avoidAmbiguous", false));
        String pw = PasswordTools.generate(o, crypto.random());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("password", pw);
        m.put("strength", strengthMap(PasswordTools.strength(pw)));
        return m;
    }

    private static boolean flag(Map<String, Object> b, String k, boolean def) { return b.get(k) instanceof Boolean v ? v : def; }

    private static Map<String, Object> strengthMap(PasswordTools.Strength s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("score", s.score()); m.put("label", s.label()); m.put("bits", s.bits()); m.put("feedback", s.feedback());
        return m;
    }

    private static VaultService.Input input(Map<String, Object> b) {
        return new VaultService.Input(str(b, "service"), str(b, "username"), str(b, "password"), str(b, "url"),
                str(b, "notes"), str(b, "category"));
    }

    private static String str(Map<String, Object> b, String k) {
        Object v = b.get(k);
        if (v == null) return null;
        if (!(v instanceof String s)) throw new VaultException(400, "Field '" + k + "' must be text.");
        return s;
    }

    private static char[] secret(Map<String, char[]> s, String k) {
        char[] v = s.get(k);
        if (v == null) throw new VaultException(400, "Missing field '" + k + "'.");
        return v;
    }

    private static void wipe(Map<String, char[]> s) { s.values().forEach(CryptoService::wipe); }

    private static Map<String, Object> body(HttpExchange ex) throws IOException {
        byte[] raw = readBody(ex);
        if (raw.length == 0) return new LinkedHashMap<>();
        return Json.parseObject(new String(raw, StandardCharsets.UTF_8));
    }

    private static byte[] readBody(HttpExchange ex) throws IOException {
        byte[] b = ex.getRequestBody().readNBytes(MAX_BODY + 1);
        if (b.length > MAX_BODY) throw new VaultException(413, "Request too large.");
        return b;
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> m = new HashMap<>();
        if (raw == null) return m;
        for (String kv : raw.split("&")) {
            int i = kv.indexOf('=');
            if (i <= 0) continue;
            m.put(java.net.URLDecoder.decode(kv.substring(0, i), StandardCharsets.UTF_8),
                    java.net.URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8));
        }
        return m;
    }

    private void send(HttpExchange ex, int status, Object payload) throws IOException {
        byte[] data = Json.write(payload).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        secureHeaders(ex);
        ex.sendResponseHeaders(status, data.length);
        ex.getResponseBody().write(data);
    }
}

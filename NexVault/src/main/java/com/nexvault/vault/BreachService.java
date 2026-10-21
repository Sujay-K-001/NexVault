package com.nexvault.vault;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Breach monitoring using the Pwned Passwords range API with k-anonymity: only the first 5 hex characters of a
 * password's SHA-1 are ever sent. Full hashes and passwords never leave this machine.
 */
public final class BreachService {
    public interface Fetcher { String range(String prefix) throws IOException; }

    private final Fetcher fetcher;

    public BreachService(Fetcher fetcher) { this.fetcher = fetcher; }

    public static Fetcher httpFetcher() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        return prefix -> {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.pwnedpasswords.com/range/" + prefix))
                        .timeout(Duration.ofSeconds(12)).header("Add-Padding", "true").header("User-Agent", "NexVault").GET().build();
                HttpResponse<String> r = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode());
                return r.body();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted", e);
            }
        };
    }

    /** Offline stand-in for the dev/test mode: pretends a few famous passwords are breached. */
    public static Fetcher devFetcher(String... pwned) {
        return prefix -> {
            StringBuilder b = new StringBuilder("0000000000000000000000000000000000A:0\r\n");
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                for (String pw : pwned) {
                    String h = HexFormat.of().withUpperCase().formatHex(md.digest(pw.getBytes(StandardCharsets.UTF_8)));
                    if (h.startsWith(prefix)) b.append(h.substring(5)).append(":4242\r\n");
                }
            } catch (Exception e) { throw new IOException(e); }
            return b.toString();
        };
    }

    public Map<String, Object> scan(VaultService vault) {
        Map<String, String> hashes = vault.sha1Hashes();
        Map<String, Integer> counts;
        try {
            counts = lookup(hashes);
        } catch (IOException e) {
            throw new VaultException(502, "Could not reach the breach-check service. Check your internet connection and try again.");
        }
        return vault.recordBreaches(counts);
    }

    Map<String, Integer> lookup(Map<String, String> hashes) throws IOException {
        Map<String, List<String>> byPrefix = new HashMap<>();
        for (Map.Entry<String, String> e : hashes.entrySet()) byPrefix.computeIfAbsent(e.getValue().substring(0, 5), k -> new ArrayList<>()).add(e.getKey());
        Map<String, Integer> out = new ConcurrentHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<Void>> fs = new ArrayList<>();
            for (Map.Entry<String, List<String>> e : byPrefix.entrySet()) {
                fs.add(pool.submit(() -> {
                    Map<String, Integer> suffixes = parse(fetcher.range(e.getKey()));
                    for (String id : e.getValue()) out.put(id, suffixes.getOrDefault(hashes.get(id).substring(5), 0));
                    return null;
                }));
            }
            for (Future<Void> f : fs) f.get(90, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException e) {
            throw new IOException("Lookup failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        } finally {
            pool.shutdownNow();
        }
        return out;
    }

    public static Map<String, Integer> parse(String body) {
        Map<String, Integer> m = new HashMap<>();
        for (String line : body.split("\\r?\\n")) {
            int i = line.indexOf(':');
            if (i < 1) continue;
            try {
                int c = Integer.parseInt(line.substring(i + 1).trim());
                if (c > 0) m.put(line.substring(0, i).trim().toUpperCase(Locale.ROOT), c);
            } catch (NumberFormatException ignored) { }
        }
        return m;
    }
}

package com.nexvault.logo;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Fetches a website's own icon (apple-touch-icon, declared icon, favicon) on the server side, so the browser never
 * contacts the site and no cookies/referrer are sent. Results (including misses) are cached on disk under hashed names.
 * Only public https hosts are contacted (SSRF-safe); images are validated by content, never trusted by type.
 */
public final class LogoService {
    public record Fetched(int status, String contentType, byte[] body) {}
    public record Logo(String type, byte[] bytes) {}
    public interface Fetcher { Fetched get(String url, int maxBytes) throws IOException; }

    private static final Pattern DOMAIN = Pattern.compile("^(?=.{4,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z][a-z0-9-]{1,23}$");
    private static final Pattern LINK = Pattern.compile("<link\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final long POS_TTL = 30L * 86_400_000L, NEG_TTL = 3L * 86_400_000L;
    private static final int MAX_IMG = 200_000, MAX_HTML = 150_000;

    private final Fetcher fetcher;
    private final Path dir;
    private final ConcurrentHashMap<String, CompletableFuture<Optional<Logo>>> inflight = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(4, r -> { Thread t = new Thread(r, "logo-fetch"); t.setDaemon(true); return t; });

    public LogoService(Fetcher fetcher, Path cacheDir) {
        this.fetcher = fetcher;
        this.dir = cacheDir;
        try { Files.createDirectories(dir); } catch (IOException ignored) { }
    }

    public static boolean validDomain(String d) { return d != null && DOMAIN.matcher(d).matches(); }

    /** Blocks up to timeoutMs; empty = no logo (or still loading, see {@link #pending}). */
    public Optional<Logo> get(String domain, long timeoutMs) {
        String d = domain == null ? "" : domain.trim().toLowerCase(Locale.ROOT);
        if (d.startsWith("www.")) d = d.substring(4);
        if (!validDomain(d)) return Optional.empty();
        Optional<Logo> cached = readCache(d);
        if (cached != null) return cached;
        final String key = d;
        CompletableFuture<Optional<Logo>> f = inflight.computeIfAbsent(key, k -> CompletableFuture.supplyAsync(() -> {
            try {
                Optional<Logo> r = fetchFresh(k);
                writeCache(k, r);
                return r;
            } finally { inflight.remove(k); }
        }, pool));
        try { return f.get(timeoutMs, TimeUnit.MILLISECONDS); }
        catch (TimeoutException e) { return Optional.empty(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return Optional.empty(); }
        catch (ExecutionException e) { return Optional.empty(); }
    }

    /** True while a fetch for this domain is still running. */
    public boolean pending(String domain) {
        String d = domain == null ? "" : domain.trim().toLowerCase(Locale.ROOT);
        if (d.startsWith("www.")) d = d.substring(4);
        return inflight.containsKey(d);
    }

    public int clearCache() {
        int n = 0;
        try (Stream<Path> s = Files.list(dir)) {
            for (Path p : (Iterable<Path>) s::iterator) { if (Files.deleteIfExists(p)) n++; }
        } catch (IOException ignored) { }
        return n;
    }

    // ---------------------------------------------------------------- cache

    private String hash(String d) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(d.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    /** null = not cached / expired; Optional.empty = cached miss. */
    private Optional<Logo> readCache(String d) {
        String h = hash(d);
        try {
            Path none = dir.resolve(h + ".none");
            if (Files.exists(none)) {
                if (System.currentTimeMillis() - Files.getLastModifiedTime(none).toMillis() < NEG_TTL) return Optional.empty();
                Files.deleteIfExists(none);
            }
            Path meta = dir.resolve(h + ".meta"), img = dir.resolve(h + ".img");
            if (Files.exists(meta) && Files.exists(img)) {
                if (System.currentTimeMillis() - Files.getLastModifiedTime(meta).toMillis() < POS_TTL)
                    return Optional.of(new Logo(Files.readString(meta).trim(), Files.readAllBytes(img)));
            }
        } catch (IOException ignored) { }
        return null;
    }

    private void writeCache(String d, Optional<Logo> r) {
        String h = hash(d);
        try {
            if (r.isPresent()) {
                Files.write(dir.resolve(h + ".img"), r.get().bytes());
                Files.writeString(dir.resolve(h + ".meta"), r.get().type());
                Files.deleteIfExists(dir.resolve(h + ".none"));
            } else {
                Files.writeString(dir.resolve(h + ".none"), "none");
            }
        } catch (IOException ignored) { }
    }

    // -------------------------------------------------------------- retrieval

    private Optional<Logo> fetchFresh(String domain) {
        for (String d : candidates(domain)) {
            Optional<Logo> l = tryDomain(d);
            if (l.isPresent()) return l;
        }
        return Optional.empty();
    }

    static List<String> candidates(String domain) {
        List<String> out = new ArrayList<>();
        out.add(domain);
        String[] labels = domain.split("\\.");
        int n = labels.length;
        while (n > 2) {
            n--;
            String[] rest = Arrays.copyOfRange(labels, labels.length - n, labels.length);
            String parent = String.join(".", rest);
            boolean ccSld = rest.length == 2 && rest[1].length() == 2 && Set.of("co", "com", "org", "net", "gov", "edu", "ac").contains(rest[0]);
            if (!ccSld) out.add(parent);
        }
        return out;
    }

    private Optional<Logo> tryDomain(String d) {
        List<String> hosts = new ArrayList<>(List.of(d));
        if (d.chars().filter(c -> c == '.').count() == 1) hosts.add("www." + d);
        for (String host : hosts) {
            Optional<Logo> l = fromUrl("https://" + host + "/apple-touch-icon.png");
            if (l.isPresent()) return l;
            try {
                Fetched page = fetcher.get("https://" + host + "/", MAX_HTML);
                if (page.status() == 200 && page.body() != null) {
                    for (String href : declaredIcons(new String(page.body(), StandardCharsets.UTF_8), "https://" + host + "/")) {
                        l = fromUrl(href);
                        if (l.isPresent()) return l;
                    }
                }
            } catch (IOException | RuntimeException ignored) { }
            l = fromUrl("https://" + host + "/favicon.ico");
            if (l.isPresent()) return l;
        }
        return Optional.empty();
    }

    private Optional<Logo> fromUrl(String url) {
        try {
            Fetched f = fetcher.get(url, MAX_IMG);
            if (f.status() != 200 || f.body() == null) return Optional.empty();
            String type = validate(f.body());
            return type == null ? Optional.empty() : Optional.of(new Logo(type, f.body()));
        } catch (IOException | RuntimeException e) { return Optional.empty(); }
    }

    /** Icon URLs declared in the page, best first (apple-touch-icon, then larger icons). SVG and data: are skipped. */
    static List<String> declaredIcons(String html, String base) {
        record Cand(String url, int score) {}
        List<Cand> c = new ArrayList<>();
        Matcher m = LINK.matcher(html);
        while (m.find()) {
            String tag = m.group();
            String rel = attr(tag, "rel"), href = attr(tag, "href");
            if (rel == null || href == null) continue;
            rel = rel.toLowerCase(Locale.ROOT);
            if (!rel.contains("icon")) continue;
            String lower = href.toLowerCase(Locale.ROOT);
            if (lower.startsWith("data:") || lower.contains(".svg")) continue;
            int size = 16;
            String sizes = attr(tag, "sizes");
            if (sizes != null) { Matcher sm = Pattern.compile("(\\d+)x\\d+").matcher(sizes); if (sm.find()) size = Integer.parseInt(sm.group(1)); }
            try {
                String abs = URI.create(base).resolve(href.trim()).toString();
                if (abs.startsWith("https://")) c.add(new Cand(abs, (rel.contains("apple-touch") ? 1000 : 0) + size));
            } catch (IllegalArgumentException ignored) { }
        }
        c.sort((a, b) -> b.score() - a.score());
        List<String> out = new ArrayList<>();
        for (Cand x : c) if (!out.contains(x.url())) out.add(x.url());
        return out.size() > 4 ? out.subList(0, 4) : out;
    }

    private static String attr(String tag, String name) {
        Matcher m = Pattern.compile("\\b" + name + "\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')", Pattern.CASE_INSENSITIVE).matcher(tag);
        if (!m.find()) return null;
        return m.group(1) != null ? m.group(1) : m.group(2);
    }

    /** Returns the image MIME type if the bytes are a real, decodable raster image; otherwise null. */
    static String validate(byte[] b) {
        if (b == null || b.length < 100 || b.length > MAX_IMG) return null;
        String type;
        if ((b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') type = "image/png";
        else if ((b[0] & 0xff) == 0xFF && (b[1] & 0xff) == 0xD8 && (b[2] & 0xff) == 0xFF) type = "image/jpeg";
        else if (b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') type = "image/gif";
        else if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        else if (b[0] == 0 && b[1] == 0 && b[2] == 1 && b[3] == 0 && b[4] > 0) return "image/x-icon";
        else return null;
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(b));
            if (img == null || img.getWidth() < 8 || img.getHeight() < 8 || img.getWidth() > 2048) return null;
        } catch (IOException | RuntimeException e) { return null; }
        return type;
    }

    // ------------------------------------------------------------ real network

    public static boolean isPublicHost(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (h.equals("localhost") || h.endsWith(".local") || h.endsWith(".internal") || h.endsWith(".localhost") || !h.contains(".")) return false;
        if (h.matches("^[0-9.]+$") || h.contains(":")) return false;   // IP literals are never needed for logos
        try {
            for (InetAddress a : InetAddress.getAllByName(h)) {
                byte[] r = a.getAddress();
                if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress() || a.isMulticastAddress()) return false;
                if (r.length == 4 && (r[0] & 0xff) == 100 && (r[1] & 0xc0) == 64) return false;          // 100.64.0.0/10
                if (r.length == 4 && (r[0] & 0xff) == 0) return false;
                if (r.length == 16 && (r[0] & 0xfe) == 0xfc) return false;                                // fc00::/7
            }
            return true;
        } catch (IOException e) { return false; }
    }

    public static Fetcher httpFetcher() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).followRedirects(HttpClient.Redirect.NEVER).build();
        return (url, max) -> {
            String cur = url;
            for (int hop = 0; hop < 4; hop++) {
                URI u = URI.create(cur);
                if (!"https".equals(u.getScheme()) || u.getHost() == null || u.getPort() != -1 && u.getPort() != 443 || !isPublicHost(u.getHost()))
                    throw new IOException("Blocked address");
                HttpRequest req = HttpRequest.newBuilder(u).timeout(Duration.ofSeconds(7))
                        .header("User-Agent", "Mozilla/5.0 (compatible; NexVault icon fetcher)").header("Accept", "image/*,text/html;q=0.8,*/*;q=0.5").GET().build();
                HttpResponse<InputStream> r;
                try { r = client.send(req, HttpResponse.BodyHandlers.ofInputStream()); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Interrupted"); }
                try (InputStream in = r.body()) {
                    int sc = r.statusCode();
                    if (sc >= 300 && sc < 400) {
                        String loc = r.headers().firstValue("Location").orElseThrow(() -> new IOException("Bad redirect"));
                        cur = u.resolve(loc).toString();
                        continue;
                    }
                    byte[] body = in.readNBytes(max + 1);
                    if (body.length > max) return new Fetched(413, "", null);
                    return new Fetched(sc, r.headers().firstValue("Content-Type").orElse(""), body);
                }
            }
            throw new IOException("Too many redirects");
        };
    }
}

package com.nexvault.util;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Minimal dependency-free JSON reader/writer. */
public final class Json {
    private Json() {}

    public static final class JsonException extends RuntimeException {
        public JsonException(String m) { super(m); }
    }

    public static Object parse(String s) {
        P p = new P(s);
        p.ws();
        Object v = p.value(0);
        p.ws();
        if (p.i != s.length()) throw new JsonException("Unexpected trailing data");
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String s) {
        Object o = parse(s);
        if (!(o instanceof Map)) throw new JsonException("Expected a JSON object");
        return (Map<String, Object>) o;
    }

    private static final class P {
        final String s;
        int i;
        P(String s) { this.s = s; }

        void ws() { while (i < s.length() && " \t\r\n".indexOf(s.charAt(i)) >= 0) i++; }

        Object value(int depth) {
            if (depth > 64) throw new JsonException("Nesting too deep");
            if (i >= s.length()) throw new JsonException("Unexpected end of input");
            switch (s.charAt(i)) {
                case '{': return obj(depth);
                case '[': return arr(depth);
                case '"': return str();
                case 't': lit("true"); return Boolean.TRUE;
                case 'f': lit("false"); return Boolean.FALSE;
                case 'n': lit("null"); return null;
                default: return num();
            }
        }

        void lit(String w) {
            if (!s.startsWith(w, i)) throw new JsonException("Invalid literal");
            i += w.length();
        }

        Map<String, Object> obj(int depth) {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; ws();
            if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
            while (true) {
                ws();
                if (i >= s.length() || s.charAt(i) != '"') throw new JsonException("Expected a string key");
                String k = str();
                ws();
                if (i >= s.length() || s.charAt(i) != ':') throw new JsonException("Expected ':'");
                i++; ws();
                m.put(k, value(depth + 1));
                ws();
                if (i >= s.length()) throw new JsonException("Unexpected end of input");
                char c = s.charAt(i++);
                if (c == '}') return m;
                if (c != ',') throw new JsonException("Expected ',' or '}'");
            }
        }

        List<Object> arr(int depth) {
            List<Object> l = new ArrayList<>();
            i++; ws();
            if (i < s.length() && s.charAt(i) == ']') { i++; return l; }
            while (true) {
                ws();
                l.add(value(depth + 1));
                ws();
                if (i >= s.length()) throw new JsonException("Unexpected end of input");
                char c = s.charAt(i++);
                if (c == ']') return l;
                if (c != ',') throw new JsonException("Expected ',' or ']'");
            }
        }

        String str() {
            i++;
            StringBuilder b = new StringBuilder();
            while (true) {
                if (i >= s.length()) throw new JsonException("Unterminated string");
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c == '\\') {
                    if (i >= s.length()) throw new JsonException("Unterminated escape");
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"': case '\\': case '/': b.append(e); break;
                        case 'b': b.append('\b'); break;
                        case 'f': b.append('\f'); break;
                        case 'n': b.append('\n'); break;
                        case 'r': b.append('\r'); break;
                        case 't': b.append('\t'); break;
                        case 'u':
                            if (i + 4 > s.length()) throw new JsonException("Bad unicode escape");
                            try { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); }
                            catch (NumberFormatException ex) { throw new JsonException("Bad unicode escape"); }
                            i += 4;
                            break;
                        default: throw new JsonException("Bad escape");
                    }
                } else if (c < 0x20) {
                    throw new JsonException("Control character in string");
                } else b.append(c);
            }
        }

        Number num() {
            int st = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (st == i) throw new JsonException("Unexpected character");
            String t = s.substring(st, i);
            try {
                if (t.matches("-?\\d{1,18}")) return Long.parseLong(t);
                return Double.parseDouble(t);
            } catch (NumberFormatException e) { throw new JsonException("Bad number"); }
        }
    }

    public static String write(Object o) {
        StringBuilder b = new StringBuilder();
        w(b, o);
        return b.toString();
    }

    private static void w(StringBuilder b, Object o) {
        if (o == null) b.append("null");
        else if (o instanceof String s) quote(b, s);
        else if (o instanceof Boolean || o instanceof Integer || o instanceof Long || o instanceof Short) b.append(o);
        else if (o instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) b.append("null");
            else if (d == Math.rint(d) && Math.abs(d) < 1e15) b.append((long) d);
            else b.append(d);
        } else if (o instanceof Map<?, ?> m) {
            b.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) b.append(',');
                first = false;
                quote(b, String.valueOf(e.getKey()));
                b.append(':');
                w(b, e.getValue());
            }
            b.append('}');
        } else if (o instanceof Iterable<?> it) {
            b.append('[');
            boolean first = true;
            for (Object x : it) {
                if (!first) b.append(',');
                first = false;
                w(b, x);
            }
            b.append(']');
        } else quote(b, String.valueOf(o));
    }

    private static void quote(StringBuilder b, String s) {
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                case '\b': b.append("\\b"); break;
                case '\f': b.append("\\f"); break;
                default:
                    if (c < 0x20 || c == '\u2028' || c == '\u2029') b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        b.append('"');
    }

    /**
     * Parses a flat JSON object whose values are all strings directly into char[] values, so that
     * secrets (master passwords) never become immutable Strings on the server. Every intermediate
     * buffer is zeroed. The caller must wipe the returned arrays.
     */
    public static Map<String, char[]> parseSecrets(byte[] body) {
        CharBuffer cb = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(body));
        char[] all = cb.array();
        int n = cb.limit();
        Map<String, char[]> out = new HashMap<>();
        try {
            int i = skipWs(all, 0, n);
            if (i >= n || all[i] != '{') throw new JsonException("Expected a JSON object");
            i = skipWs(all, i + 1, n);
            if (i < n && all[i] == '}') return out;
            while (true) {
                i = skipWs(all, i, n);
                if (i >= n || all[i] != '"') throw new JsonException("Expected a string key");
                int[] pos = {i};
                char[] key = readString(all, pos, n);
                String k = new String(key);
                Arrays.fill(key, '\0');
                i = skipWs(all, pos[0], n);
                if (i >= n || all[i] != ':') throw new JsonException("Expected ':'");
                i = skipWs(all, i + 1, n);
                if (i >= n || all[i] != '"') throw new JsonException("Secret values must be strings");
                pos[0] = i;
                out.put(k, readString(all, pos, n));
                i = skipWs(all, pos[0], n);
                if (i >= n) throw new JsonException("Unexpected end of input");
                if (all[i] == '}') return out;
                if (all[i] != ',') throw new JsonException("Expected ',' or '}'");
                i++;
            }
        } catch (RuntimeException e) {
            out.values().forEach(v -> Arrays.fill(v, '\0'));
            throw e instanceof JsonException ? e : new JsonException("Malformed JSON");
        } finally {
            Arrays.fill(all, '\0');
            Arrays.fill(body, (byte) 0);
        }
    }

    private static int skipWs(char[] a, int i, int n) {
        while (i < n && (a[i] == ' ' || a[i] == '\t' || a[i] == '\r' || a[i] == '\n')) i++;
        return i;
    }

    private static char[] readString(char[] a, int[] pos, int n) {
        int i = pos[0] + 1;
        char[] buf = new char[n];
        int len = 0;
        try {
            while (true) {
                if (i >= n) throw new JsonException("Unterminated string");
                char c = a[i++];
                if (c == '"') break;
                if (c == '\\') {
                    if (i >= n) throw new JsonException("Unterminated escape");
                    char e = a[i++];
                    switch (e) {
                        case '"': case '\\': case '/': buf[len++] = e; break;
                        case 'b': buf[len++] = '\b'; break;
                        case 'f': buf[len++] = '\f'; break;
                        case 'n': buf[len++] = '\n'; break;
                        case 'r': buf[len++] = '\r'; break;
                        case 't': buf[len++] = '\t'; break;
                        case 'u':
                            if (i + 4 > n) throw new JsonException("Bad unicode escape");
                            int v = 0;
                            for (int k = 0; k < 4; k++) {
                                int d = Character.digit(a[i + k], 16);
                                if (d < 0) throw new JsonException("Bad unicode escape");
                                v = v * 16 + d;
                            }
                            buf[len++] = (char) v;
                            i += 4;
                            break;
                        default: throw new JsonException("Bad escape");
                    }
                } else if (c < 0x20) throw new JsonException("Control character in string");
                else buf[len++] = c;
            }
            pos[0] = i;
            return Arrays.copyOf(buf, len);
        } finally {
            Arrays.fill(buf, '\0');
        }
    }
}

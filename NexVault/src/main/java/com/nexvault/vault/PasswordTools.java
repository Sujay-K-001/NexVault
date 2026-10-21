package com.nexvault.vault;

import java.security.SecureRandom;
import java.util.*;

/** Password generation and a transparent, heuristic strength estimator. */
public final class PasswordTools {
    private PasswordTools() {}

    public static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    public static final String DIGITS = "0123456789";
    public static final String SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?/~";
    private static final String AMBIGUOUS = "Il1O0o|";

    public record Options(int length, boolean upper, boolean lower, boolean digits, boolean symbols, boolean avoidAmbiguous) {}

    public static String generate(Options o, SecureRandom rnd) {
        if (o.length() < 8 || o.length() > 128) throw new IllegalArgumentException("Length must be between 8 and 128.");
        List<String> pools = new ArrayList<>();
        if (o.upper()) pools.add(filter(UPPER, o.avoidAmbiguous()));
        if (o.lower()) pools.add(filter(LOWER, o.avoidAmbiguous()));
        if (o.digits()) pools.add(filter(DIGITS, o.avoidAmbiguous()));
        if (o.symbols()) pools.add(filter(SYMBOLS, o.avoidAmbiguous()));
        if (pools.isEmpty()) throw new IllegalArgumentException("Select at least one character type.");
        StringBuilder all = new StringBuilder();
        pools.forEach(all::append);
        char[] out = new char[o.length()];
        int n = 0;
        for (String p : pools) out[n++] = p.charAt(rnd.nextInt(p.length())); // guarantee one of each selected type
        while (n < out.length) out[n++] = all.charAt(rnd.nextInt(all.length()));
        for (int i = out.length - 1; i > 0; i--) { // Fisher-Yates shuffle
            int j = rnd.nextInt(i + 1);
            char t = out[i]; out[i] = out[j]; out[j] = t;
        }
        String s = new String(out);
        Arrays.fill(out, '\0');
        return s;
    }

    private static String filter(String set, boolean avoid) {
        if (!avoid) return set;
        StringBuilder b = new StringBuilder();
        for (char c : set.toCharArray()) if (AMBIGUOUS.indexOf(c) < 0) b.append(c);
        return b.toString();
    }

    // ---------------------------------------------------------------- strength

    public record Strength(int score, String label, double bits, List<String> feedback) {
        public boolean weak() { return score <= 1; }
    }

    private static final String[] LABELS = {"Very weak", "Weak", "Fair", "Strong", "Excellent"};
    private static final Set<String> COMMON = new HashSet<>(Arrays.asList(
            "password", "passw0rd", "pass", "admin", "administrator", "root", "login", "welcome", "letmein", "qwerty",
            "qwertyuiop", "asdfgh", "asdfghjkl", "zxcvbn", "zxcvbnm", "abc", "abcdef", "iloveyou", "monkey", "dragon",
            "master", "sunshine", "princess", "football", "baseball", "soccer", "hockey", "batman", "superman",
            "shadow", "michael", "jennifer", "jordan", "ashley", "charlie", "mustang", "freedom", "whatever",
            "trustno", "secret", "summer", "winter", "spring", "autumn", "fall", "january", "february", "march",
            "april", "june", "july", "august", "september", "october", "november", "december", "hello", "hunter",
            "starwars", "pokemon", "internet", "computer", "google", "facebook", "changeme", "default", "guest",
            "test", "testing", "user", "temp", "love", "angel", "hottie", "ninja", "access", "flower", "cheese",
            "killer", "pepper", "ginger", "buster", "tigger", "joshua", "matrix", "yankees", "cowboys", "pass123"));
    private static final String[] KEYBOARD = {"qwertyuiop", "asdfghjkl", "zxcvbnm", "1234567890", "0987654321"};

    public static Strength strength(String pw) {
        if (pw == null || pw.isEmpty()) return new Strength(0, LABELS[0], 0, List.of("Enter a password."));
        List<String> fb = new ArrayList<>();
        boolean lo = false, up = false, di = false, sy = false;
        Set<Integer> uniq = new HashSet<>();
        int len = pw.codePointCount(0, pw.length());
        for (int i = 0; i < pw.length(); ) {
            int c = pw.codePointAt(i);
            i += Character.charCount(c);
            uniq.add(c);
            if (c >= 'a' && c <= 'z') lo = true;
            else if (c >= 'A' && c <= 'Z') up = true;
            else if (c >= '0' && c <= '9') di = true;
            else sy = true;
        }
        int pool = (lo ? 26 : 0) + (up ? 26 : 0) + (di ? 10 : 0) + (sy ? 33 : 0);
        double effLen = uniq.size() + 0.35 * (len - uniq.size());
        double bits = effLen * (Math.log(pool) / Math.log(2));

        String low = pw.toLowerCase(Locale.ROOT);
        String norm = leet(low);
        String stripped = norm.replaceAll("[^a-z]+$", "").replaceAll("^[^a-z]+", "");
        boolean common = COMMON.contains(low) || COMMON.contains(norm) || COMMON.contains(stripped);
        boolean containsCommon = false;
        if (!common) {
            for (String w : COMMON) {
                if (w.length() >= 4 && norm.contains(w) && w.length() * 2 >= len) { containsCommon = true; break; }
            }
        }
        if (common) { bits = Math.min(bits, 18); fb.add("This is a very common password or a trivial variation of one."); }
        else if (containsCommon) { bits = Math.min(bits, 32); fb.add("Avoid common words, even with substitutions."); }

        int penalties = 0;
        for (String row : KEYBOARD) if (hasRun(low, row, 4)) penalties++;
        if (hasStep(low, 4)) penalties++;
        if (penalties > 0) { bits -= 10 * Math.min(penalties, 2); fb.add("Avoid keyboard patterns and sequences like 1234 or abcd."); }
        if (len - uniq.size() > len / 2 && len >= 6) fb.add("Too many repeated characters.");
        if (len < 8) bits = Math.min(bits, 28);
        bits = Math.max(0, bits);

        if (len < 12) fb.add("Use at least 12 characters.");
        if (!up || !lo) fb.add("Mix uppercase and lowercase letters.");
        if (!di) fb.add("Add numbers.");
        if (!sy) fb.add("Add symbols.");

        int score = bits < 30 ? 0 : bits < 45 ? 1 : bits < 65 ? 2 : bits < 85 ? 3 : 4;
        if (score >= 3) fb.removeIf(f -> f.startsWith("Use at least") || f.startsWith("Add ") || f.startsWith("Mix "));
        return new Strength(score, LABELS[score], Math.round(bits * 10) / 10.0, fb.size() > 4 ? fb.subList(0, 4) : fb);
    }

    private static String leet(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '@': case '4': b.append('a'); break;
                case '0': b.append('o'); break;
                case '1': case '!': case '|': b.append('i'); break;
                case '3': b.append('e'); break;
                case '$': case '5': b.append('s'); break;
                case '7': case '+': b.append('t'); break;
                default: b.append(c);
            }
        }
        return b.toString();
    }

    private static boolean hasRun(String s, String row, int min) {
        for (int i = 0; i + min <= row.length(); i++) {
            String sub = row.substring(i, i + min);
            if (s.contains(sub) || s.contains(new StringBuilder(sub).reverse())) return true;
        }
        return false;
    }

    private static boolean hasStep(String s, int min) {
        int run = 1, dir = 0;
        for (int i = 1; i < s.length(); i++) {
            int d = s.charAt(i) - s.charAt(i - 1);
            if ((d == 1 || d == -1) && (dir == 0 || d == dir)) { run++; dir = d; }
            else { run = (d == 1 || d == -1) ? 2 : 1; dir = (d == 1 || d == -1) ? d : 0; }
            if (run >= min) return true;
        }
        return false;
    }
}

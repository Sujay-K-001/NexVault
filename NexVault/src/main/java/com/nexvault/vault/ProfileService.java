package com.nexvault.vault;

import com.nexvault.crypto.CryptoService;
import com.nexvault.db.VaultStore;
import com.nexvault.mail.SmtpClient;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The master's profile: name, e-mail (alert recipient) and avatar. Stored as plain settings so it can greet you on the lock screen. */
public final class ProfileService {
    public static final List<String> BUILTIN = List.of("fox", "cat", "robot", "owl", "astro", "ninja", "wizard", "panda");
    private static final Pattern DATA_URL = Pattern.compile("^data:image/(png|jpeg|webp);base64,([A-Za-z0-9+/]+={0,2})$");
    static final int MAX_AVATAR_BYTES = 150_000;

    private final VaultStore store;

    public ProfileService(VaultStore store) { this.store = store; }

    public String name() { return store.getSetting("profileName").orElse(""); }
    public String email() { return store.getSetting("profileEmail").orElse(""); }

    public Map<String, Object> summary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name());
        m.put("email", email());
        m.put("avatarKind", store.getSetting("avatarKind").orElse("none"));
        m.put("avatarId", store.getSetting("avatarId").orElse(""));
        return m;
    }

    /** Full profile including the custom avatar image (data URL), if any. */
    public Map<String, Object> get() {
        Map<String, Object> m = summary();
        m.put("avatarData", "custom".equals(m.get("avatarKind")) ? store.getSetting("avatarData").orElse("") : "");
        return m;
    }

    public void update(String name, String email) {
        String n = name == null ? "" : name.trim();
        if (n.length() > 60) throw new VaultException(400, "The name must be at most 60 characters.");
        if (n.chars().anyMatch(Character::isISOControl)) throw new VaultException(400, "The name contains invalid characters.");
        String e = email == null ? "" : email.trim();
        if (!e.isEmpty() && (e.length() > 254 || !SmtpClient.EMAIL.matcher(e).matches())) throw new VaultException(400, "Enter a valid e-mail address.");
        store.putSetting("profileName", n);
        store.putSetting("profileEmail", e);
    }

    public void setBuiltin(String id) {
        if (!BUILTIN.contains(id)) throw new VaultException(400, "Unknown avatar.");
        store.putSetting("avatarKind", "builtin");
        store.putSetting("avatarId", id);
        store.putSetting("avatarData", "");
    }

    public void setCustom(String dataUrl) {
        Matcher m = dataUrl == null ? null : DATA_URL.matcher(dataUrl);
        if (m == null || !m.matches()) throw new VaultException(400, "Choose a PNG, JPEG or WebP image.");
        byte[] raw;
        try { raw = Base64.getDecoder().decode(m.group(2)); } catch (IllegalArgumentException ex) { throw new VaultException(400, "That image could not be read."); }
        if (raw.length > MAX_AVATAR_BYTES) throw new VaultException(400, "That image is too large.");
        if (!magicMatches(m.group(1), raw)) throw new VaultException(400, "That file is not a valid image.");
        store.putSetting("avatarKind", "custom");
        store.putSetting("avatarId", "");
        store.putSetting("avatarData", dataUrl);
    }

    public void clearAvatar() {
        store.putSetting("avatarKind", "none");
        store.putSetting("avatarId", "");
        store.putSetting("avatarData", "");
    }

    static boolean magicMatches(String type, byte[] b) {
        if (b.length < 12) return false;
        return switch (type) {
            case "png" -> (b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
            case "jpeg" -> (b[0] & 0xff) == 0xFF && (b[1] & 0xff) == 0xD8 && (b[2] & 0xff) == 0xFF;
            case "webp" -> b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            default -> false;
        };
    }
}

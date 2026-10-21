package com.nexvault.vault;

import java.util.LinkedHashMap;
import java.util.Map;

/** A user-facing error with an HTTP-style status code and optional machine-readable extras. */
public class VaultException extends RuntimeException {
    private final int status;
    private final boolean locked;
    private final Map<String, Object> extra = new LinkedHashMap<>();

    public VaultException(int status, String message) { this(status, message, false); }

    public VaultException(int status, String message, boolean locked) {
        super(message);
        this.status = status;
        this.locked = locked;
    }

    public VaultException with(String key, Object value) { extra.put(key, value); return this; }

    public int status() { return status; }
    public boolean locked() { return locked; }
    public Map<String, Object> extra() { return extra; }
}

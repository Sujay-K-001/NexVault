package com.nexvault.vault;

/** Hook for security notifications (implemented by the e-mail alert service). */
public interface SecurityEvents {
    SecurityEvents NONE = (attempts, lockedOut, lockoutSeconds) -> { };

    /** Called after every consecutive failed master-password attempt beyond the alert threshold. */
    void failedAttempts(int attempts, boolean lockedOut, long lockoutSeconds);
}

package com.nexvault.util;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * System clipboard access with automatic clearing. Secrets are copied by the server so they never
 * need to pass through the browser. Only clears the clipboard if it still holds what we put there.
 */
public final class ClipboardService {
    public static final class Unavailable extends Exception {
        public Unavailable(String m) { super(m); }
    }

    private final ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "clipboard-clear");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> pending;
    private byte[] ourHash;

    public synchronized void copy(String text, int clearSeconds) throws Unavailable {
        if (GraphicsEnvironment.isHeadless()) throw new Unavailable("No system clipboard available (headless).");
        try {
            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            cb.setContents(new StringSelection(text), null);
        } catch (Exception e) {
            throw new Unavailable("Could not access the system clipboard: " + e.getMessage());
        }
        if (pending != null) pending.cancel(false);
        pending = null;
        ourHash = null;
        if (clearSeconds > 0) {
            ourHash = sha(text);
            pending = ex.schedule(this::clearIfOurs, clearSeconds, TimeUnit.SECONDS);
        }
    }

    /** Clears the clipboard now if it still contains the last secret we copied. */
    public synchronized void clearNow() {
        if (pending != null) pending.cancel(false);
        pending = null;
        clearIfOurs();
    }

    private synchronized void clearIfOurs() {
        if (ourHash == null || GraphicsEnvironment.isHeadless()) return;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
                if (cb.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                    Object cur = cb.getData(DataFlavor.stringFlavor);
                    if (cur instanceof String s && MessageDigest.isEqual(sha(s), ourHash)) {
                        cb.setContents(new StringSelection(""), null);
                    }
                }
                ourHash = null;
                return;
            } catch (Exception e) {
                try { Thread.sleep(150); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
            }
        }
    }

    private static byte[] sha(String s) {
        try { return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}

package com.nexvault.web;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Opens NexVault in a dedicated, chromeless Edge/Chrome "app" window using a private browser profile
 * (so none of your everyday browser extensions can read the vault page). Falls back to the default browser.
 */
public final class BrowserLauncher {
    private BrowserLauncher() {}

    public static void open(String url, Path dataDir, boolean appMode) {
        try {
            if (appMode) {
                String exe = findChromium();
                if (exe != null) {
                    Path profile = dataDir.resolve("browser-profile");
                    Files.createDirectories(profile);
                    new ProcessBuilder(exe, "--app=" + url, "--user-data-dir=" + profile, "--no-first-run",
                            "--no-default-browser-check", "--disable-extensions", "--disable-sync",
                            "--window-size=1320,860").inheritIO().start();
                    return;
                }
            }
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception e) {
            System.err.println("Could not open a browser automatically: " + e.getMessage());
        }
        System.out.println("Open this address in your browser:\n  " + url);
    }

    private static String findChromium() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return null;
        List<String> c = new ArrayList<>();
        for (String base : new String[]{System.getenv("ProgramFiles(x86)"), System.getenv("ProgramFiles"), System.getenv("LOCALAPPDATA")}) {
            if (base == null) continue;
            c.add(base + "\\Microsoft\\Edge\\Application\\msedge.exe");
            c.add(base + "\\Google\\Chrome\\Application\\chrome.exe");
            c.add(base + "\\BraveSoftware\\Brave-Browser\\Application\\brave.exe");
        }
        for (String p : c) if (new File(p).isFile()) return p;
        return null;
    }
}

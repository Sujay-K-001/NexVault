package com.nexvault;

import com.nexvault.crypto.CryptoService;
import com.nexvault.db.InMemoryStore;
import com.nexvault.db.SqliteStore;
import com.nexvault.db.VaultStore;
import com.nexvault.util.ClipboardService;
import com.nexvault.logo.DevLogos;
import com.nexvault.logo.LogoService;
import com.nexvault.mail.AlertService;
import com.nexvault.mail.SecretStore;
import com.nexvault.vault.BreachService;
import com.nexvault.vault.ProfileService;
import com.nexvault.vault.VaultService;
import com.nexvault.web.ApiServer;
import com.nexvault.web.BrowserLauncher;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CountDownLatch;

/** NexVault entry point. Options: --data DIR, --port N, --no-browser, --browser-default, --dev-memory-store */
public final class Main {
    public static void main(String[] args) throws Exception {
        String dataArg = System.getenv("NEXVAULT_DATA");
        int port = 0;
        boolean openBrowser = true, appMode = true, memory = false;
        long devLockoutSeconds = 0;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--data" -> dataArg = args[++i];
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--no-browser" -> openBrowser = false;
                case "--browser-default" -> appMode = false;
                case "--dev-memory-store" -> memory = true;
                case "--dev-lockout-seconds" -> devLockoutSeconds = Long.parseLong(args[++i]);
                default -> { System.err.println("Unknown option: " + args[i]); System.exit(64); }
            }
        }
        Path dataDir = Path.of(dataArg == null ? "data" : dataArg).toAbsolutePath();
        Files.createDirectories(dataDir);

        FileChannel lockChannel = FileChannel.open(dataDir.resolve("nexvault.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock = lockChannel.tryLock();
        if (lock == null) {
            System.err.println("NexVault is already running for this data folder (" + dataDir + ").");
            System.err.println("Switch to the existing NexVault window, or close it first.");
            System.exit(2);
        }

        VaultStore store = memory ? new InMemoryStore() : new SqliteStore(dataDir.resolve("nexvault.db"));
        CryptoService crypto = new CryptoService();
        store.putSetting("theme", "light");   // every launch opens in light mode; switching to dark lasts for the session
        VaultService vault = new VaultService(store, crypto);
        ClipboardService clipboard = new ClipboardService();
        if (devLockoutSeconds > 0 && memory) vault.setLockoutMillis(devLockoutSeconds * 1000);
        ProfileService profile = new ProfileService(store);
        AlertService alerts = new AlertService(store, profile, SecretStore.forPlatform(dataDir), AlertService.SMTP, System::currentTimeMillis, true);
        vault.setEvents(alerts);
        LogoService logos = new LogoService(memory ? DevLogos.fetcher() : LogoService.httpFetcher(), dataDir.resolve("logo-cache"));
        vault.setOnLock(clipboard::clearNow);

        CountDownLatch quit = new CountDownLatch(1);
        BreachService breach = new BreachService(memory ? BreachService.devFetcher("letmein", "password123") : BreachService.httpFetcher());
        ApiServer server = new ApiServer(vault, clipboard, crypto, breach, profile, alerts, logos, quit::countDown);
        int actual = server.start(port);
        String url = "http://127.0.0.1:" + actual + "/#t=" + server.token();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            vault.lock(null);        // wipes keys and decrypted metadata
            clipboard.clearNow();
            server.stop();
            store.close();
        }, "nexvault-shutdown"));

        System.out.println("NexVault " + (memory ? "(DEV: in-memory store, nothing is saved) " : "") + "is running.");
        System.out.println("Data folder : " + dataDir);
        System.out.println("NEXVAULT_URL=" + url);
        System.out.println("Leave this window open while you use NexVault. Press Ctrl+C or use Quit in the app to stop.");
        if (openBrowser) BrowserLauncher.open(url, dataDir, appMode);

        quit.await();
        System.out.println("NexVault is shutting down...");
        System.exit(0);
    }
}

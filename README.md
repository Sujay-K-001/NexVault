# NexVault

A local desktop password manager for Windows. Every credential is encrypted with **AES-256-GCM**;
the key is protected by your master password via **PBKDF2WithHmacSHA256** (200,000 iterations, unique
256-bit salt) and stored in a local **SQLite** file. Nothing leaves your machine except the optional,
privacy-preserving breach check (see below).

## Quick start (Windows 10/11)

1. Install a **JDK 21 or newer** (the JDK, not just the JRE): <https://adoptium.net/temurin/releases/?version=21>
   (tick "Add to PATH" / "Set JAVA_HOME").
2. Double-click **`run-vault.bat`**.
3. On the first run it downloads three small libraries into `lib\` (needs internet once), compiles
   the app, and opens NexVault in its own window. Create your master password and you are in.

Keep the console window open while NexVault runs. Use **Quit** in the app (or close the console) to stop.
If something goes wrong the launcher prints what failed and how to fix it.

Launcher options: `run-vault.bat --browser-default` (open your normal browser instead of the
dedicated app window), `run-vault.bat --test` (build and run the automated tests).

## What's new in v5

- **Typography.** Godber for the logo, startup branding, the vault caption and the whole left menu bar; Urbanist for the interface (SemiBold headings, buttons and settings; Medium password cards and form labels; Regular body text); Space Grotesk with tabular figures for security statistics and timestamps. `run-vault.bat` downloads Urbanist and Space Grotesk on first run (SIL OFL). **Godber is not bundled**: put `Godber.woff2` (or `.otf` / `.ttf`; `godber-3lxoz.ttf` is also recognised) in `src\main\resources\web\fonts`. Every family has a fallback stack, so nothing breaks without the files.
- **Startup.** Mouse-responsive particle field (glowing dark-grey motes on three depth layers, parallax, gentle repulsion), ambient light rays and beams, and a Godber reveal with tightening letter-spacing.
- **Runaway Unlock button** on the unlock screen: it dodges the pointer with spring motion and a soft glow until you type your master password. Keyboard, touch and the Reduced/Off animation settings get a normal button.
- **Shortcuts:** `Alt+N` adds an entry, `Alt+L` locks the vault.
- **FAQ & Info** page in the sidebar: searchable FAQ, quick start, shortcuts and security notes.
- **Security alert e-mails** are now multipart (plain text + HTML) with an attempts meter, incident table, actions taken, recommended steps and UTC/local times. They still contain no passwords, vault data, images or links.
- **Dark theme** restyled as pure-black obsidian: smoked translucent glass, silver edge highlights, layered depth and platinum accents. No purple.
- **Fixed:** the show/hide-password eye lost clicks that were not dead-centre (the pressed state moved the button away from the pointer).
- **Fixed:** a grey wash from the top of the Vault page in both themes (the active filter chip's gloss was not clipped to the chip).

## What's new in v4

- **Lockout and alerts:** after more than 3 wrong master-password attempts NexVault e-mails a security alert to the master address; after 5 in a row the vault locks for 1 hour with a live countdown. The counter and the lockout are stored in the database, so restarting the app (or setting the clock back) does not bypass them. Recovery and erase are blocked during a lockout too. Alerts never contain passwords or vault data; the SMTP connection must use TLS (plain text only for localhost) and the SMTP password is protected by Windows DPAPI (an owner-only key file on other systems). Configure it under Settings > Security alert e-mail and use "Send test e-mail".
- **Master profile:** name, e-mail, and a profile picture (upload your own, or pick one of 8 built-in avatars), shown in the sidebar, top bar, sign-in screen and Settings.
- **Website logos:** cards show the site's own icon (fetched privately by NexVault, SSRF-guarded, cached under hashed names, with letter fallbacks). Turn off or clear the cache in Settings > Privacy.
- **Themes:** dark is ~80% black with purple accents; light is charcoal, black and grey skeuomorphism/neumorphism. Teal is gone.
- **Agent Tiny** is now shaded and lit with breathing, blinking and eyes that follow your pointer.
- **Cinematic vault:** 3D door with moving lighting, light rays, sparks and synthesized sound (no audio files). Click or press any key to skip. Settings > Appearance lets you pick Cinematic / Reduced / Off and mute sound. Windows' own "reduce animations" setting no longer hides the animations; use the in-app setting.
- **Startup logo** reveal and a balanced, multi-column **Settings** page that fills wide windows.

## What's new in v3

- **Dark theme redesign:** futuristic skeuomorphism + spatial UI on an obsidian (volcanic-glass) background with purple as the accent:
  beveled keycap buttons, engraved input wells, glowing purple gem buttons, machined score dial, polished obsidian credential cards with
  a purple rim light, and floating rim-lit glass panels. The background and the sign-in card respond to your pointer with a subtle parallax.
- **Always starts in light mode:** every launch of `run-vault.bat` opens in the light (peacock + grey) theme. Switch to dark inside the app
  (sidebar > "Dark theme" or Settings); that choice lasts for the session and resets to light on the next launch.

## What's in v2

- **Themes:** obsidian + purple dark mode; peacock green + light grey light mode. Larger, higher-contrast labels.
- **Security Center:** score, checklist, weak/reused/stale audit, breach monitoring and recovery status in one page.
- **Breach monitoring:** scans your passwords against the Pwned Passwords database using k-anonymity (only the first 5
  characters of a SHA-1 hash are sent; passwords and full hashes never leave your PC). Manual scan or optional weekly auto-check.
- **Forgot master password:** one-time **recovery key** (shown at setup, replaceable in Security Center), optional **hint**, or **erase and start over**.
- **Motion:** animated logo at startup, animated vault door opening/closing on unlock/lock, smooth page and dialog transitions
  (all disabled automatically if Windows is set to reduce motion).
- **Agent Tiny:** a sarcastic chibi agent reacts to wrong passwords, escalating from smug to sunglasses to arms-crossed when you are throttled.
- **Credential cards:** interactive 3D-tilt cards with hover quick-actions and a strength bar instead of plain rows.
- **Collapsible sidebar:** remembered between launches.

## Features

- Master-password setup, unlock and lock; brute-force throttling (delay doubles after 3 failed tries)
- Add, view, edit, delete and search entries (service, username, password, website, notes, category)
- Categories: Social, Email, Finance, Shopping, Work, Entertainment, Development, Other
- Password generator: length 8-128, upper/lower/digits/symbols, optional "avoid look-alike characters",
  uses `SecureRandom` and guarantees at least one character of each selected type
- Strength meter (entropy estimate with common-password, leetspeak, keyboard-pattern and repeat penalties)
- Weak / reused / stale (180+ days) password detection, dashboard with security score, category chart, recent activity
- Show/hide password (auto-hides after 15 s), copy to clipboard with automatic clearing (default 20 s)
- Inactivity auto-lock (default 5 min) with a countdown in the last minute
- Change master password (re-encrypts the whole vault atomically)
- Light/dark themes, responsive layout, confirmation dialogs, empty states, toasts and inline validation

## Architecture

| Layer | Package / files | Responsibility |
|---|---|---|
| Crypto | `crypto/CryptoService` | PBKDF2 key derivation, AES-256-GCM, HMAC. Nothing else touches keys. |
| Storage | `db/VaultStore`, `SqliteStore`, `InMemoryStore`, `resources/schema.sql` | Persists ciphertext only. |
| Business logic | `vault/VaultService`, `PasswordTools` | Unlock/lock, CRUD, audit, scoring, settings, throttling. |
| API | `web/ApiServer` | Loopback-only HTTP server: JSON API + static UI. |
| UI | `resources/web/` (HTML, CSS, vanilla JS) | Liquid Glass + Neumorphism interface. |
| Support | `util/Json`, `ClipboardService`; `web/BrowserLauncher`; `Main` | |

**Why this UI technology?** Liquid Glass relies on `backdrop-filter` blur, layered gradients and smooth
compositing, which Chromium renders far better than Swing or JavaFX CSS can. The app therefore pairs a
Java 21 core with a web front end served from the JDK's built-in HTTP server, so there is no framework
or extra runtime to install. On Windows it opens in a chromeless Edge/Chrome "app" window with its own
private browser profile (`data\browser-profile`), so none of your everyday browser extensions can read
the vault page.

## Security design

- **Key hierarchy:** a random 256-bit data key encrypts the vault. It is stored wrapped (AES-256-GCM) twice: under a key derived from your
  master password (PBKDF2WithHmacSHA256, 200,000 iterations, random 32-byte salt, new salt on every change) and under a key derived from the
  160-bit recovery key. Changing the master password only re-wraps the data key, so the recovery key keeps working.
- **Recovery key:** shown once, never stored in readable form and never returned by any later API call. Anyone holding it can reset the master
  password, so store it offline. Replacing it requires the master password and invalidates the old one.
- **Encryption:** AES-256-GCM with a fresh random 96-bit IV per item. Each ciphertext is bound to its row
  (the entry id is authenticated data), so swapped or tampered rows are detected and skipped.
- **What is on disk:** only ids, timestamps, the KDF parameters/salt, an encrypted verifier, and ciphertext.
  Service names, usernames, URLs, notes, categories and the activity log are all inside the encrypted payload.
  The master password is never stored; unlocking succeeds only if the verifier decrypts.
- **Memory:** the master password is parsed straight into a `char[]` (never a `String`) on the server and zeroed
  after key derivation; the derived key is zeroed on lock. While unlocked the server keeps searchable metadata and a
  keyed HMAC fingerprint of each password (for reuse detection), not the passwords themselves. Passwords are
  decrypted only on demand. Locking wipes the key and metadata and clears any password NexVault put on the clipboard.
- **Local server hardening:** binds to 127.0.0.1 on a random port; every API call needs a random per-launch session
  token; Host and Origin headers are checked (DNS-rebinding and CSRF); JSON-only; strict Content-Security-Policy;
  no caching; the UI uses no inline scripts or styles and never inserts data as HTML.
- **Database:** `secure_delete` is on, so deleted rows are overwritten.
- **Window closed?** If the UI stops talking to the server for 2 minutes the vault locks itself.

### Honest limitations

- The browser UI necessarily holds what you type in JavaScript strings, which cannot be zeroed. The password
  field is cleared after submit, but a determined attacker with access to your running, unlocked session could
  read memory. Lock the vault when you step away.
- Windows Clipboard History (Win+V) or cloud clipboard sync can keep a copy of anything copied. Turn them off in
  Settings > System > Clipboard if that matters to you.
- The strength meter is a transparent heuristic, not a guarantee. Use the generator for important accounts.
- If you lose BOTH the master password and the recovery key, the data cannot be recovered. There is no backdoor. Back up `data\nexvault.db`.
- The password hint is stored unencrypted so it can be shown before unlocking. Never put the password in it.
- Breach monitoring only covers passwords (Pwned Passwords). Checking e-mail addresses against breach lists needs a paid API key and is not included.
- Alert e-mails need a working SMTP account (use an app password). The lockout timer is stored unencrypted in the database settings (it has to work while locked), so someone with file access could edit it, but that does not help them decrypt anything.
- Logos are fetched from each site directly; the site operator sees a request from your IP the first time. Disable in Settings if that matters.
- The sarcastic agent is original artwork; it is a joke, not an affiliate of any agency.
- Dependencies are downloaded from Maven Central over HTTPS and checked against the SHA-1 published beside each file
  (this catches corruption, not a compromised repository). Delete `lib\` to re-fetch.

## Where is my data?

`data\nexvault.db` (plus `data\browser-profile\`, the app window's private profile). Copy `nexvault.db` to back it up.
Override the folder with `run-vault.bat --data D:\Vaults\mine` or the `NEXVAULT_DATA` environment variable.

## Project layout

```
run-vault.bat            one-click launcher (checks JDK, fetches libs, builds, runs)
scripts/fetch.ps1        verified downloader used by the launcher
src/main/java/...        application code
src/main/resources/      schema.sql and the web UI (index.html, app.css, app.js)
src/test/java/...        dependency-free test suite (run-vault.bat --test)
lib/ build/ data/        created at runtime (git-ignored)
```

## Troubleshooting

| Message | Fix |
|---|---|
| "A Java 21 JDK was not found" | Install the JDK (not JRE) 21+, reopen the launcher. |
| "Could not download a required library" | Check internet/proxy, or place the three jars listed in the message into `lib\`. |
| "NexVault is already running" | Only one instance per data folder; switch to the open window. |
| Locked out | 5 consecutive wrong attempts (master password or recovery key) lock the vault for 1 hour. Wait for the countdown; it survives restarts. |
| "Could not reach the breach-check service" | Needs internet for that one feature. Everything else works offline. |
| Window did not open | Copy the `NEXVAULT_URL=` address from the console into a browser, or use `--browser-default`. |
| Clipboard copy says "browser clipboard" | Normal in restricted sessions; the browser copies instead and clears when it can. |

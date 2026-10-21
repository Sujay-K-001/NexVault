# NexVault

### Your passwords. Your device. Your control.

NexVault is a local-first desktop password manager for Windows, built with **Java 21, AES-256-GCM encryption, and SQLite**. It combines privacy-focused credential storage with intelligent security monitoring, password auditing, recovery controls, and a modern, immersive interface.

Your vault stays on your machine. Your master password is never stored, and your credentials are encrypted before being persisted.

## Features

- **Encrypted Vault** — AES-256-GCM authenticated encryption with a random 256-bit data key and unique 96-bit IVs.
- **Master Password Security** — PBKDF2-HMAC-SHA256 with 200,000 iterations and a unique salt.
- **Secure Recovery** — A one-time recovery key lets you regain access if you forget your master password.
- **Password Generator** — Generate strong passwords with configurable length and character sets using `SecureRandom`.
- **Security Center** — Identify weak, reused, and outdated passwords with a security score and actionable insights.
- **Breach Monitoring** — Check passwords against the Pwned Passwords database using k-anonymity.
- **Intrusion Alerts** — Email notifications for repeated failed unlock attempts, with escalating lockout protection.
- **Automatic Locking** — Inactivity timeout, manual locking, clipboard clearing, and persistent lockout tracking.
- **Credential Management** — Create, edit, search, categorize, copy, and delete saved credentials.
- **Modern Interface** — Obsidian dark mode, a refined light theme, translucent surfaces, interactive cards, animated startup effects, and responsive layouts.
- **Privacy-Focused Design** — A loopback-only local server, per-launch session tokens, request validation, and a private browser profile.

## Security at a Glance

| Component | Implementation |
|---|---|
| Credential encryption | AES-256-GCM |
| Key derivation | PBKDF2-HMAC-SHA256 |
| KDF iterations | 200,000 |
| Data encryption key | Random 256-bit key |
| Password recovery | Recovery-key-based key wrapping |
| Local persistence | SQLite |
| Password generation | Java `SecureRandom` |
| Local API | Loopback-only HTTP server |
| Email alerts | SMTP with TLS |
| Breach checks | Pwned Passwords k-anonymity |

NexVault is designed to minimize exposure of sensitive information. However, no software can guarantee protection against every attack, especially malware or an attacker with access to an unlocked device.

## Quick Start

### Requirements

- Windows 10 or Windows 11
- Java Development Kit (JDK) 21 or newer
- Internet access for the initial dependency downloads and optional online features

Download a compatible JDK from [Eclipse Adoptium](https://adoptium.net/temurin/releases/?version=21).

### Run NexVault

1. Clone the repository:

   ```bash
   git clone https://github.com/Sujay-K-001/NexVault.git
   cd NexVault/NexVault
   ```

2. Launch the application:

   ```bat
   run-vault.bat
   ```

3. Create your master password and follow the setup instructions.

On first launch, the launcher downloads the required Java libraries, compiles the application, and opens NexVault in its own app window.

**Useful launcher options**

```bat
run-vault.bat --test
run-vault.bat --browser-default
```

- `--test` builds the application and runs its automated tests.
- `--browser-default` opens the interface in your default browser.

Keep the launcher console open while the application is running.

## How It Works

NexVault separates encryption, storage, business logic, and presentation into distinct layers.

1. **Key management:** A random data key encrypts vault contents. The data key is wrapped using a key derived from the master password and separately using the recovery key.
2. **Encryption:** AES-256-GCM protects credential payloads and authenticates associated entry identifiers.
3. **Storage:** SQLite stores encrypted payloads and the metadata required to unlock and manage the vault.
4. **Application logic:** Java handles authentication, credential operations, password auditing, recovery, and lockout controls.
5. **Presentation:** A local web interface communicates with the Java application through a protected loopback API.

### Technology Stack

- **Core:** Java 21+
- **Frontend:** HTML5, CSS3, Vanilla JavaScript
- **Database:** SQLite
- **Cryptography:** Java Cryptography Architecture
- **Local API:** JDK HTTP server
- **Desktop experience:** Dedicated Chromium-based app window when supported
- **Build and launch:** Windows batch scripts and PowerShell

## Project Structure

```text
NexVault/
├── run-vault.bat
├── scripts/
│   └── fetch.ps1
├── src/
│   ├── main/
│   │   ├── java/com/nexvault/
│   │   │   ├── crypto/
│   │   │   ├── db/
│   │   │   ├── logo/
│   │   │   ├── mail/
│   │   │   ├── util/
│   │   │   ├── vault/
│   │   │   ├── web/
│   │   │   └── Main.java
│   │   └── resources/
│   │       ├── schema.sql
│   │       └── web/
│   └── test/
├── lib/       # Downloaded dependencies
├── build/     # Generated build output
└── data/      # Local vault and browser profile
```

## Privacy and Security Notes

- **Local storage:** Vault data is stored locally in `data/nexvault.db`.
- **Master password:** Never stored as plaintext.
- **Recovery key:** Save it offline in a secure location. Anyone possessing it may be able to recover the vault.
- **Breach monitoring:** Only a partial password hash is sent to the breach-check service; the password and full hash are not sent.
- **Email alerts:** Configure SMTP in Settings → Security alert e-mail. Use an app password where required.
- **Website icons:** Fetching website logos may expose your IP address to the relevant website.
- **Clipboard:** Clipboard history and cloud synchronization may retain copied passwords even after NexVault clears the clipboard.
- **Backups:** Back up `data/nexvault.db` securely. Without both the master password and recovery key, recovery may be impossible.

Optional network features require connectivity. Core vault functionality is designed to run locally.

## Testing

Run the built-in test suite:

```bat
run-vault.bat --test
```

The launcher handles compilation and test execution.

## Current Scope and Limitations

- Windows is the primary supported desktop platform.
- Email-address breach monitoring is not included.
- The password-strength score is a heuristic, not a guarantee of security.
- A compromised device or running, unlocked session can expose sensitive information.
- Dependency checksum verification helps detect corruption but does not guarantee that a dependency source has not been compromised.

## Roadmap

Potential future improvements include:

- Expanded automated security testing
- More comprehensive backup and restore workflows
- Additional platform support
- Further hardening of local API and session handling
- Improved accessibility and interface customization

## License

No license has been specified yet. Until a license is added to the repository, reuse and redistribution permissions should not be assumed.

---

**NexVault** — Private by design. Secure by architecture. Built for control.

Developed by [Sujay K](https://github.com/Sujay-K-001).

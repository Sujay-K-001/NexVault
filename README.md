# 🔐 NexVault
### Your passwords. Your vault. Your control.

<p align="center">
  <strong>A privacy-focused desktop password manager built with Java, AES-256-GCM encryption, and SQLite.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/Security-AES--256--GCM-00A884?style=for-the-badge&logo=letsencrypt&logoColor=white" alt="AES-256-GCM" />
  <img src="https://img.shields.io/badge/Database-SQLite-003B57?style=for-the-badge&logo=sqlite&logoColor=white" alt="SQLite" />
  <img src="https://img.shields.io/badge/Platform-Windows-0078D4?style=for-the-badge&logo=windows&logoColor=white" alt="Windows" />
</p>

<p align="center">
  <a href="https://github.com/Sujay-K-001/NexVault">📦 Repository</a> •
  <a href="https://github.com/Sujay-K-001/NexVault/issues">🐛 Report a Bug</a> •
  <a href="https://github.com/Sujay-K-001/NexVault/issues">💡 Request a Feature</a>
</p>

---

## ✨ About NexVault

**NexVault** is a desktop password manager designed to help users store and manage their credentials through a secure, locally oriented vault experience.

Built with Java, it combines authenticated encryption, password-based key derivation, a SQLite database, and a modern web-based interface.

From protecting saved credentials to detecting suspicious login attempts, NexVault brings essential password-management functionality together in one application.

> 🛡️ **Built around privacy. Designed for control. Made for everyday security.**

## 🚀 Features

| Feature | Description |
|---|---|
| 🔐 Encrypted Vault | Uses AES-256-GCM authenticated encryption to protect stored vault data. |
| 🔑 Master Password | Password-based authentication for accessing the vault. |
| 🧂 Secure Key Derivation | Uses PBKDF2-HMAC-SHA256 with a 16-byte salt and 200,000 iterations. |
| 🗄️ Local Database | Uses SQLite for persistent application data. |
| 🚨 Login Protection | Tracks failed unlock attempts and applies a temporary lockout. |
| 📧 Security Alerts | Email notifications for suspicious or repeated failed login attempts. |
| 🔎 Breach Checking | Includes a password breach-checking feature using a k-anonymity approach. |
| 🎨 Modern Interface | HTML, CSS, and JavaScript power the application's interactive interface. |
| 🪟 Desktop Experience | Java desktop integration provides a convenient way to launch and use the vault. |

*Security features depend on the application's implementation and configuration. Review the source code and setup requirements before relying on NexVault for sensitive credentials.*

## 🧰 Tech Stack

<p>
  <img src="https://img.shields.io/badge/Java-Backend-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java" />
  <img src="https://img.shields.io/badge/HTML5-Interface-E34F26?style=flat-square&logo=html5&logoColor=white" alt="HTML5" />
  <img src="https://img.shields.io/badge/CSS3-Styling-1572B6?style=flat-square&logo=css3&logoColor=white" alt="CSS3" />
  <img src="https://img.shields.io/badge/JavaScript-Interactions-F7DF1E?style=flat-square&logo=javascript&logoColor=black" alt="JavaScript" />
  <img src="https://img.shields.io/badge/SQLite-Storage-003B57?style=flat-square&logo=sqlite&logoColor=white" alt="SQLite" />
</p>

- **Language:** Java 21
- **Interface:** HTML, CSS, JavaScript
- **Database:** SQLite
- **Encryption:** AES-256-GCM
- **Key derivation:** PBKDF2-HMAC-SHA256
- **Desktop integration:** Java with supporting web resources
- **Version control:** Git and GitHub

## 🏗️ How It Works

```text
           👤 User
              │
              ▼
      🔑 Master Password
              │
              ▼
      🛡️ Authentication
              │
              ▼
       🔐 Encrypted Vault
              │
       ┌──────┴──────┐
       ▼             ▼
  🔒 Encryption   🗄️ SQLite
       │             │
       └──────┬──────┘
              ▼
      💻 Desktop Interface
```

NexVault combines authentication, cryptographic protection, persistent storage, and a desktop interface to provide a unified password-management experience.

## ⚙️ Getting Started

### 📋 Prerequisites

Before running NexVault, make sure you have:

- ☕ **Java Development Kit (JDK) 21** installed.
- 🪟 A compatible Windows environment.
- 📦 Git installed to clone the repository.
- 🔧 Any additional dependencies specified by the project.

Check your Java installation:

```powershell
java -version
javac -version
```

### 📥 1. Clone the repository

```powershell
git clone https://github.com/Sujay-K-001/NexVault.git
cd NexVault
```

### 📂 2. Navigate to the application directory

The current repository layout places the application inside a nested `NexVault` directory.

```powershell
cd NexVault
```

### ▶️ 3. Launch NexVault

```powershell
.\run-vault.bat
```

Follow the application's on-screen instructions to configure or unlock your vault.

> 💡 If the application does not start, verify your Java version and review the project's scripts and dependency requirements.

## 📁 Project Structure

```text
NexVault/
├── .gitignore
├── README.md
└── NexVault/
    ├── src/              # Java application source
    ├── lib/              # Project libraries
    ├── scripts/          # Supporting scripts
    ├── web/              # HTML, CSS, JavaScript and assets
    ├── run-vault.bat     # Windows launcher
    └── ...
```

*The tree highlights the main project areas; individual files may vary by version.*

## 🔒 Security & Privacy

Security is central to NexVault's design.

- 🔐 **Authenticated encryption:** AES-GCM provides confidentiality and integrity protection when used correctly.
- 🧂 **Password-based key derivation:** PBKDF2-HMAC-SHA256 derives cryptographic key material from a password.
- 🚫 **Login safeguards:** Failed-attempt tracking and temporary lockouts help discourage repeated guessing.
- 📧 **Security notifications:** Configured email alerts can help users identify repeated failed unlock attempts.
- 🗃️ **Local data storage:** SQLite stores application data locally.

### ⚠️ Important Security Notes

NexVault is a personal software project and should not be assumed to have undergone an independent security audit.

- Use a strong, unique master password.
- Keep backups of important vault data in a secure location.
- Never publish database files, encryption keys, SMTP credentials, application passwords, or other secrets.
- Configure email credentials outside publicly committed source code.
- Understand the recovery and backup limitations before storing critical credentials.
- Do not treat a breach-checking feature as a guarantee that a password is safe.

## 🛣️ Roadmap

Potential future improvements include:

- [ ] 📸 Add verified application screenshots and a demonstration GIF.
- [ ] 🔑 Improve password generation and strength feedback.
- [ ] 🧪 Expand automated tests for authentication and encryption.
- [ ] ♿ Improve accessibility and keyboard navigation.
- [ ] 📦 Streamline application packaging and installation.
- [ ] 🛡️ Conduct an independent security review.

## 🤝 Contributing

Suggestions, bug reports, and improvements are welcome.

1. Fork the repository.
2. Create a feature branch.
3. Make and test your changes.
4. Submit a pull request describing the improvement.

For bugs or feature ideas, visit the [GitHub Issues](https://github.com/Sujay-K-001/NexVault/issues) page.

## 👨‍💻 Author

**Sujay Kumar**

Developer of NexVault — a project exploring desktop application development, password security, encryption, and modern interface design.

- 🐙 GitHub: [@Sujay-K-001](https://github.com/Sujay-K-001)
- 📦 Project: [NexVault](https://github.com/Sujay-K-001/NexVault)

---

<p align="center">
  <strong>🔐 NexVault — Security in your hands.</strong>
  <br/>
  <sub>Built with ☕ Java and a focus on privacy.</sub>
</p>

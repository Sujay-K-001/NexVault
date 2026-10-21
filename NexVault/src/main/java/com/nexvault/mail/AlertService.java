package com.nexvault.mail;

import com.nexvault.db.VaultStore;
import com.nexvault.util.Json;
import com.nexvault.vault.ProfileService;
import com.nexvault.vault.SecurityEvents;
import com.nexvault.vault.VaultException;
import com.nexvault.vault.VaultService;

import java.io.IOException;
import java.net.InetAddress;
import java.time.ZoneId;
import java.util.*;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * Sends security alert e-mails (never containing vault data, passwords or credentials) to the master's e-mail
 * address. SMTP settings live in the settings table; the SMTP password is protected by a {@link SecretStore}.
 */
public final class AlertService implements SecurityEvents {
    public interface Sender {
        void send(SmtpClient.Config c, String to, String subject, String body) throws IOException;

        /** Plain text plus an HTML alternative. Senders that only know plain text simply drop the HTML. */
        default void send(SmtpClient.Config c, String to, String subject, String body, String html) throws IOException { send(c, to, subject, body); }
    }

    /** The production sender: real SMTP, multipart/alternative when HTML is supplied. */
    public static final Sender SMTP = new Sender() {
        @Override public void send(SmtpClient.Config c, String to, String subject, String body) throws IOException { SmtpClient.send(c, to, subject, body); }
        @Override public void send(SmtpClient.Config c, String to, String subject, String body, String html) throws IOException { SmtpClient.send(c, to, subject, body, html); }
    };

    private static final Pattern HOST = Pattern.compile("^(?=.{1,253}$)([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)(\\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$");
    private final VaultStore store;
    private final ProfileService profile;
    private final SecretStore secrets;
    private final Sender sender;
    private final LongSupplier clock;
    private final boolean async;

    public AlertService(VaultStore store, ProfileService profile, SecretStore secrets, Sender sender, LongSupplier clock, boolean async) {
        this.store = store; this.profile = profile; this.secrets = secrets; this.sender = sender; this.clock = clock; this.async = async;
    }

    private String s(String k) { return store.getSetting(k).orElse(""); }

    public Map<String, Object> config() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("host", s("smtpHost"));
        m.put("port", s("smtpPort").isEmpty() ? 587 : Integer.parseInt(s("smtpPort")));
        m.put("security", s("smtpSecurity").isEmpty() ? "STARTTLS" : s("smtpSecurity"));
        m.put("user", s("smtpUser"));
        m.put("from", s("smtpFrom"));
        m.put("passSet", !s("smtpPass").isEmpty());
        m.put("recipient", profile.email());
        m.put("ready", ready());
        m.put("lastAlert", lastAlert());
        return m;
    }

    public boolean ready() { return !s("smtpHost").isEmpty() && !profile.email().isEmpty(); }

    @SuppressWarnings("unchecked")
    public Map<String, Object> lastAlert() {
        String raw = s("lastAlert");
        if (raw.isEmpty()) return null;
        try { return Json.parseObject(raw); } catch (RuntimeException e) { return null; }
    }

    private void record(String status, String detail) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ts", clock.getAsLong()); m.put("status", status); m.put("detail", detail);
        store.putSetting("lastAlert", Json.write(m));
    }

    /** Values are plain strings; "password" is optional (absent = keep, "" with clearPassword=1 = remove). */
    public void update(Map<String, char[]> in) {
        String host = str(in, "host").trim();
        if (!host.isEmpty() && !HOST.matcher(host).matches()) throw new VaultException(400, "Enter a valid SMTP server name.");
        int port;
        try { port = str(in, "port").isEmpty() ? 587 : Integer.parseInt(str(in, "port").trim()); } catch (NumberFormatException e) { throw new VaultException(400, "The port must be a number."); }
        if (port < 1 || port > 65535) throw new VaultException(400, "The port must be between 1 and 65535.");
        SmtpClient.Security sec;
        try { sec = SmtpClient.Security.parse(str(in, "security").isEmpty() ? "STARTTLS" : str(in, "security")); } catch (IllegalArgumentException e) { throw new VaultException(400, e.getMessage()); }
        if (sec == SmtpClient.Security.NONE && !SmtpClient.isLoopbackHost(host.toLowerCase(Locale.ROOT))) throw new VaultException(400, "Unencrypted SMTP is only allowed for localhost. Choose SSL or STARTTLS.");
        String user = str(in, "user").trim();
        if (user.length() > 254 || user.chars().anyMatch(Character::isISOControl)) throw new VaultException(400, "The SMTP user name is invalid.");
        String from = str(in, "from").trim();
        if (!from.isEmpty() && !SmtpClient.EMAIL.matcher(from).matches()) throw new VaultException(400, "Enter a valid From address.");
        store.putSetting("smtpHost", host);
        store.putSetting("smtpPort", Integer.toString(port));
        store.putSetting("smtpSecurity", sec.name());
        store.putSetting("smtpUser", user);
        store.putSetting("smtpFrom", from);
        char[] pw = in.get("password");
        if (pw != null && pw.length > 0) {
            if (pw.length > 512) throw new VaultException(400, "The SMTP password is too long.");
            try { store.putSetting("smtpPass", secrets.protect(pw)); }
            catch (IOException e) { throw new VaultException(500, "The password could not be stored securely on this computer."); }
        } else if (in.containsKey("clearPassword") && "1".equals(str(in, "clearPassword"))) {
            store.putSetting("smtpPass", "");
        }
    }

    private static String str(Map<String, char[]> m, String k) { char[] v = m.get(k); return v == null ? "" : new String(v); }

    private SmtpClient.Config smtp() throws IOException {
        String host = s("smtpHost");
        if (host.isEmpty()) throw new IOException("No SMTP server is configured.");
        String blob = s("smtpPass");
        char[] pw = blob.isEmpty() ? new char[0] : secrets.unprotect(blob);
        String user = s("smtpUser");
        String from = !s("smtpFrom").isEmpty() ? s("smtpFrom") : (SmtpClient.EMAIL.matcher(user).matches() ? user : profile.email());
        SmtpClient.Config c = new SmtpClient.Config(host, Integer.parseInt(s("smtpPort").isEmpty() ? "587" : s("smtpPort")),
                SmtpClient.Security.parse(s("smtpSecurity").isEmpty() ? "STARTTLS" : s("smtpSecurity")), user, new String(pw), from);
        Arrays.fill(pw, '\0');
        return c;
    }

    /** Sends synchronously and returns normally on success; used by the "Send test e-mail" button. */
    public void sendTest() {
        String to = profile.email();
        if (to.isEmpty()) throw new VaultException(400, "Add your e-mail address in your profile first.");
        AlertEmail.Message m = AlertEmail.build(AlertEmail.Kind.TEST, profile.name(), 0, VaultService.MAX_ATTEMPTS, 0, clock.getAsLong(), computerName(), ZoneId.systemDefault());
        try {
            sender.send(smtp(), to, m.subject(), m.text(), m.html());
            record("sent", "Test message delivered");
        } catch (IOException | RuntimeException e) {
            record("failed", reason(e));
            throw new VaultException(502, "The test e-mail could not be sent: " + reason(e));
        }
    }

    private static String computerName() {
        try { return InetAddress.getLocalHost().getHostName(); } catch (Exception e) { return "your computer"; }
    }

    @Override public void failedAttempts(int attempts, boolean lockedOut, long lockoutSeconds) {
        Runnable job = () -> deliver(attempts, lockedOut, lockoutSeconds);
        if (async) { Thread t = new Thread(job, "security-alert"); t.setDaemon(true); t.start(); } else job.run();
    }

    private void deliver(int attempts, boolean lockedOut, long lockoutSeconds) {
        String to = profile.email();
        if (to.isEmpty() || s("smtpHost").isEmpty()) { record("skipped", "No master e-mail or SMTP server configured"); return; }
        AlertEmail.Message m = AlertEmail.build(lockedOut ? AlertEmail.Kind.LOCKOUT : AlertEmail.Kind.FAILED_ATTEMPTS, profile.name(), attempts,
                VaultService.MAX_ATTEMPTS, lockoutSeconds, clock.getAsLong(), computerName(), ZoneId.systemDefault());
        try {
            sender.send(smtp(), to, m.subject(), m.text(), m.html());
            record("sent", lockedOut ? "Lockout alert delivered" : "Failed-attempts alert delivered");
        } catch (IOException | RuntimeException e) {
            record("failed", reason(e));
        }
    }

    /** A short, credential-free explanation. */
    static String reason(Exception e) {
        String m = e.getMessage() == null ? "" : e.getMessage();
        if (m.contains("535") || m.toLowerCase(Locale.ROOT).contains("authentication")) return "The mail server rejected the user name or password";
        if (e instanceof java.net.UnknownHostException) return "The mail server name could not be found";
        if (e instanceof java.net.ConnectException || e instanceof java.net.SocketTimeoutException) return "Could not connect to the mail server";
        if (e instanceof javax.net.ssl.SSLException) return "The mail server's secure connection could not be verified";
        if (m.startsWith("Mail server replied")) return m.length() > 120 ? m.substring(0, 120) : m;
        if (m.contains("STARTTLS") || m.contains("Unencrypted") || m.contains("No SMTP") || m.contains("address")) return m;
        return "The mail server could not be reached";
    }
}

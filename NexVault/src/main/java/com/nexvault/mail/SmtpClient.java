package com.nexvault.mail;

import com.nexvault.crypto.CryptoService;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Minimal SMTP submission client. TLS is mandatory (implicit SSL or STARTTLS, certificates and host names are
 * verified); plain text is only accepted for loopback hosts. Credentials are never logged or put in messages.
 */
public final class SmtpClient {
    private SmtpClient() {}

    public enum Security {
        SSL, STARTTLS, NONE;
        public static Security parse(String s) {
            try { return valueOf(s.trim().toUpperCase(Locale.ROOT)); } catch (RuntimeException e) { throw new IllegalArgumentException("Security must be SSL, STARTTLS or NONE."); }
        }
    }

    public record Config(String host, int port, Security security, String user, String password, String from) {}

    private static final int TIMEOUT_MS = 10_000;
    public static final Pattern EMAIL = Pattern.compile("^[^\\s@<>\"'\\r\\n]{1,64}@[A-Za-z0-9.-]{1,190}\\.[A-Za-z]{2,24}$");

    public static boolean isLoopbackHost(String h) {
        return h.equals("localhost") || h.equals("127.0.0.1") || h.equals("::1");
    }

    /** Plain-text only message. */
    public static void send(Config c, String to, String subject, String body) throws IOException { send(c, to, subject, body, null); }

    /** Sends a multipart/alternative message (plain text plus HTML) when {@code html} is non-null. */
    public static void send(Config c, String to, String subject, String body, String html) throws IOException {
        if (!EMAIL.matcher(to).matches() || !EMAIL.matcher(c.from()).matches()) throw new IOException("Invalid e-mail address.");
        if (subject.indexOf('\r') >= 0 || subject.indexOf('\n') >= 0) throw new IOException("Invalid subject.");
        if (c.security() == Security.NONE && !isLoopbackHost(c.host())) throw new IOException("Unencrypted SMTP is only allowed for localhost.");
        Socket sock = new Socket();
        try {
            sock.connect(new InetSocketAddress(InetAddress.getByName(c.host()), c.port()), TIMEOUT_MS);
            sock.setSoTimeout(TIMEOUT_MS);
            if (c.security() == Security.SSL) sock = tls(sock, c.host(), c.port());
            Conn conn = new Conn(sock);
            conn.expect(conn.read(), 220);
            Reply ehlo = conn.cmd("EHLO nexvault.local", 250);
            if (c.security() == Security.STARTTLS) {
                if (!ehlo.has("STARTTLS")) throw new IOException("The server does not offer STARTTLS.");
                conn.cmd("STARTTLS", 220);
                sock = tls(sock, c.host(), c.port());
                conn = new Conn(sock);
                ehlo = conn.cmd("EHLO nexvault.local", 250);
            }
            if (c.user() != null && !c.user().isEmpty()) {
                byte[] cred = ("\0" + c.user() + "\0" + c.password()).getBytes(StandardCharsets.UTF_8);
                try {
                    if (ehlo.has("AUTH") && ehlo.text.toUpperCase(Locale.ROOT).contains("PLAIN")) {
                        conn.cmd("AUTH PLAIN " + Base64.getEncoder().encodeToString(cred), 235);
                    } else {
                        conn.cmd("AUTH LOGIN", 334);
                        conn.cmd(Base64.getEncoder().encodeToString(c.user().getBytes(StandardCharsets.UTF_8)), 334);
                        conn.cmd(Base64.getEncoder().encodeToString(c.password().getBytes(StandardCharsets.UTF_8)), 235);
                    }
                } finally { CryptoService.wipe(cred); }
            }
            conn.cmd("MAIL FROM:<" + c.from() + ">", 250);
            conn.cmd("RCPT TO:<" + to + ">", 250, 251);
            conn.cmd("DATA", 354);
            conn.raw(message(c.from(), to, subject, body, html) + "\r\n.\r\n");
            conn.expect(conn.read(), 250);
            try { conn.cmd("QUIT", 221); } catch (IOException ignored) { }
        } finally {
            try { sock.close(); } catch (IOException ignored) { }
        }
    }

    private static Socket tls(Socket base, String host, int port) throws IOException {
        SSLSocket s = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(base, host, port, true);
        SSLParameters p = s.getSSLParameters();
        p.setEndpointIdentificationAlgorithm("HTTPS");
        p.setProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
        s.setSSLParameters(p);
        s.startHandshake();
        return s;
    }

    static String message(String from, String to, String subject, String body) { return message(from, to, subject, body, null); }

    static String message(String from, String to, String subject, String body, String html) {
        String domain = from.substring(from.indexOf('@') + 1);
        String head = "From: NexVault <" + from + ">\r\nTo: <" + to + ">\r\nSubject: " + subject + "\r\n"
                + "Date: " + DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC)) + "\r\n"
                + "Message-ID: <" + UUID.randomUUID() + "@" + domain + ">\r\nMIME-Version: 1.0\r\n"
                + "Auto-Submitted: auto-generated\r\nX-Auto-Response-Suppress: All\r\nImportance: high\r\n";
        if (html == null) {
            return head + "Content-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n" + b64(body);
        }
        String boundary = "=_nexvault_" + UUID.randomUUID().toString().replace("-", "");
        return head + "Content-Type: multipart/alternative; boundary=\"" + boundary + "\"\r\n\r\n"
                + "This is a multi-part message in MIME format.\r\n"
                + "--" + boundary + "\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n" + b64(body) + "\r\n"
                + "--" + boundary + "\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n" + b64(html) + "\r\n"
                + "--" + boundary + "--";
    }

    private static String b64(String s) {
        return Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private record Reply(int code, String text) {
        boolean has(String cap) { return text.toUpperCase(Locale.ROOT).contains(cap); }
    }

    private static final class Conn {
        private final BufferedReader in;
        private final OutputStream out;
        Conn(Socket s) throws IOException {
            in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.ISO_8859_1));
            out = s.getOutputStream();
        }
        Reply read() throws IOException {
            StringBuilder sb = new StringBuilder();
            while (true) {
                String line = in.readLine();
                if (line == null || line.length() < 3) throw new IOException("The mail server closed the connection.");
                sb.append(line).append('\n');
                if (line.length() == 3 || line.charAt(3) != '-') {
                    try { return new Reply(Integer.parseInt(line.substring(0, 3)), sb.toString()); }
                    catch (NumberFormatException e) { throw new IOException("Unexpected reply from the mail server."); }
                }
            }
        }
        void raw(String s) throws IOException { out.write(s.getBytes(StandardCharsets.ISO_8859_1)); out.flush(); }
        Reply cmd(String c, int... ok) throws IOException { raw(c + "\r\n"); Reply r = read(); expect(r, ok); return r; }
        void expect(Reply r, int... ok) throws IOException {
            for (int c : ok) if (r.code == c) return;
            String t = r.text.replace('\n', ' ').trim();
            throw new IOException("Mail server replied " + (t.length() > 110 ? t.substring(0, 110) : t));
        }
    }
}

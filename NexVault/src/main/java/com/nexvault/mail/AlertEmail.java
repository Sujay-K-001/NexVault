package com.nexvault.mail;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the security alert e-mails (plain text plus a self-contained HTML alternative).
 *
 * <p>Nothing in here ever receives a password, a vault entry or any key material: the only inputs are counters,
 * a timestamp, the computer name and the recipient's display name. The HTML uses inline styles only, no images,
 * no links and no remote content, so mail clients have nothing to fetch and nothing can be tracked.
 */
final class AlertEmail {
    private AlertEmail() {}

    enum Kind { FAILED_ATTEMPTS, LOCKOUT, TEST }

    /** A finished message. {@code subject} is pure ASCII (it is written raw into the mail header). */
    record Message(String subject, String text, String html) {}

    private static final DateTimeFormatter UTC_FMT = DateTimeFormatter.ofPattern("EEE, d MMM uuuu HH:mm:ss 'UTC'", Locale.ENGLISH);
    private static final DateTimeFormatter LOCAL_FMT = DateTimeFormatter.ofPattern("EEE, d MMM uuuu HH:mm:ss", Locale.ENGLISH);

    static Message build(Kind kind, String recipientName, int attempts, int maxAttempts, long lockoutSeconds,
                         long nowMillis, String computer, ZoneId zone) {
        Instant now = Instant.ofEpochMilli(nowMillis);
        ZonedDateTime utc = now.atZone(ZoneOffset.UTC);
        ZonedDateTime local = now.atZone(zone);
        String utcText = UTC_FMT.format(utc);
        String localText = LOCAL_FMT.format(local) + " " + zoneLabel(local);
        String ref = "NV-" + Long.toString(nowMillis / 1000, 36).toUpperCase(Locale.ROOT);
        String until = lockoutSeconds > 0
                ? LOCAL_FMT.format(now.plusSeconds(lockoutSeconds).atZone(zone)) + " " + zoneLabel(local) : "";
        String duration = duration(lockoutSeconds);
        String name = recipientName == null ? "" : recipientName.trim();
        String hello = name.isEmpty() ? "Hello," : "Hello " + name + ",";
        String host = computer == null || computer.isBlank() ? "your computer" : computer;

        String subject, headline, tone, summary, status, preheader;
        List<String[]> facts = new ArrayList<>();
        List<String> did = new ArrayList<>();
        List<String> todo = new ArrayList<>();
        String ifYou, ifNot;

        switch (kind) {
            case LOCKOUT -> {
                subject = "NexVault security alert: vault locked for " + duration + " after " + attempts + " failed unlock attempts";
                headline = "Your vault has been locked";
                tone = "critical";
                preheader = "Vault locked for " + duration + " after " + attempts + " incorrect master-password attempts.";
                summary = attempts + " consecutive incorrect master-password attempts were made on " + host + ". "
                        + "To protect your data, NexVault has locked the vault for " + duration + ". Nobody, including you, can open it until the timer ends.";
                status = "LOCKED for " + duration + (until.isEmpty() ? "" : " (until about " + until + ")");
                facts.add(new String[]{"Event", "Vault locked after repeated failed unlock attempts"});
                facts.add(new String[]{"Failed attempts", attempts + " consecutive (limit: " + maxAttempts + ")"});
                facts.add(new String[]{"Vault status", status});
                did.add("Locked the vault for " + duration + ". Unlocking, recovery-key reset and \"erase and start over\" are all blocked during this time.");
                did.add("Saved the lock timer in the database, so restarting NexVault or changing the system clock does not bypass it.");
                did.add("Recorded the event. Your encrypted data was not touched and no data left your computer.");
                ifYou = "For example, you may have mistyped your master password. No action is needed: wait for the timer to end, then unlock normally. A successful unlock resets the counter.";
                ifNot = "Someone with access to " + host + " may be trying to open your vault. The lock gives you time to act.";
            }
            case TEST -> {
                subject = "NexVault test message: security alerts are working";
                headline = "Security alerts are working";
                tone = "ok";
                preheader = "This is a test. No action is needed.";
                summary = "This is a test message sent from NexVault on " + host + ". If you can read it, security alerts will reach this address.";
                status = "No incident. This is only a test.";
                facts.add(new String[]{"Event", "Test message requested from Settings"});
                facts.add(new String[]{"Status", status});
                did.add("Connected to your mail server over an encrypted (TLS) connection and delivered this message.");
                ifYou = "You do not need to do anything. NexVault will use these settings to warn you about repeated incorrect master-password attempts and vault lockouts.";
                ifNot = "";
            }
            default -> {
                int left = Math.max(0, maxAttempts - attempts);
                subject = "NexVault security alert: " + attempts + " incorrect master-password attempts detected";
                headline = "Repeated incorrect master-password attempts";
                tone = "warning";
                preheader = attempts + " wrong attempts in a row. " + left + " more will lock the vault for " + duration(3600) + ".";
                summary = "An incorrect master password has been entered " + attempts + " times in a row on " + host + ". "
                        + "The vault is still available, but " + left + " more failed attempt" + (left == 1 ? "" : "s")
                        + " will lock it for " + duration(3600) + ".";
                status = "Unlocked and available. " + left + " attempt" + (left == 1 ? "" : "s") + " remaining before a lockout";
                facts.add(new String[]{"Event", "Repeated incorrect master-password attempts"});
                facts.add(new String[]{"Failed attempts", attempts + " consecutive (limit: " + maxAttempts + ")"});
                facts.add(new String[]{"Vault status", status});
                did.add("Counted and stored the failed attempts, so the count survives a restart of NexVault.");
                did.add("Sent this alert. After " + maxAttempts + " consecutive failures NexVault locks the vault for " + duration(3600) + " automatically.");
                did.add("Recorded the event. Your encrypted data was not touched and no data left your computer.");
                ifYou = "No action is needed. Enter the correct master password to reset the counter, or use your recovery key if you have forgotten it.";
                ifNot = "Someone with access to " + host + " may be guessing your master password.";
            }
        }
        facts.add(new String[]{"Detected (UTC)", utcText});
        facts.add(new String[]{"Detected (local)", localText});
        facts.add(new String[]{"Computer", host});
        facts.add(new String[]{"Reference", ref});

        if (kind != Kind.TEST) {
            todo.add("Check who can use " + host + " right now, and lock the Windows session if you are away from it.");
            todo.add("When you next unlock successfully, change your master password (Settings > Change master password) and choose a long, unique one.");
            todo.add("Create a fresh recovery key (Security Center > Account recovery) so any copy of the old one stops working.");
            todo.add("Run a breach scan in Security Center and review Recent activity on the Dashboard.");
            todo.add("Change the passwords of your most sensitive accounts if you suspect someone had access to this computer.");
        }
        String footer = "This message never contains your passwords or any vault data. NexVault sent it from your own computer using the mail account you set up under Settings > Security alert e-mail. "
                + "It contains no links or images and cannot be replied to. You can change or remove this address at any time in Settings.";

        String text = text(headline, hello, summary, facts, did, todo, ifYou, ifNot, footer, kind, status);
        String html = html(kind, headline, tone, preheader, hello, summary, facts, did, todo, ifYou, ifNot, footer, attempts, maxAttempts, host);
        return new Message(subject, text, html);
    }

    // ------------------------------------------------------------------------------------------ plain text

    private static String text(String headline, String hello, String summary, List<String[]> facts, List<String> did, List<String> todo,
                               String ifYou, String ifNot, String footer, Kind kind, String status) {
        StringBuilder b = new StringBuilder();
        String bar = "=".repeat(62), thin = "-".repeat(62);
        b.append(bar).append("\n").append(kind == Kind.TEST ? "NEXVAULT  |  TEST MESSAGE" : "NEXVAULT  |  SECURITY ALERT").append("\n").append(bar).append("\n\n");
        b.append(headline.toUpperCase(Locale.ROOT)).append("\n\n").append(hello).append("\n\n").append(wrap(summary, 70)).append("\n\n");
        b.append(kind == Kind.TEST ? "DETAILS\n" : "INCIDENT DETAILS\n").append(thin).append("\n");
        for (String[] f : facts) b.append(String.format("%-18s %s%n", f[0] + ":", f[1]));
        b.append("\nWHAT NEXVAULT HAS DONE\n").append(thin).append("\n");
        for (String s : did) b.append(" * ").append(wrap(s, 66).replace("\n", "\n   ")).append("\n");
        b.append("\nWHAT TO DO NEXT\n").append(thin).append("\n");
        b.append(wrap((kind == Kind.TEST ? "" : "If this was you. ") + ifYou, 70)).append("\n");
        if (!ifNot.isEmpty()) {
            b.append("\n").append(wrap("If this was not you. " + ifNot + " Recommended steps:", 70)).append("\n");
            int i = 1;
            for (String s : todo) b.append(" ").append(i++).append(". ").append(wrap(s, 66).replace("\n", "\n    ")).append("\n");
        }
        b.append("\n").append(thin).append("\n").append(wrap(footer, 70)).append("\n");
        return b.toString();
    }

    private static String wrap(String s, int width) {
        StringBuilder out = new StringBuilder(), line = new StringBuilder();
        for (String w : s.split(" ")) {
            if (line.length() + w.length() + 1 > width && line.length() > 0) { out.append(line).append("\n"); line.setLength(0); }
            if (line.length() > 0) line.append(' ');
            line.append(w);
        }
        return out.append(line).toString();
    }

    // ----------------------------------------------------------------------------------------------- HTML

    private static String esc(String s) {
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&#39;");
                default -> b.append(c);
            }
        }
        return b.toString();
    }

    private static String html(Kind kind, String headline, String tone, String preheader, String hello, String summary, List<String[]> facts,
                               List<String> did, List<String> todo, String ifYou, String ifNot, String footer, int attempts, int maxAttempts, String host) {
        String accent, accentBg, accentInk, label;
        switch (tone) {
            case "critical" -> { accent = "#c0364f"; accentBg = "#fbeaee"; accentInk = "#7d1f31"; label = "CRITICAL"; }
            case "ok" -> { accent = "#2b8a5a"; accentBg = "#e8f5ee"; accentInk = "#1c5a3a"; label = "TEST"; }
            default -> { accent = "#c98a12"; accentBg = "#fdf3dc"; accentInk = "#7a5208"; label = "WARNING"; }
        }
        final String font = "font-family:'Segoe UI',-apple-system,BlinkMacSystemFont,Helvetica,Arial,sans-serif;";
        StringBuilder b = new StringBuilder(6000);
        b.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<meta name=\"color-scheme\" content=\"light\"><title>").append(esc(headline)).append("</title></head>")
                .append("<body style=\"margin:0;padding:0;background:#eceef1;").append(font).append("\">")
                .append("<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;color:#eceef1;font-size:1px;line-height:1px;\">").append(esc(preheader)).append("</div>")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#eceef1;padding:28px 12px;\"><tr><td align=\"center\">")
                .append("<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" style=\"width:100%;max-width:600px;background:#ffffff;border-radius:16px;overflow:hidden;border:1px solid #d9dce2;\">");

        // header band
        b.append("<tr><td style=\"background:#0d0e10;padding:22px 30px;\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr>")
                .append("<td style=\"").append(font).append("font-size:21px;font-weight:700;letter-spacing:.06em;color:#f4f5f7;\">NexVault</td>")
                .append("<td align=\"right\"><span style=\"").append(font).append("display:inline-block;padding:5px 12px;border-radius:999px;background:").append(accent)
                .append(";color:#ffffff;font-size:11px;font-weight:700;letter-spacing:.12em;\">").append(label).append("</span></td></tr></table></td></tr>");

        // headline
        b.append("<tr><td style=\"padding:30px 30px 8px 30px;\"><div style=\"").append(font).append("font-size:12px;font-weight:700;letter-spacing:.14em;color:").append(accent).append(";\">")
                .append(kind == Kind.TEST ? "DELIVERY TEST" : "SECURITY ALERT").append("</div>")
                .append("<div style=\"").append(font).append("font-size:26px;line-height:1.25;font-weight:700;color:#14161a;margin-top:6px;\">").append(esc(headline)).append("</div></td></tr>");

        // greeting + summary
        b.append("<tr><td style=\"padding:10px 30px 4px 30px;").append(font).append("font-size:15px;line-height:1.6;color:#2b2f36;\">")
                .append("<p style=\"margin:0 0 10px 0;\">").append(esc(hello)).append("</p><p style=\"margin:0;\">").append(esc(summary)).append("</p></td></tr>");

        // attempts meter
        if (kind != Kind.TEST) {
            b.append("<tr><td style=\"padding:18px 30px 0 30px;\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:").append(accentBg)
                    .append(";border-radius:12px;\"><tr><td style=\"padding:16px 18px;\"><div style=\"").append(font).append("font-size:12px;font-weight:700;letter-spacing:.1em;color:")
                    .append(accentInk).append(";\">FAILED ATTEMPTS: ").append(attempts).append(" OF ").append(maxAttempts).append("</div>")
                    .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-top:10px;\"><tr>");
            for (int i = 1; i <= maxAttempts; i++) {
                b.append("<td style=\"height:8px;border-radius:6px;background:").append(i <= attempts ? accent : "#e1d9c8").append(";font-size:0;line-height:0;\">&nbsp;</td>");
                if (i < maxAttempts) b.append("<td width=\"6\" style=\"font-size:0;line-height:0;\">&nbsp;</td>");
            }
            b.append("</tr></table></td></tr></table></td></tr>");
        }

        // details table
        b.append(section(kind == Kind.TEST ? "Details" : "Incident details", font)).append("<tr><td style=\"padding:0 30px;\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"border:1px solid #e3e5ea;border-radius:12px;border-collapse:separate;\">");
        for (int i = 0; i < facts.size(); i++) {
            String top = i == 0 ? "" : "border-top:1px solid #e9ebef;";
            b.append("<tr><td width=\"36%\" valign=\"top\" style=\"padding:11px 14px;").append(top).append(font).append("font-size:12.5px;font-weight:600;color:#5b616c;background:#f7f8fa;\">")
                    .append(esc(facts.get(i)[0])).append("</td><td valign=\"top\" style=\"padding:11px 14px;").append(top).append(font).append("font-size:14px;color:#14161a;\">")
                    .append(esc(facts.get(i)[1])).append("</td></tr>");
        }
        b.append("</table></td></tr>");

        // what NexVault did
        b.append(section("What NexVault has done", font)).append("<tr><td style=\"padding:0 30px;\">").append(list(did, "#2b2f36", accent, font)).append("</td></tr>");

        // what to do
        b.append(section("What to do next", font)).append("<tr><td style=\"padding:0 30px;").append(font).append("font-size:14.5px;line-height:1.6;color:#2b2f36;\">")
                .append("<p style=\"margin:0 0 10px 0;\">").append(kind == Kind.TEST ? "" : "<b style=\"color:#14161a;\">If this was you.</b> ").append(esc(ifYou)).append("</p>");
        if (!ifNot.isEmpty()) {
            b.append("<p style=\"margin:0 0 10px 0;\"><b style=\"color:#14161a;\">If this was not you.</b> ").append(esc(ifNot)).append(" Recommended steps:</p>")
                    .append(numbered(todo, font));
        }
        b.append("</td></tr>");

        // footer
        b.append("<tr><td style=\"padding:26px 30px 28px 30px;\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"border-top:1px solid #e3e5ea;\"><tr><td style=\"padding-top:16px;")
                .append(font).append("font-size:12px;line-height:1.6;color:#6b717c;\">").append(esc(footer)).append("</td></tr></table></td></tr>")
                .append("</table>")
                .append("<div style=\"").append(font).append("font-size:11.5px;color:#8a909b;padding-top:14px;\">NexVault &middot; local password vault &middot; sent from ").append(esc(host)).append("</div>")
                .append("</td></tr></table></body></html>");
        return b.toString();
    }

    private static String section(String title, String font) {
        return "<tr><td style=\"padding:26px 30px 10px 30px;" + font + "font-size:13px;font-weight:700;letter-spacing:.1em;color:#14161a;\">" + esc(title.toUpperCase(Locale.ROOT)) + "</td></tr>";
    }

    private static String list(List<String> items, String ink, String accent, String font) {
        StringBuilder b = new StringBuilder("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">");
        for (String s : items) {
            b.append("<tr><td width=\"22\" valign=\"top\" style=\"padding:5px 0;font-size:14px;color:").append(accent).append(";\">&#9679;</td><td style=\"padding:3px 0 7px 0;")
                    .append(font).append("font-size:14.5px;line-height:1.55;color:").append(ink).append(";\">").append(esc(s)).append("</td></tr>");
        }
        return b.append("</table>").toString();
    }

    private static String numbered(List<String> items, String font) {
        StringBuilder b = new StringBuilder("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">");
        int i = 1;
        for (String s : items) {
            b.append("<tr><td width=\"34\" valign=\"top\" style=\"padding:4px 0 8px 0;\"><div style=\"width:24px;height:24px;line-height:24px;border-radius:12px;background:#14161a;color:#ffffff;text-align:center;")
                    .append(font).append("font-size:12px;font-weight:700;\">").append(i++).append("</div></td><td style=\"padding:3px 0 8px 0;").append(font)
                    .append("font-size:14.5px;line-height:1.55;color:#2b2f36;\">").append(esc(s)).append("</td></tr>");
        }
        return b.append("</table>").toString();
    }

    // ---------------------------------------------------------------------------------------------- helpers

    static String duration(long seconds) {
        if (seconds >= 3600 && seconds % 3600 == 0) { long h = seconds / 3600; return h + (h == 1 ? " hour" : " hours"); }
        if (seconds >= 60) { long m = Math.round(seconds / 60.0); return m + (m == 1 ? " minute" : " minutes"); }
        return Math.max(1, seconds) + (seconds == 1 ? " second" : " seconds");
    }

    private static String zoneLabel(ZonedDateTime t) {
        String off = t.getOffset().getId().equals("Z") ? "+00:00" : t.getOffset().getId();
        String id = t.getZone().getId();
        return (id.equals("Z") || id.startsWith("+") || id.startsWith("-") ? "" : id + " ") + "(UTC" + off + ")";
    }
}

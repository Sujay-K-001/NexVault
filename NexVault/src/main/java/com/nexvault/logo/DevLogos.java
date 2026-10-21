package com.nexvault.logo;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Set;

/** Offline stand-in used by --dev-memory-store and tests: serves generated icons for a few well-known demo domains. */
public final class DevLogos {
    private DevLogos() {}

    public static LogoService.Fetcher fetcher() {
        Set<String> known = Set.of("github.com", "example.com", "bank.com", "netflix.com");
        return (url, max) -> {
            URI u = URI.create(url);
            String host = u.getHost().replaceFirst("^www\\.", "");
            if (u.getPath().equals("/apple-touch-icon.png") && known.contains(host)) return new LogoService.Fetched(200, "image/png", icon(host));
            return new LogoService.Fetched(404, "", null);
        };
    }

    static byte[] icon(String host) throws IOException {
        BufferedImage img = new BufferedImage(180, 180, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int h = Math.abs(host.hashCode()) % 360;
        g.setPaint(new GradientPaint(0, 0, Color.getHSBColor(h / 360f, .7f, .95f), 180, 180, Color.getHSBColor(((h + 40) % 360) / 360f, .8f, .55f)));
        g.fillRoundRect(0, 0, 180, 180, 40, 40);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 110));
        String l = host.substring(0, 1).toUpperCase();
        FontMetrics fm = g.getFontMetrics();
        g.drawString(l, (180 - fm.stringWidth(l)) / 2, (180 - fm.getHeight()) / 2 + fm.getAscent());
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}

package eu.amadeos.apps.news.ui.common;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Country flags from {@code flags/<code>.png} (48 × 36, made from the MIT-licensed flag-icons), scaled down with
 * good quality and kept.
 */
public final class Flags {
    private static final Map<String, Optional<Icon>> CACHE = new ConcurrentHashMap<>();

    private Flags() {
    }

    /** The flag at the height in pixels, 4:3; empty for an unknown or empty code. */
    public static Optional<Icon> icon(String code, int height) {
        if (code == null || code.isBlank()) return Optional.empty();
        return CACHE.computeIfAbsent(code + "@" + height, k -> load(code, height));
    }

    /** The address of the flag image, for the reading pane's HTML. */
    public static Optional<URL> url(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        return Optional.ofNullable(Flags.class.getResource("/eu/amadeos/apps/news/flags/" + code + ".png"));
    }

    private static Optional<Icon> load(String code, int height) {
        Optional<URL> url = url(code);
        if (url.isEmpty()) return Optional.empty();
        try (InputStream in = url.get().openStream()) {
            BufferedImage source = ImageIO.read(in);
            int width = Math.round(height * 4f / 3f);
            BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, width, height, null);
            g.setColor(new java.awt.Color(0, 0, 0, 70));
            g.drawRect(0, 0, width - 1, height - 1);
            g.dispose();
            return Optional.of(new ImageIcon(scaled));
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}

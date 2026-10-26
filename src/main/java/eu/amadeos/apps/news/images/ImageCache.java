package eu.amadeos.apps.news.images;

import eu.amadeos.apps.news.net.Http;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * The articles' pictures: downloaded once into {@code ~/.cache/amadeos/news/images/}, kept there for 30 days, and
 * held in memory scaled to the sizes the window asks for. Loading happens in the background; the window gets a
 * call on the EDT when a picture is there.
 */
public final class ImageCache {
    private static final Logger LOG = LoggerFactory.getLogger(ImageCache.class);
    static final int MAX_BYTES = 8 * 1024 * 1024;
    static final Duration KEEP = Duration.ofDays(30);
    private static final int MEMORY = 300;

    private final Http http;
    private final Path dir;
    private final Executor background;
    private final Executor ui;
    private final Clock clock;
    private final Map<String, BufferedImage> scaled = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
            return size() > MEMORY;
        }
    };
    private final Set<String> loading = ConcurrentHashMap.newKeySet();
    private final Set<String> failed = ConcurrentHashMap.newKeySet();

    public ImageCache(Http http, Path dir, Executor background, Executor ui, Clock clock) {
        this.http = http;
        this.dir = dir;
        this.background = background;
        this.ui = ui;
        this.clock = clock;
    }

    /**
     * The picture scaled to fit the box, if it is already there. Otherwise it is fetched in the background and
     * {@code ready} runs on the EDT once it can be asked for again.
     */
    public Optional<BufferedImage> get(String url, int maxWidth, int maxHeight, boolean crop, Runnable ready) {
        if (url == null || url.isBlank() || failed.contains(url)) return Optional.empty();
        String key = url + "@" + maxWidth + "x" + maxHeight + (crop ? "c" : "");
        synchronized (scaled) {
            BufferedImage hit = scaled.get(key);
            if (hit != null) return Optional.of(hit);
        }
        if (loading.add(key)) {
            background.execute(() -> {
                try {
                    BufferedImage original = original(url);
                    if (original == null) {
                        failed.add(url);
                        return;
                    }
                    BufferedImage fitted = crop ? cover(original, maxWidth, maxHeight) : fit(original, maxWidth, maxHeight);
                    synchronized (scaled) {
                        scaled.put(key, fitted);
                    }
                    ui.execute(ready);
                } finally {
                    loading.remove(key);
                }
            });
        }
        return Optional.empty();
    }

    /** The cached file of the picture, if it was downloaded; for the reading pane's HTML. */
    public Optional<Path> file(String url) {
        Path f = fileOf(url);
        return Files.exists(f) ? Optional.of(f) : Optional.empty();
    }

    private BufferedImage original(String url) {
        Path file = fileOf(url);
        try {
            byte[] bytes;
            if (Files.exists(file)) {
                bytes = Files.readAllBytes(file);
            } else {
                Http.Response r = http.get(URI.create(url), Map.of("Accept", "image/*"));
                if (r.status() != 200 || r.body().length == 0 || r.body().length > MAX_BYTES) return null;
                bytes = r.body();
                Files.createDirectories(dir);
                Path temp = dir.resolve(file.getFileName() + ".tmp");
                Files.write(temp, bytes);
                Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) LOG.debug("Not a picture Java can read: {}", url);
            return image;
        } catch (IOException | IllegalArgumentException e) {
            LOG.debug("Cannot load {}: {}", url, e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    Path fileOf(String url) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(url.getBytes(StandardCharsets.UTF_8));
            return dir.resolve(HexFormat.of().formatHex(hash));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Deletes pictures not used for {@link #KEEP}; run once at start. */
    public void cleanUp() {
        background.execute(() -> {
            if (!Files.isDirectory(dir)) return;
            Instant limit = clock.instant().minus(KEEP);
            List<Path> old = new ArrayList<>();
            try (Stream<Path> files = Files.list(dir)) {
                files.forEach(f -> {
                    try {
                        if (Files.getLastModifiedTime(f).toInstant().isBefore(limit)) old.add(f);
                    } catch (IOException e) {
                        // gone already
                    }
                });
                for (Path f : old) Files.deleteIfExists(f);
            } catch (IOException e) {
                LOG.debug("Cleaning the picture cache: {}", e.getMessage());
            }
        });
    }

    /** Scaled down to fit inside the box, never up. */
    static BufferedImage fit(BufferedImage image, int maxWidth, int maxHeight) {
        double scale = Math.min(1.0, Math.min((double) maxWidth / image.getWidth(), (double) maxHeight / image.getHeight()));
        int w = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(image.getHeight() * scale));
        return draw(image, w, h, 0, 0, w, h);
    }

    /** Scaled to fill the box exactly, the middle kept and the overhang cut off: for the list's thumbnails. */
    static BufferedImage cover(BufferedImage image, int width, int height) {
        double scale = Math.max((double) width / image.getWidth(), (double) height / image.getHeight());
        int w = (int) Math.ceil(image.getWidth() * scale);
        int h = (int) Math.ceil(image.getHeight() * scale);
        return draw(image, width, height, (width - w) / 2, (height - h) / 2, w, h);
    }

    private static BufferedImage draw(BufferedImage image, int canvasW, int canvasH, int x, int y, int w, int h) {
        BufferedImage out = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(image, x, y, w, h, null);
        g.dispose();
        return out;
    }
}

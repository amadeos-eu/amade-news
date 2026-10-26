package eu.amadeos.apps.news;

import eu.amadeos.apps.news.domain.Feed;
import eu.amadeos.apps.news.images.ImageCache;
import eu.amadeos.apps.news.ui.dialogs.CatalogDialog;
import eu.amadeos.apps.news.ui.main.MainPresenter;
import eu.amadeos.apps.news.ui.main.MainWindow;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopTheme;

import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.RootPaneContainer;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static eu.amadeos.apps.news.Components.find;
import static eu.amadeos.apps.news.Components.onEdt;
import static eu.amadeos.apps.news.TestTexts.EN;

/**
 * The pictures of the README and the AppStream metadata, taken from the real windows with made-up feeds served
 * from memory: {@code xvfb-run ./gradlew screenshots}. The window frames are drawn like the amadeOS window manager's.
 */
public final class Screenshots {
    private static final DesktopTheme theme = DesktopTheme.WARM_MOTIF;
    private static final String WORLD = "https://desk.example.org/rss.xml";
    private static final String SCIENCE = "https://science.example.org/rss.xml";
    private static final String SKY = "https://sky.example.org/rss.xml";
    private static final String GARDEN = "https://garden.example.org/rss.xml";
    private static final String CYCLING = "https://cycling.example.org/rss.xml";

    public static void main(String[] args) throws Exception {
        try {
            take(args);
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        } finally {
            System.exit(0);
        }
    }

    private static void take(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "docs/screenshots");
        Path work = Files.createTempDirectory("amade-news-shots");
        Files.createDirectories(out);
        Scene scene = new Scene(work);
        serve(scene.http);
        scene.subscriptions.load();
        scene.subscriptions.add(List.of(Feed.of("World Desk", WORLD, ""),
                Feed.of("Science Today", SCIENCE, "Science"), Feed.of("Sky Notes", SKY, "Science"),
                Feed.of("Garden Diary", GARDEN, "Blogs"), Feed.of("Cycling Tours", CYCLING, "Blogs")));
        onEdt(() -> AmadeUI.install(theme));

        MainWindow[] main = new MainWindow[1];
        MainPresenter[] presenter = new MainPresenter[1];
        onEdt(() -> {
            ImageCache images = new ImageCache(scene.http, work.resolve("images"), Runnable::run, Runnable::run,
                    scene.clock);
            main[0] = new MainWindow(EN, null, images);
            presenter[0] = new MainPresenter(main[0], EN, null, scene.services());
            main[0].bind(presenter[0]);
            main[0].setSize(1180, 720);
            main[0].setVisible(true);
            presenter[0].start(List.of());
        });
        onEdt(() -> {
            @SuppressWarnings("unchecked")
            JList<Object> articles = find(main[0], "articles", JList.class);
            articles.setSelectedIndex(2);
            articles.setSelectedIndex(0);
        });
        Thread.sleep(500);
        save(main[0], out.resolve("amade-news.png"));

        CatalogDialog[] catalog = new CatalogDialog[1];
        onEdt(() -> {
            catalog[0] = new CatalogDialog(main[0], EN, scene.catalog, Set.of("https://feeds.bbci.co.uk/news/world/rss.xml",
                    "https://rss.dw.com/xml/rss-en-all", "https://www.france24.com/en/rss"), List.of("en"));
            catalog[0].setModalityType(JDialog.ModalityType.MODELESS);
            catalog[0].setVisible(true);
        });
        Thread.sleep(500);
        save(catalog[0], out.resolve("amade-news-europe.png"));
        onEdt(catalog[0]::dispose);
        onEdt(main[0]::dispose);
        System.exit(0);
    }

    private static void serve(FakeHttp http) throws Exception {
        http.serve(WORLD, Scene.rss("World Desk", "https://desk.example.org/",
                item(http, "w1", "Ministers agree on a night train network", "Mon, 26 Oct 2026 07:40:00 GMT",
                        "Twelve countries want sleeper trains between their capitals by 2029. The first lines are to run from Vienna, Brussels and Copenhagen; tickets are to be bookable across borders.",
                        new Color(40, 60, 110), new Color(230, 160, 90)),
                item(http, "w2", "River levels fall after a week of rain", "Mon, 26 Oct 2026 06:15:00 GMT",
                        "Towns along the river can open their quays again.", new Color(90, 130, 160), new Color(200, 210, 200)),
                item(http, "w3", "Harbour reopens after repairs", "Sun, 25 Oct 2026 19:30:00 GMT",
                        "The ferries run on the usual timetable from Tuesday.", null, null),
                item(http, "w4", "Parliament debates the budget - opposition calls the spending plans for the coming two years unrealistic",
                        "Sun, 25 Oct 2026 15:00:00 GMT", "The vote is planned for December.", new Color(110, 90, 80), new Color(180, 170, 150)),
                item(http, "w5", "Regional elections: turnout higher than four years ago", "Sun, 25 Oct 2026 12:00:00 GMT",
                        "First results are expected at 18:00.", null, null),
                item(http, "w6", "Museums open their doors for a long night of culture", "Sat, 24 Oct 2026 20:00:00 GMT",
                        "More than eighty houses take part.", new Color(60, 40, 90), new Color(200, 170, 90))), "application/rss+xml");
        http.serve(SCIENCE, Scene.rss("Science Today", "https://science.example.org/",
                item(http, "s1", "Bees can count to four", "Mon, 26 Oct 2026 05:00:00 GMT",
                        "A study of honey bees shows they tell small numbers apart.", new Color(240, 200, 60), new Color(120, 90, 30)),
                item(http, "s2", "A new map of the sea floor", "Sat, 24 Oct 2026 12:00:00 GMT",
                        "Sonar ships measured an area larger than France.", null, null)), "application/rss+xml");
        http.serve(SKY, Scene.rss("Sky Notes", "https://sky.example.org/",
                item(http, "k1", "A comet in the morning sky", "Mon, 26 Oct 2026 04:20:00 GMT",
                        "Look east before sunrise this week.", new Color(10, 20, 60), new Color(80, 110, 170))), "application/rss+xml");
        http.serve(GARDEN, Scene.rss("Garden Diary", "https://garden.example.org/",
                item(http, "g1", "Planting tulips before the frost", "Sun, 25 Oct 2026 16:00:00 GMT",
                        "Ten centimetres deep, pointed end up.", new Color(120, 170, 80), new Color(220, 80, 90))), "application/rss+xml");
        http.serve(CYCLING, Scene.rss("Cycling Tours", "https://cycling.example.org/",
                item(http, "c1", "Along the lake in autumn colours", "Fri, 23 Oct 2026 18:00:00 GMT",
                        "Sixty kilometres, mostly flat, with three cafés on the way.", new Color(200, 120, 40), new Color(60, 120, 170))), "application/rss+xml");
    }

    /** An item with a made-up picture: sky and land in two colours. */
    private static String item(FakeHttp http, String id, String title, String date, String text, Color top, Color bottom)
            throws Exception {
        String picture = "";
        if (top != null) {
            String url = "https://pictures.example.org/" + id + ".png";
            http.serve(url, 200, picture(top, bottom), Map.of("content-type", "image/png"));
            picture = "<enclosure url=\"" + url + "\" type=\"image/png\" length=\"0\"/>";
        }
        return "<item><guid>" + id + "</guid><title>" + title + "</title><link>https://desk.example.org/" + id
                + "</link><pubDate>" + date + "</pubDate><description>" + text + "</description>" + picture + "</item>";
    }

    private static byte[] picture(Color top, Color bottom) throws Exception {
        BufferedImage img = new BufferedImage(640, 360, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, top, 0, 240, top.brighter()));
        g.fillRect(0, 0, 640, 360);
        g.setColor(new Color(255, 240, 200, 200));
        g.fillOval(440, 50, 70, 70);
        g.setPaint(new GradientPaint(0, 200, bottom, 0, 360, bottom.darker()));
        int[] xs = {0, 120, 260, 380, 520, 640, 640, 0};
        int[] ys = {250, 200, 235, 190, 230, 210, 360, 360};
        g.fillPolygon(xs, ys, xs.length);
        g.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bytes);
        return bytes.toByteArray();
    }

    /** The window's inside with a drawn frame, on the workspace colour. */
    static void save(Window window, Path file) throws Exception {
        BufferedImage[] img = new BufferedImage[1];
        onEdt(() -> {
            String title = window instanceof JFrame f ? f.getTitle() : ((JDialog) window).getTitle();
            img[0] = framed(title, (RootPaneContainer) window);
        });
        int gap = 20;
        BufferedImage shot = new BufferedImage(img[0].getWidth() + 2 * gap, img[0].getHeight() + 2 * gap,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = shot.createGraphics();
        g.setColor(theme.workspaceColor(0));
        g.fillRect(0, 0, shot.getWidth(), shot.getHeight());
        g.drawImage(img[0], gap, gap, null);
        g.dispose();
        ImageIO.write(shot, "png", file.toFile());
        System.out.println("Wrote " + file);
    }

    private static BufferedImage framed(String title, RootPaneContainer window) {
        var root = window.getRootPane();
        int cw = root.getWidth(), ch = root.getHeight();
        int border = 5, titleH = 24;
        int w = cw + 2 * border, h = ch + 2 * border + titleH;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(theme.activeTitleBg());
        g.fillRect(0, 0, w, h);
        AmadeUI.paintBevel(g, 0, 0, w, h, true, 2);
        int x0 = border, y0 = border;
        AmadeUI.paintBevel(g, x0, y0, titleH, titleH, true, 1);
        g.setColor(theme.highlight());
        g.fillRect(x0 + 6, y0 + 10, titleH - 12, 4);
        AmadeUI.paintBevel(g, x0 + 6, y0 + 10, titleH - 12, 4, true, 1);
        int tx = x0 + titleH, tw = w - 2 * border - 3 * titleH;
        AmadeUI.paintBevel(g, tx, y0, tw, titleH, true, 1);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.setColor(DesktopTheme.isDark(theme.activeTitleBg()) ? Color.WHITE : Color.BLACK);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, tx + (tw - fm.stringWidth(title)) / 2, y0 + (titleH + fm.getAscent()) / 2 - 2);
        int bx = tx + tw;
        AmadeUI.paintBevel(g, bx, y0, titleH, titleH, true, 1);
        g.setColor(theme.highlight());
        g.fillRect(bx + 10, y0 + 10, 4, 4);
        AmadeUI.paintBevel(g, bx + 10, y0 + 10, 4, 4, true, 1);
        bx += titleH;
        AmadeUI.paintBevel(g, bx, y0, titleH, titleH, true, 1);
        AmadeUI.paintBevel(g, bx + 5, y0 + 5, titleH - 10, titleH - 10, true, 1);
        Graphics2D cg = (Graphics2D) g.create(border, border + titleH, cw, ch);
        root.printAll(cg);
        cg.dispose();
        g.dispose();
        return img;
    }
}

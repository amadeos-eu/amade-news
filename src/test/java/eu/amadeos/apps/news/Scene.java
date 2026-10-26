package eu.amadeos.apps.news;

import eu.amadeos.apps.news.catalog.Catalog;
import eu.amadeos.apps.news.domain.Feed;
import eu.amadeos.apps.news.store.ArticleStore;
import eu.amadeos.apps.news.store.NewsPreferences;
import eu.amadeos.apps.news.store.Subscriptions;
import eu.amadeos.apps.news.ui.main.MainPresenter;
import eu.amadeos.apps.news.update.FeedFinder;
import eu.amadeos.apps.news.update.FeedUpdater;
import eu.amadeos.commons.desktop.DesktopLauncher;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * A reader's world for the presenter and window tests: subscriptions and articles in a temporary folder, a fake
 * network serving two feeds, a clock that stands at Monday 26 October 2026, 08:00 UTC until a test moves it.
 */
public final class Scene {
    public static final Instant NOW = Instant.parse("2026-10-26T08:00:00Z");
    public static final String WORLD = "https://world.example.org/rss.xml";
    public static final String SCIENCE = "https://science.example.org/atom.xml";
    public static final String SITE = "https://blog.example.org/";

    public final FakeHttp http = new FakeHttp();
    public final MovableClock clock = new MovableClock(NOW);
    public final Subscriptions subscriptions;
    public final ArticleStore store;
    public final NewsPreferences preferences;
    public final Catalog catalog = Catalog.load();
    public final List<String> browsed = new ArrayList<>();
    public final DesktopLauncher launcher = new DesktopLauncher() {
        @Override
        public void browse(String uri) throws IOException {
            if (uri.contains("broken")) throw new IOException("no browser");
            browsed.add(uri);
        }
    };
    public int exits;

    public Scene(Path dir) {
        subscriptions = new Subscriptions(dir.resolve("config/subscriptions.opml"), clock);
        store = new ArticleStore(dir.resolve("articles"));
        preferences = new NewsPreferences(dir.resolve("config/news.properties"));
        http.serve(WORLD, 200, rss("World News", "https://world.example.org/",
                item("w1", "Summit ends with a deal", "Mon, 26 Oct 2026 07:30:00 GMT", "Leaders agreed late at night."),
                item("w2", "Storm reaches the coast", "Mon, 26 Oct 2026 06:10:00 GMT", "Trains stopped in the north."),
                item("w3", "Markets calm", "Sun, 25 Oct 2026 21:00:00 GMT", "Prices hardly moved.")).getBytes(),
                java.util.Map.of("content-type", "application/rss+xml"));
        http.serve(SCIENCE, 200, atom("Science Today",
                entry("s1", "A comet in the morning sky", "2026-10-26T05:00:00Z", "Look east before sunrise."),
                entry("s2", "Bees count to four", "2026-10-24T12:00:00Z", "A new study of honey bees.")).getBytes(),
                java.util.Map.of("content-type", "application/atom+xml"));
        http.serve(SITE, "<html><head><title>A blog</title>"
                + "<link rel=\"alternate\" type=\"application/rss+xml\" title=\"Posts\" href=\"/feed.xml\">"
                + "</head><body></body></html>", "text/html");
        http.serve("https://blog.example.org/feed.xml", rss("A blog", SITE,
                item("b1", "Hello", "Sat, 24 Oct 2026 10:00:00 GMT", "First post.")), "application/rss+xml");
    }

    /** Subscribes the two feeds of the scene: World News without folder, Science Today in Science. */
    public Scene withFeeds() throws IOException {
        subscriptions.load();
        subscriptions.add(List.of(Feed.of("World News", WORLD, ""), Feed.of("Science Today", SCIENCE, "Science")));
        return this;
    }

    /** The presenter's services; the executors run the work straight away, so a test sees the result at once. */
    public MainPresenter.Services services(Executor background, Executor ui) {
        return new MainPresenter.Services(subscriptions, store, preferences, new FeedUpdater(http, store, clock),
                new FeedFinder(http), catalog, launcher, background, ui, clock, ZoneOffset.UTC, () -> exits++);
    }

    public MainPresenter.Services services() {
        return services(Runnable::run, Runnable::run);
    }

    public static String rss(String title, String link, String... items) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\"?><rss version=\"2.0\"><channel><title>")
                .append(title).append("</title><link>").append(link).append("</link>");
        for (String item : items) sb.append(item);
        return sb.append("</channel></rss>").toString();
    }

    public static String item(String id, String title, String date, String text) {
        return "<item><guid>" + id + "</guid><title>" + title + "</title><link>https://world.example.org/" + id
                + "</link><pubDate>" + date + "</pubDate><description>" + text + "</description></item>";
    }

    public static String atom(String title, String... entries) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\"?><feed xmlns=\"http://www.w3.org/2005/Atom\"><title>")
                .append(title).append("</title><link href=\"https://science.example.org/\"/>");
        for (String e : entries) sb.append(e);
        return sb.append("</feed>").toString();
    }

    public static String entry(String id, String title, String date, String text) {
        return "<entry><id>" + id + "</id><title>" + title + "</title><link href=\"https://science.example.org/" + id
                + "\"/><updated>" + date + "</updated><summary>" + text + "</summary></entry>";
    }

    /** A clock the test moves forward. */
    public static final class MovableClock extends Clock {
        private volatile Instant now;

        MovableClock(Instant start) {
            now = start;
        }

        public void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

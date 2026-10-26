package eu.amadeos.apps.news.update;

import eu.amadeos.apps.news.FakeHttp;
import eu.amadeos.apps.news.domain.Article;
import eu.amadeos.apps.news.domain.Feed;
import eu.amadeos.apps.news.store.ArticleStore;
import eu.amadeos.apps.news.store.FeedState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedUpdaterTest {
    static final String URL = "https://news.example.org/feed.xml";
    static final Instant NOW = Instant.parse("2026-10-26T08:00:00Z");

    @TempDir
    Path dir;

    static String rss(String... items) {
        StringBuilder sb = new StringBuilder("<rss version=\"2.0\"><channel><title>Example</title><link>https://news.example.org/</link>");
        for (String item : items) sb.append(item);
        return sb.append("</channel></rss>").toString();
    }

    static String item(String id, String title, String date) {
        return "<item><guid>" + id + "</guid><title>" + title + "</title><pubDate>" + date + "</pubDate></item>";
    }

    private FeedUpdater updater(FakeHttp http, ArticleStore store, Instant now) {
        return new FeedUpdater(http, store, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void newArticlesAreAddedAndKnownOnesKeepTheirState() throws Exception {
        FakeHttp http = new FakeHttp().serve(URL, 200, rss(item("a", "A", "Mon, 26 Oct 2026 07:00:00 GMT"),
                item("b", "B", "Mon, 26 Oct 2026 06:00:00 GMT")).getBytes(), Map.of("etag", "\"v1\""));
        ArticleStore store = new ArticleStore(dir);
        Feed feed = Feed.of("Example", URL, "");
        FeedUpdater.Result first = updater(http, store, NOW).update(feed, 30);
        assertEquals(2, first.added());
        assertEquals("Example", first.parsed().orElseThrow().title());
        store.update(a -> a.id().equals("a"), a -> a.withRead(true).withStarred(true));

        http.serve(URL, 200, rss(item("a", "A changed", "Mon, 26 Oct 2026 07:00:00 GMT"),
                item("c", "C", "Mon, 26 Oct 2026 07:30:00 GMT")).getBytes(), Map.of("etag", "\"v2\""));
        FeedUpdater.Result second = updater(http, store, NOW.plusSeconds(60)).update(feed, 30);
        assertEquals(1, second.added());
        assertEquals(Map.of("If-None-Match", "\"v1\""), http.requestHeaders.getLast());
        List<Article> all = store.of(feed.id());
        assertEquals(List.of("c", "a", "b"), all.stream().map(Article::id).toList());
        Article a = all.get(1);
        assertEquals("A changed", a.title());
        assertTrue(a.read() && a.starred());
        assertEquals("\"v2\"", store.state(feed.id()).etag());

        // the file has everything
        ArticleStore again = new ArticleStore(dir);
        again.load(List.of(feed.id()));
        assertEquals(all, again.of(feed.id()));
        assertEquals("\"v2\"", again.state(feed.id()).etag());
    }

    @Test
    void notModifiedKeepsEverything() throws Exception {
        FakeHttp http = new FakeHttp().serve(URL, 200, rss(item("a", "A", "Mon, 26 Oct 2026 07:00:00 GMT")).getBytes(),
                Map.of("last-modified", "Mon, 26 Oct 2026 07:00:00 GMT"));
        ArticleStore store = new ArticleStore(dir);
        Feed feed = Feed.of("", URL, "");
        updater(http, store, NOW).update(feed, 30);
        http.status(URL, 304, Map.of());
        FeedUpdater.Result r = updater(http, store, NOW.plusSeconds(1800)).update(feed, 30);
        assertEquals(0, r.added());
        assertFalse(r.failed());
        assertEquals(Map.of("If-Modified-Since", "Mon, 26 Oct 2026 07:00:00 GMT"), http.requestHeaders.getLast());
        assertEquals(1, store.of(feed.id()).size());
        assertEquals(NOW.plusSeconds(1800), store.state(feed.id()).checked());
    }

    @Test
    void oldArticlesGoUnlessKeptOrStillInTheFeed() throws Exception {
        ArticleStore store = new ArticleStore(dir);
        Feed feed = Feed.of("", URL, "");
        FakeHttp http = new FakeHttp().serve(URL, rss(item("old", "Old", "Mon, 01 Jun 2026 07:00:00 GMT"),
                item("starred", "S", "Mon, 01 Jun 2026 07:00:00 GMT"), item("gone", "G", "Mon, 01 Jun 2026 07:00:00 GMT")),
                "application/rss+xml");
        updater(http, store, NOW).update(feed, 30);
        store.update(a -> a.id().equals("starred"), a -> a.withStarred(true));
        http.serve(URL, rss(item("old", "Old", "Mon, 01 Jun 2026 07:00:00 GMT"), item("new", "N", "Mon, 26 Oct 2026 07:00:00 GMT")),
                "application/rss+xml");
        updater(http, store, NOW).update(feed, 30);
        assertEquals(List.of("new", "old", "starred"), store.of(feed.id()).stream().map(Article::id).sorted().toList());
    }

    @Test
    void errorsAndPauses() throws Exception {
        ArticleStore store = new ArticleStore(dir);
        Feed feed = Feed.of("", URL, "");
        FakeHttp http = new FakeHttp();
        assertEquals("no-connection", updater(http, store, NOW).update(feed, 30).error());
        assertEquals("no-connection", store.state(feed.id()).error());

        http.status(URL, 404, Map.of());
        assertEquals("http-404", updater(http, store, NOW).update(feed, 30).error());

        http.serve(URL, "<html><body>moved</body></html>", "text/html");
        assertEquals("web-page", updater(http, store, NOW).update(feed, 30).error());

        http.status(URL, 429, Map.of("retry-after", "120"));
        assertEquals("http-429", updater(http, store, NOW).update(feed, 30).error());
        FeedState state = store.state(feed.id());
        assertEquals(NOW.plusSeconds(120), state.notBefore());
        int before = http.requests.size();
        FeedUpdater.Result skipped = updater(http, store, NOW.plusSeconds(60)).update(feed, 30);
        assertFalse(skipped.failed());
        assertEquals(before, http.requests.size(), "no request while the server asked to wait");
        http.serve(URL, rss(item("a", "A", "Mon, 26 Oct 2026 07:00:00 GMT")), "application/rss+xml");
        FeedUpdater.Result after = updater(http, store, NOW.plusSeconds(121)).update(feed, 30);
        assertEquals(1, after.added());
        assertEquals("", store.state(feed.id()).error());
    }

    @Test
    void retryAfterAndDates() {
        assertEquals(Duration.ofMinutes(5), FeedUpdater.retryAfter("300", NOW));
        assertEquals(Duration.ofHours(1), FeedUpdater.retryAfter("", NOW));
        assertEquals(Duration.ofHours(2), FeedUpdater.retryAfter("Mon, 26 Oct 2026 10:00:00 GMT", NOW));
        assertEquals(Duration.ofDays(1), FeedUpdater.retryAfter("999999", NOW));
        assertEquals(Duration.ofMinutes(1), FeedUpdater.retryAfter("0", NOW));
    }

    @Test
    void futureAndMissingDatesBecomeNow() throws Exception {
        ArticleStore store = new ArticleStore(dir);
        Feed feed = Feed.of("", URL, "");
        FakeHttp http = new FakeHttp().serve(URL, rss(item("f", "Future", "Mon, 26 Oct 2099 07:00:00 GMT"),
                "<item><guid>n</guid><title>No date</title></item>"), "application/rss+xml");
        updater(http, store, NOW).update(feed, 30);
        assertTrue(store.of(feed.id()).stream().allMatch(a -> a.published().equals(NOW)));
    }
}

package eu.amadeos.apps.news.update;

import eu.amadeos.apps.news.FakeHttp;
import eu.amadeos.apps.news.feed.FeedParserTest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeedFinderTest {
    @Test
    void addresses() {
        assertEquals("https://fsfe.org", FeedFinder.normalize(" fsfe.org "));
        assertEquals("http://example.org/rss", FeedFinder.normalize("http://example.org/rss"));
        assertEquals("https://example.org/rss", FeedFinder.normalize("feed://example.org/rss"));
        assertEquals("https://example.org/rss", FeedFinder.normalize("feed:https://example.org/rss"));
    }

    @Test
    void aFeedAddressIsTheCandidate() throws Exception {
        FakeHttp http = new FakeHttp().serve("https://news.example.org/feed.xml", 200,
                FeedParserTest.sample("rss2.xml"), Map.of("content-type", "application/rss+xml"));
        List<FeedFinder.Candidate> found = new FeedFinder(http).find("news.example.org/feed.xml");
        assertEquals(1, found.size());
        FeedFinder.Candidate c = found.getFirst();
        assertEquals("Morgenpost & Abendblatt", c.title());
        assertEquals(3, c.articles());
        assertEquals("RSS", c.format());
        assertEquals("de", c.language());
        assertEquals(java.time.Instant.parse("2026-10-26T06:12:00Z"), c.newest());
    }

    @Test
    void aWebsiteNamesItsFeeds() throws Exception {
        FakeHttp http = new FakeHttp()
                .serve("https://example.org", 200, FeedParserTest.sample("page.html"), Map.of("content-type", "text/html; charset=utf-8"))
                .serve("https://example.org/feed.xml", 200, FeedParserTest.sample("rss2.xml"), Map.of())
                .serve("https://other.example.org/atom", 200, FeedParserTest.sample("atom.xml"), Map.of());
        List<FeedFinder.Candidate> found = new FeedFinder(http).find("example.org");
        assertEquals(List.of("Morgenpost & Abendblatt", "Notizie di prova"), found.stream().map(FeedFinder.Candidate::title).toList());
        assertEquals("Atom", found.get(1).format());
    }

    @Test
    void nothingFound() {
        FakeHttp http = new FakeHttp().serve("https://plain.example.org", 200,
                "<html><head></head><body>no feeds</body></html>".getBytes(StandardCharsets.UTF_8), Map.of("content-type", "text/html"));
        assertEquals("none", assertThrows(FeedFinder.NotFound.class, () -> new FeedFinder(http).find("plain.example.org")).reason());
        assertEquals("no-connection", assertThrows(FeedFinder.NotFound.class, () -> new FeedFinder(http).find("nowhere.example")).reason());
        http.status("https://gone.example.org", 410, Map.of());
        assertEquals("http-410", assertThrows(FeedFinder.NotFound.class, () -> new FeedFinder(http).find("gone.example.org")).reason());
    }
}

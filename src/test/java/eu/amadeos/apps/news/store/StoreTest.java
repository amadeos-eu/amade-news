package eu.amadeos.apps.news.store;

import eu.amadeos.apps.news.domain.Feed;
import eu.amadeos.apps.news.domain.Settings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreTest {
    @TempDir
    Path dir;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-26T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void subscriptionsKeepFoldersTogetherAndSkipDuplicates() throws Exception {
        Subscriptions subs = new Subscriptions(dir.resolve("news/subscriptions.opml"), clock);
        subs.load();
        assertFalse(subs.existed());
        subs.add(List.of(Feed.of("tagesschau", "https://t.example/rss", "Europe"), Feed.of("Blog", "https://b.example/rss", "")));
        subs.add(List.of(Feed.of("ORF", "https://o.example/rss", "Europe"), Feed.of("Again", "https://t.example/rss", "X")));
        assertEquals(List.of("tagesschau", "ORF", "Blog"), subs.feeds().stream().map(Feed::title).toList());
        assertEquals(List.of("Europe"), subs.folders());
        assertTrue(subs.existed());

        Feed orf = subs.byUrl("https://o.example/rss").orElseThrow();
        subs.change(orf.id(), f -> f.withTitle("ORF news"));
        subs.renameFolder("Europe", "Europa");
        subs.remove(subs.byUrl("https://b.example/rss").orElseThrow().id());

        Subscriptions again = new Subscriptions(subs.file(), clock);
        again.load();
        assertEquals(List.of("tagesschau", "ORF news"), again.feeds().stream().map(Feed::title).toList());
        assertEquals(List.of("Europa"), again.folders());
        assertEquals(orf.id(), again.feeds().get(1).id(), "the id comes from the address and survives the file");
    }

    @Test
    void opmlRoundTripWithNestedFoldersAndExtras() throws Exception {
        String other = """
                <?xml version="1.0"?>
                <opml version="1.0"><head><title>Export</title></head><body>
                  <outline text="Top" xmlUrl="https://top.example/rss" htmlUrl="https://top.example/"/>
                  <outline text="Tech">
                    <outline text="Linux">
                      <outline text="Debian &amp; more" type="rss" xmlUrl="https://debian.example/news.rdf" language="en"/>
                    </outline>
                    <outline title="Blog" text="ignored" xmlUrl="https://blog.example/atom"/>
                  </outline>
                </body></opml>
                """;
        List<Feed> feeds = Opml.read(other.getBytes(StandardCharsets.UTF_8));
        assertEquals(3, feeds.size());
        assertEquals("", feeds.get(0).folder());
        assertEquals("https://top.example/", feeds.get(0).siteUrl());
        assertEquals("Linux", feeds.get(1).folder());
        assertEquals("Debian & more", feeds.get(1).title());
        assertEquals("en", feeds.get(1).language());
        assertEquals("Tech", feeds.get(2).folder());
        assertEquals("Blog", feeds.get(2).title());

        List<Feed> withFlag = List.of(new Feed(null, "ANSA", "https://ansa.example/rss", "", "Europe", "it", "it"));
        String written = Opml.write(withFlag, "amadeOS News", clock.instant().atZone(ZoneOffset.UTC));
        assertTrue(written.contains("<dateCreated>Mon, 26 Oct 2026 08:00:00 GMT</dateCreated>"), written);
        assertEquals(withFlag, Opml.read(written.getBytes(StandardCharsets.UTF_8)));
        assertThrows(Exception.class, () -> Opml.read("<html/>".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void preferences() {
        NewsPreferences prefs = new NewsPreferences(dir.resolve("news.properties"));
        assertEquals(Settings.DEFAULT, prefs.settings());
        Settings changed = new Settings(60, false, 90, 15, false, false);
        prefs.save(changed);
        assertEquals(changed, new NewsPreferences(dir.resolve("news.properties")).settings());
    }

    @Test
    void brokenArticleFilesAreSkipped() throws Exception {
        Files.writeString(dir.resolve("abc.json"), "{ not json");
        ArticleStore store = new ArticleStore(dir);
        store.load(List.of("abc", "missing"));
        assertEquals(List.of(), store.all());
        assertEquals(FeedState.NEW, store.state("abc"));
    }

    @Test
    void feedIds() {
        assertEquals(16, Feed.idOf("https://x.example/rss").length());
        assertEquals(Feed.idOf("https://x.example/rss"), Feed.of("", " https://x.example/rss ", "").id());
        assertEquals("x.example", Feed.of(" ", "https://x.example/rss", "").title());
    }
}

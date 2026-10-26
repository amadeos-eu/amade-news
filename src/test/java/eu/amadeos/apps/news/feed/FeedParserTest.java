package eu.amadeos.apps.news.feed;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FeedParserTest {
    public static byte[] sample(String name) throws IOException {
        try (InputStream in = FeedParserTest.class.getResourceAsStream("/feeds/" + name)) {
            return in.readAllBytes();
        }
    }

    @Test
    void rss2WithContentMediaAndPodcast() throws Exception {
        ParsedFeed feed = FeedParser.parse(sample("rss2.xml"), null, "https://news.example.org/feed.xml");
        assertEquals("Morgenpost & Abendblatt", feed.title());
        assertEquals("https://news.example.org/", feed.siteUrl());
        assertEquals("de", feed.language());
        assertEquals(3, feed.entries().size());

        ParsedFeed.Entry storm = feed.entries().get(0);
        assertEquals("sturm-2026-10-26", storm.id());
        assertEquals("https://news.example.org/artikel/sturm.html", storm.link());
        assertEquals(Instant.parse("2026-10-26T06:12:00Z"), storm.published());
        assertEquals("Anna Berger", storm.author());
        // content:encoded is longer than the description, so it wins; paragraphs stay apart
        assertTrue(storm.text().startsWith("Wegen umgestürzter Bäume ist der Fernverkehr zwischen Hamburg"), storm.text());
        assertTrue(storm.text().contains("unterbrochen.\n\nDie Bahn rechnet damit"), storm.text());
        assertEquals("https://news.example.org/bilder/sturm.jpg", storm.imageUrl());
        assertEquals("", storm.audioUrl());

        ParsedFeed.Entry energy = feed.entries().get(1);
        assertEquals("https://news.example.org/energie.html", energy.link());
        assertEquals(energy.link(), energy.id());
        assertEquals("Strom und Gas – ein Überblick.", energy.text());
        assertEquals("https://cdn.example.org/gross.jpg", energy.imageUrl(), "the widest media:content");
        assertEquals("Redaktion Wirtschaft", energy.author());

        ParsedFeed.Entry podcast = feed.entries().get(2);
        assertEquals("https://news.example.org/podcast/42", podcast.link(), "a permalink guid serves as link");
        assertEquals("https://news.example.org/podcast/42.mp3", podcast.audioUrl());
        assertEquals("https://news.example.org/podcast/42.jpg", podcast.imageUrl());
        assertEquals(Instant.parse("2026-10-24T15:00:00Z"), podcast.published());
    }

    @Test
    void atomWithXhtmlContentAndRelativeLinks() throws Exception {
        ParsedFeed feed = FeedParser.parse(sample("atom.xml"), null, "https://notizie.example.it/atom.xml");
        assertEquals("Notizie di prova", feed.title());
        assertEquals("https://notizie.example.it/", feed.siteUrl());
        assertEquals("it", feed.language());
        ParsedFeed.Entry venice = feed.entries().get(0);
        assertEquals("Venezia, le nuove barriere & la prima acqua alta", venice.title());
        assertEquals("https://notizie.example.it/2026/10/venezia", venice.link());
        assertEquals("tag:notizie.example.it,2026:venezia", venice.id());
        assertEquals(Instant.parse("2026-10-26T05:48:00Z"), venice.published(), "published wins over updated");
        assertEquals("https://img.example.it/venezia.jpg", venice.imageUrl());
        assertEquals("Redazione", venice.author(), "the feed's author when the entry has none");
        assertEquals("Le barriere sono state alzate nella notte.", venice.text());

        ParsedFeed.Entry wine = feed.entries().get(1);
        assertEquals("Qualità alta,\n\nquantità in calo.", wine.text());
        assertEquals("Giulia Rossi", wine.author());
        assertEquals(Instant.parse("2026-10-25T20:00:00Z"), wine.published());
    }

    @Test
    void rdfRss10() throws Exception {
        ParsedFeed feed = FeedParser.parse(sample("rdf.xml"), null, "https://rdf.example.net/rss");
        assertEquals("RDF News", feed.title());
        assertEquals(2, feed.entries().size());
        assertEquals("https://rdf.example.net/1", feed.entries().get(0).id());
        assertEquals(Instant.parse("2026-10-20T08:00:00Z"), feed.entries().get(0).published());
        assertEquals(Instant.parse("2026-10-21T00:00:00Z"), feed.entries().get(1).published());
    }

    @Test
    void brokenLatin1IsRepaired() throws Exception {
        ParsedFeed feed = FeedParser.parse(sample("broken-latin1.xml"), null, "https://b.example.com/rss");
        assertEquals("Café & Bar", feed.title());
        ParsedFeed.Entry e = feed.entries().getFirst();
        assertEquals("Grüße aus München …", e.title());
        assertEquals("https://b.example.com/1?a=1&b=2", e.link());
        assertEquals("Fish & Chips", e.text());
        assertNull(e.published());
    }

    @Test
    void webPagesAndOtherXmlAreNoFeeds() {
        FeedException page = assertThrows(FeedException.class, () ->
                FeedParser.parse("<!DOCTYPE html><html><body>x</body></html>".getBytes(StandardCharsets.UTF_8), null, "https://x"));
        assertTrue(page.webPage());
        FeedException other = assertThrows(FeedException.class, () ->
                FeedParser.parse("<svg/>".getBytes(StandardCharsets.UTF_8), null, "https://x"));
        assertFalse(other.webPage());
        assertThrows(FeedException.class, () -> FeedParser.parse(new byte[0], null, "https://x"));
    }

    @Test
    void externalEntitiesAreNeverLoaded() throws Exception {
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE rss [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
                <rss version="2.0"><channel><title>T</title><item><title>&secret;</title><guid>1</guid></item></channel></rss>
                """;
        try {
            ParsedFeed feed = FeedParser.parse(xxe.getBytes(StandardCharsets.UTF_8), null, "https://x");
            assertFalse(feed.entries().getFirst().title().contains("root:"), feed.entries().getFirst().title());
        } catch (FeedException e) {
            // refusing the document is fine too
        }
    }

    @Test
    void entriesWithoutIdGetOneFromTitleAndDate() throws Exception {
        String rss = "<rss version=\"2.0\"><channel><title>T</title><item><title>Only a title</title></item>"
                + "<item><description>no title, no link</description></item></channel></rss>";
        ParsedFeed feed = FeedParser.parse(rss.getBytes(StandardCharsets.UTF_8), null, "https://x");
        assertTrue(feed.entries().get(0).id().startsWith("sha1:"));
        assertEquals("", feed.entries().get(1).id());
    }
}

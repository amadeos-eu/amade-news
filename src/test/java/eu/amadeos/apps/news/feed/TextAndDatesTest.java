package eu.amadeos.apps.news.feed;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextAndDatesTest {
    @Test
    void htmlBecomesParagraphs() {
        assertEquals("Erster Absatz mit fett.\n\nZweiter\nmit Umbruch.\n\n• eins\n\n• zwei",
                HtmlText.toText("<p>Erster  Absatz mit <b>fett</b>.</p><p>Zweiter<br/>mit Umbruch.</p>"
                        + "<script>alert(1)</script><ul><li>eins</li><li>zwei</li></ul>"));
        assertEquals("", HtmlText.toText(null));
        assertEquals("a < b & c", HtmlText.toText("a &lt; b &amp; c"));
    }

    @Test
    void entities() {
        assertEquals("Grüße – „Zitat“ … €5 ©", HtmlText.decode("Gr&uuml;&szlig;e &ndash; &bdquo;Zitat&ldquo; &hellip; &euro;5 &#169;"));
        assertEquals("&unknown; stays", HtmlText.decode("&unknown; stays"));
        assertEquals("x😀", HtmlText.decode("x&#x1F600;"));
        assertEquals("One line, no tags", HtmlText.inline(" One <i>line</i>,\n  no&nbsp;tags "));
    }

    @Test
    void firstImageSkipsPixelsAndDataUris() {
        String html = "<img src=\"data:image/gif;base64,AAA\"><img width=\"1\" height=\"1\" src=\"/pixel.gif\">"
                + "<img alt='x' src='pics/a.jpg?w=1&amp;h=2'>";
        assertEquals(Optional.of("https://e.org/news/pics/a.jpg?w=1&h=2"), HtmlText.firstImage(html, "https://e.org/news/item"));
        assertEquals(Optional.empty(), HtmlText.firstImage("no pictures", "https://e.org"));
    }

    @Test
    void rfc822Variants() {
        assertEquals(Instant.parse("2026-10-26T06:12:00Z"), Dates.parse("Mon, 26 Oct 2026 07:12:00 +0100").orElseThrow());
        assertEquals(Instant.parse("2026-10-26T07:12:00Z"), Dates.parse("26 Oct 2026 07:12 GMT").orElseThrow());
        assertEquals(Instant.parse("2026-10-26T05:12:00Z"), Dates.parse("Mon, 26 Oct 2026 07:12:00 CEST").orElseThrow());
        assertEquals(Instant.parse("2026-10-26T11:12:00Z"), Dates.parse("Mon, 26 Oct 26 07:12:00 EDT").orElseThrow());
        assertEquals(Instant.parse("2026-03-05T09:00:00Z"), Dates.parse("Do, 05 Mär 2026 10:00:00 +01:00").orElseThrow());
        assertEquals(Instant.parse("2026-12-24T18:00:00Z"), Dates.parse("jeu., 24 déc. 2026 18:00:00 GMT").orElseThrow());
        assertEquals(Instant.parse("2026-10-26T07:12:00Z"), Dates.parse("Mon, 26 Oct 2026 07:12:00").orElseThrow());
    }

    @Test
    void isoVariants() {
        assertEquals(Instant.parse("2026-10-26T05:48:00Z"), Dates.parse("2026-10-26T06:48:00+01:00").orElseThrow());
        assertEquals(Instant.parse("2026-10-26T05:48:00Z"), Dates.parse("2026-10-26T06:48:00+0100").orElseThrow());
        assertEquals(Instant.parse("2026-10-26T06:48:00.500Z"), Dates.parse("2026-10-26T06:48:00.5Z").orElseThrow());
        assertEquals(Instant.parse("2026-10-26T06:48:00Z"), Dates.parse("2026-10-26 06:48:00").orElseThrow());
        assertEquals(Instant.parse("2026-10-26T00:00:00Z"), Dates.parse("2026-10-26").orElseThrow());
        for (String bad : List.of("", "yesterday", "32 Oct 2026 10:00", "2026-13-40")) {
            assertEquals(Optional.empty(), Dates.parse(bad), bad);
        }
    }

    @Test
    void discoveryFindsAlternateFeeds() throws Exception {
        String html = new String(FeedParserTest.sample("page.html"), java.nio.charset.StandardCharsets.UTF_8);
        List<FeedDiscovery.Found> found = FeedDiscovery.find(html, "https://example.org/blog/");
        assertEquals(List.of(new FeedDiscovery.Found("https://example.org/feed.xml", "Example & News", "RSS"),
                new FeedDiscovery.Found("https://other.example.org/atom", "", "Atom")), found);
    }
}

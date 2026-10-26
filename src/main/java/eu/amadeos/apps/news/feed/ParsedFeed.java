package eu.amadeos.apps.news.feed;

import java.time.Instant;
import java.util.List;

/**
 * What a feed document says, before News merges it with what it already has.
 *
 * @param title    the feed's name; empty if it has none
 * @param siteUrl  the website; empty if none
 * @param language ISO 639-1 code; empty if not given
 * @param entries  in document order
 */
public record ParsedFeed(String title, String siteUrl, String language, List<Entry> entries) {
    public ParsedFeed {
        entries = List.copyOf(entries);
    }

    /**
     * One item or entry.
     *
     * @param id        guid, Atom id, link, or a made-up id from title and date
     * @param title     plain text
     * @param text      plain text, paragraphs separated by an empty line
     * @param link      absolute address of the article; empty if none
     * @param published null if the feed gives no date
     * @param author    empty if unknown
     * @param imageUrl  absolute; empty if none
     * @param audioUrl  absolute; empty if none
     */
    public record Entry(String id, String title, String text, String link, Instant published, String author,
                        String imageUrl, String audioUrl) {
    }
}

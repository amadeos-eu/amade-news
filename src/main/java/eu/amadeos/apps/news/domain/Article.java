package eu.amadeos.apps.news.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * One article of a feed as News keeps it.
 *
 * @param id        the entry's id within its feed (guid, Atom id, or the link)
 * @param feedId    the {@link Feed#id()} it belongs to
 * @param title     plain text
 * @param text      plain text, paragraphs separated by an empty line
 * @param link      the article on the website; empty if the feed gives none
 * @param published when it was published, or when News first saw it if the feed has no date
 * @param author    empty if unknown
 * @param imageUrl  a picture for the list and the reading pane; empty if none
 * @param audioUrl  an audio enclosure (podcast episode); empty if none
 * @param read      the user has seen it
 * @param starred   kept for good, not removed with old articles
 * @param later     on the Read Later list
 */
public record Article(String id, String feedId, String title, String text, String link, Instant published,
                      String author, String imageUrl, String audioUrl, boolean read, boolean starred, boolean later) {
    public Article {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(feedId, "feedId");
        Objects.requireNonNull(published, "published");
        title = title == null ? "" : title;
        text = text == null ? "" : text;
        link = link == null ? "" : link;
        author = author == null ? "" : author;
        imageUrl = imageUrl == null ? "" : imageUrl;
        audioUrl = audioUrl == null ? "" : audioUrl;
    }

    /** The key used across feeds: feed id and article id. */
    public String key() {
        return feedId + "\n" + id;
    }

    public Article withRead(boolean value) {
        return new Article(id, feedId, title, text, link, published, author, imageUrl, audioUrl, value, starred, later);
    }

    public Article withStarred(boolean value) {
        return new Article(id, feedId, title, text, link, published, author, imageUrl, audioUrl, read, value, later);
    }

    public Article withLater(boolean value) {
        return new Article(id, feedId, title, text, link, published, author, imageUrl, audioUrl, read, starred, value);
    }

    // --8<-- [start:updated-from]
    /** The same article with new content from the feed, keeping what the user did with it. */
    public Article updatedFrom(Article fresh) {
        return new Article(id, feedId, fresh.title, fresh.text, fresh.link, published, fresh.author, fresh.imageUrl,
                fresh.audioUrl, read, starred, later);
    }
    // --8<-- [end:updated-from]

    /** Kept regardless of age. */
    public boolean kept() {
        return starred || later;
    }

    /** True if every word of the query is in the title or the text, ignoring case. */
    public boolean matches(String query) {
        if (query == null || query.isBlank()) return true;
        String haystack = (title + "\n" + text + "\n" + author).toLowerCase(Locale.ROOT);
        for (String word : query.toLowerCase(Locale.ROOT).strip().split("\\s+")) {
            if (!haystack.contains(word)) return false;
        }
        return true;
    }
}

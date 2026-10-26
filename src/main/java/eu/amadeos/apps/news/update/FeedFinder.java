package eu.amadeos.apps.news.update;

import eu.amadeos.apps.news.feed.FeedDiscovery;
import eu.amadeos.apps.news.feed.FeedException;
import eu.amadeos.apps.news.feed.FeedParser;
import eu.amadeos.apps.news.feed.ParsedFeed;
import eu.amadeos.apps.news.net.Http;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * What the Add Feed dialog needs: the user types a feed address or just a website, and gets the feeds behind it with
 * their names, number of articles and the date of the newest one.
 */
public final class FeedFinder {
    /** At most this many feeds named by a page are fetched for their details. */
    static final int MAX_FOUND = 6;

    /**
     * A feed that can be subscribed.
     *
     * @param newest the newest article's date; null if the feed has no dated articles
     */
    public record Candidate(String url, String title, String siteUrl, String language, String format, int articles,
                           Instant newest) {
    }

    /** Nothing found: {@code reason} is an error code as in {@link FeedUpdater.Result#error()}, or {@code none}. */
    public static final class NotFound extends Exception {
        private final String reason;

        public NotFound(String reason) {
            super(reason);
            this.reason = reason;
        }

        public String reason() {
            return reason;
        }
    }

    private final Http http;

    public FeedFinder(Http http) {
        this.http = http;
    }

    /** {@code example.org} becomes {@code https://example.org}; {@code feed:} addresses lose their scheme. */
    public static String normalize(String input) {
        String s = input == null ? "" : input.strip();
        if (s.toLowerCase(Locale.ROOT).startsWith("feed:")) {
            s = s.substring(5);
            if (s.startsWith("//")) s = "https:" + s;
        }
        if (!s.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) s = "https://" + s;
        return s;
    }

    public List<Candidate> find(String input) throws NotFound, InterruptedException {
        String url = normalize(input);
        Http.Response response = fetch(url);
        String finalUrl = response.finalUri().toString();
        try {
            ParsedFeed feed = FeedParser.parse(response.body(), response.charset(), finalUrl);
            return List.of(candidate(url, feed, format(response.body())));
        } catch (FeedException e) {
            if (!e.webPage() && !response.isHtml()) throw new NotFound("not-a-feed");
        }
        String html = new String(response.body(), Objects.requireNonNullElse(response.charset(), StandardCharsets.UTF_8));
        List<FeedDiscovery.Found> found = FeedDiscovery.find(html, finalUrl);
        if (found.isEmpty()) throw new NotFound("none");
        List<Candidate> candidates = new ArrayList<>();
        for (FeedDiscovery.Found f : found.subList(0, Math.min(found.size(), MAX_FOUND))) {
            try {
                Http.Response r = fetch(f.url());
                ParsedFeed feed = FeedParser.parse(r.body(), r.charset(), r.finalUri().toString());
                Candidate c = candidate(f.url(), feed, f.type());
                if (c.title().isEmpty() && !f.title().isEmpty()) {
                    c = new Candidate(c.url(), f.title(), c.siteUrl(), c.language(), c.format(), c.articles(), c.newest());
                }
                candidates.add(c);
            } catch (NotFound | FeedException e) {
                // a broken feed on the page is simply not offered
            }
        }
        if (candidates.isEmpty()) throw new NotFound("none");
        return candidates;
    }

    private Http.Response fetch(String url) throws NotFound, InterruptedException {
        try {
            Http.Response r = http.get(URI.create(url), Map.of());
            if (r.status() < 200 || r.status() >= 300) throw new NotFound("http-" + r.status());
            return r;
        } catch (IOException | IllegalArgumentException e) {
            throw new NotFound(FeedUpdater.message(e));
        }
    }

    private static Candidate candidate(String url, ParsedFeed feed, String format) {
        Instant newest = feed.entries().stream().map(ParsedFeed.Entry::published).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        return new Candidate(url, feed.title(), feed.siteUrl(), feed.language(), format, feed.entries().size(), newest);
    }

    private static String format(byte[] body) {
        String head = new String(body, 0, Math.min(body.length, 1024), StandardCharsets.ISO_8859_1);
        return head.contains("http://www.w3.org/2005/Atom") && head.contains("<feed") ? "Atom" : "RSS";
    }
}

package eu.amadeos.apps.news.update;

import eu.amadeos.apps.news.domain.Article;
import eu.amadeos.apps.news.domain.Feed;
import eu.amadeos.apps.news.feed.FeedException;
import eu.amadeos.apps.news.feed.FeedParser;
import eu.amadeos.apps.news.feed.ParsedFeed;
import eu.amadeos.apps.news.net.Http;
import eu.amadeos.apps.news.store.ArticleStore;
import eu.amadeos.apps.news.store.FeedState;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fetches one feed and merges it into the {@link ArticleStore}: conditional GET with the ETag and Last-Modified of
 * the last answer, and a pause when the server asks for one (429 or 503 with Retry-After, one hour if it names no
 * time).
 */
public final class FeedUpdater {
    static final Duration DEFAULT_PAUSE = Duration.ofHours(1);

    /**
     * How an update went.
     *
     * @param added  new articles
     * @param error  why it failed, as a code ({@code timeout}, {@code unknown-host}, {@code no-connection},
     *               {@code http-404}, {@code web-page}, {@code not-a-feed}) or the exception's message; empty if it
     *               worked or was skipped
     * @param parsed the feed as read, if it was read (for its title and website)
     */
    public record Result(String feedId, int added, String error, Optional<ParsedFeed> parsed) {
        public boolean failed() {
            return !error.isEmpty();
        }
    }

    private final Http http;
    private final ArticleStore store;
    private final Clock clock;

    public FeedUpdater(Http http, ArticleStore store, Clock clock) {
        this.http = http;
        this.store = store;
        this.clock = clock;
    }

    // --8<-- [start:update]
    public Result update(Feed feed, int keepDays) throws InterruptedException {
        Instant now = clock.instant();
        FeedState state = store.state(feed.id());
        if (state.notBefore() != null && now.isBefore(state.notBefore())) {
            return new Result(feed.id(), 0, "", Optional.empty());
        }
        Map<String, String> headers = new LinkedHashMap<>();
        if (!state.etag().isEmpty()) headers.put("If-None-Match", state.etag());
        if (!state.lastModified().isEmpty()) headers.put("If-Modified-Since", state.lastModified());
        Http.Response response;
        try {
            response = http.get(URI.create(feed.url()), headers);
        } catch (IOException | IllegalArgumentException e) {
            return fail(feed, state, now, message(e), null);
        }
        int status = response.status();
        if (status == 304) {
            store.touch(feed.id(), new FeedState(state.etag(), state.lastModified(), now, "", null));
            return new Result(feed.id(), 0, "", Optional.empty());
        }
        if (status == 429 || status == 503) {
            Instant until = now.plus(retryAfter(response.header("retry-after").orElse(""), now));
            return fail(feed, state, now, "http-" + status, until);
        }
        if (status < 200 || status >= 300) return fail(feed, state, now, "http-" + status, null);
        ParsedFeed parsed;
        try {
            parsed = FeedParser.parse(response.body(), response.charset(), response.finalUri().toString());
        } catch (FeedException e) {
            return fail(feed, state, now, e.webPage() ? "web-page" : "not-a-feed", null);
        }
        List<Article> fresh = articles(feed.id(), parsed, now);
        FeedState newState = new FeedState(response.header("etag").orElse(""),
                response.header("last-modified").orElse(""), now, "", null);
        int added = store.merge(feed.id(), fresh, newState, now, keepDays);
        return new Result(feed.id(), added, "", Optional.of(parsed));
    }
    // --8<-- [end:update]

    private Result fail(Feed feed, FeedState state, Instant now, String why, Instant wait) {
        store.touch(feed.id(), state.failed(now, why, wait));
        return new Result(feed.id(), 0, why, Optional.empty());
    }

    /** The entries as unread articles; no date means now, a date in the future means now as well. */
    static List<Article> articles(String feedId, ParsedFeed parsed, Instant now) {
        List<Article> list = new ArrayList<>();
        for (ParsedFeed.Entry e : parsed.entries()) {
            if (e.id().isEmpty()) continue;
            Instant published = e.published() == null || e.published().isAfter(now) ? now : e.published();
            list.add(new Article(e.id(), feedId, e.title(), e.text(), e.link(), published, e.author(), e.imageUrl(),
                    e.audioUrl(), false, false, false));
        }
        return list;
    }

    // --8<-- [start:retry-after]
    /** Retry-After in seconds or as an HTTP date; one hour if missing or unreadable, at most a day. */
    static Duration retryAfter(String value, Instant now) {
        String v = value.strip();
        Duration wait = DEFAULT_PAUSE;
        if (v.matches("\\d{1,9}")) {
            wait = Duration.ofSeconds(Long.parseLong(v));
        } else if (!v.isEmpty()) {
            try {
                wait = Duration.between(now, ZonedDateTime.parse(v, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
            } catch (DateTimeParseException e) {
                // keep the default
            }
        }
        if (wait.isNegative() || wait.isZero()) return Duration.ofMinutes(1);
        return wait.compareTo(Duration.ofDays(1)) > 0 ? Duration.ofDays(1) : wait;
    }
    // --8<-- [end:retry-after]

    /** The error code of a failed connection; the exception's message for anything else. */
    static String message(Exception e) {
        if (e instanceof java.net.http.HttpTimeoutException) return "timeout";
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof java.net.UnknownHostException
                    || cause instanceof java.nio.channels.UnresolvedAddressException) {
                return "unknown-host";
            }
            cause = cause.getCause();
        }
        if (e instanceof java.net.ConnectException) return "no-connection";
        String m = e.getMessage();
        return m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
    }
}

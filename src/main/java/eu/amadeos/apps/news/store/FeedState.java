package eu.amadeos.apps.news.store;

import java.time.Instant;

/**
 * What News remembers about fetching a feed.
 *
 * @param etag         the server's ETag, sent back as If-None-Match; empty if none
 * @param lastModified the server's Last-Modified, sent back as If-Modified-Since; empty if none
 * @param checked      the last attempt; null if never
 * @param error        why the last attempt failed; empty if it worked
 * @param notBefore    the server asked to wait (429/503 with Retry-After); null if not
 */
public record FeedState(String etag, String lastModified, Instant checked, String error, Instant notBefore) {
    public static final FeedState NEW = new FeedState("", "", null, "", null);

    public FeedState {
        etag = etag == null ? "" : etag;
        lastModified = lastModified == null ? "" : lastModified;
        error = error == null ? "" : error;
    }

    public FeedState failed(Instant when, String why, Instant wait) {
        return new FeedState(etag, lastModified, when, why, wait);
    }

    public boolean failed() {
        return !error.isEmpty();
    }
}

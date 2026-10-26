package eu.amadeos.apps.news.feed;

/** The document is not a feed News can read. */
public class FeedException extends Exception {
    private final boolean webPage;

    public FeedException(String message, boolean webPage) {
        super(message);
        this.webPage = webPage;
    }

    public FeedException(String message, Throwable cause) {
        super(message, cause);
        this.webPage = false;
    }

    /** The address gave an HTML page, so it may name its feeds ({@link FeedDiscovery}). */
    public boolean webPage() {
        return webPage;
    }
}

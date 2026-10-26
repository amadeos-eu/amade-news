package eu.amadeos.apps.news.feed;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the feeds a web page names in its head:
 * {@code <link rel="alternate" type="application/rss+xml" href="/feed" title="News">}.
 */
public final class FeedDiscovery {
    private static final Pattern LINK = Pattern.compile("(?is)<link\\b([^>]*)>");
    private static final Pattern ATTRIBUTE = Pattern.compile("(?is)([a-z-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");
    private static final List<String> TYPES = List.of("application/rss+xml", "application/atom+xml",
            "application/rdf+xml", "application/feed+json", "text/xml", "application/xml");

    /** A feed named by a page. */
    public record Found(String url, String title, String type) {
    }

    private FeedDiscovery() {
    }

    // --8<-- [start:find]
    /** The feeds in the page, in page order, without duplicates; relative addresses resolved against the page. */
    public static List<Found> find(String html, String pageUrl) {
        Map<String, Found> found = new LinkedHashMap<>();
        Matcher link = LINK.matcher(html == null ? "" : html);
        while (link.find()) {
            Map<String, String> attributes = attributes(link.group(1));
            String rel = attributes.getOrDefault("rel", "").toLowerCase(Locale.ROOT);
            String type = attributes.getOrDefault("type", "").toLowerCase(Locale.ROOT).strip();
            String href = attributes.getOrDefault("href", "");
            if (!List.of(rel.split("\\s+")).contains("alternate") || href.isBlank()) continue;
            if (!TYPES.contains(type) || type.equals("application/feed+json")) continue;
            String url = HtmlText.resolve(pageUrl, HtmlText.decode(href));
            found.putIfAbsent(url, new Found(url, HtmlText.inline(attributes.getOrDefault("title", "")),
                    type.contains("atom") ? "Atom" : "RSS"));
        }
        return new ArrayList<>(found.values());
    }
    // --8<-- [end:find]

    private static Map<String, String> attributes(String text) {
        Map<String, String> map = new LinkedHashMap<>();
        Matcher m = ATTRIBUTE.matcher(text);
        while (m.find()) {
            String value = m.group(3) != null ? m.group(3) : m.group(4) != null ? m.group(4) : m.group(5);
            map.putIfAbsent(m.group(1).toLowerCase(Locale.ROOT), value);
        }
        return map;
    }
}

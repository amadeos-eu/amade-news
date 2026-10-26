package eu.amadeos.apps.news.store;

import eu.amadeos.apps.news.domain.Feed;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OPML 2.0, the file format feed readers exchange subscriptions in, and the format News keeps its own
 * subscriptions in. Folders are outlines without {@code xmlUrl}; a feed in a folder within a folder belongs to the
 * innermost one. The flag of a feed from Europe's News is kept in an extra {@code country} attribute, which other
 * readers ignore.
 */
public final class Opml {
    private Opml() {
    }

    /** The feeds in document order. */
    public static List<Feed> read(byte[] bytes) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(bytes));
        List<Feed> feeds = new ArrayList<>();
        Deque<String> folders = new ArrayDeque<>();
        Deque<Boolean> isFolder = new ArrayDeque<>();
        boolean sawOpml = false;
        try {
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = reader.getLocalName();
                    if (name.equals("opml")) sawOpml = true;
                    if (!name.equals("outline")) continue;
                    Map<String, String> a = new LinkedHashMap<>();
                    for (int i = 0; i < reader.getAttributeCount(); i++) {
                        a.put(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
                    }
                    String url = a.getOrDefault("xmlUrl", a.getOrDefault("xmlurl", "")).strip();
                    String text = a.getOrDefault("title", "").isBlank() ? a.getOrDefault("text", "") : a.get("title");
                    if (url.isEmpty()) {
                        folders.push(text.strip());
                        isFolder.push(true);
                    } else {
                        String folder = folders.isEmpty() ? "" : folders.peek();
                        feeds.add(new Feed(null, text, url, a.getOrDefault("htmlUrl", ""), folder,
                                a.getOrDefault("country", ""), a.getOrDefault("language", "")));
                        isFolder.push(false);
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT && reader.getLocalName().equals("outline")) {
                    if (!isFolder.isEmpty() && isFolder.pop()) folders.pop();
                }
            }
        } finally {
            reader.close();
        }
        if (!sawOpml) throw new XMLStreamException("Not an OPML file");
        return feeds;
    }

    /** The feeds as OPML: feeds without a folder first, then one outline per folder in order of appearance. */
    public static String write(List<Feed> feeds, String title, ZonedDateTime now) {
        StringBuilder out = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<opml version=\"2.0\">\n");
        out.append("  <head>\n    <title>").append(escape(title)).append("</title>\n    <dateCreated>")
                .append(DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ENGLISH).format(now))
                .append("</dateCreated>\n  </head>\n  <body>\n");
        for (Feed feed : feeds) {
            if (feed.folder().isEmpty()) outline(out, feed, "    ");
        }
        Map<String, List<Feed>> byFolder = new LinkedHashMap<>();
        for (Feed feed : feeds) {
            if (!feed.folder().isEmpty()) byFolder.computeIfAbsent(feed.folder(), k -> new ArrayList<>()).add(feed);
        }
        for (Map.Entry<String, List<Feed>> folder : byFolder.entrySet()) {
            out.append("    <outline text=\"").append(escape(folder.getKey())).append("\" title=\"")
                    .append(escape(folder.getKey())).append("\">\n");
            for (Feed feed : folder.getValue()) outline(out, feed, "      ");
            out.append("    </outline>\n");
        }
        return out.append("  </body>\n</opml>\n").toString();
    }

    private static void outline(StringBuilder out, Feed feed, String indent) {
        out.append(indent).append("<outline type=\"rss\" text=\"").append(escape(feed.title()))
                .append("\" title=\"").append(escape(feed.title()))
                .append("\" xmlUrl=\"").append(escape(feed.url())).append('"');
        if (!feed.siteUrl().isEmpty()) out.append(" htmlUrl=\"").append(escape(feed.siteUrl())).append('"');
        if (!feed.language().isEmpty()) out.append(" language=\"").append(escape(feed.language())).append('"');
        if (!feed.country().isEmpty()) out.append(" country=\"").append(escape(feed.country())).append('"');
        out.append("/>\n");
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}

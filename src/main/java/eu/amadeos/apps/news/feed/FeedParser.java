package eu.amadeos.apps.news.feed;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads RSS 0.9x and 2.0, RSS 1.0 (RDF) and Atom 1.0, with the Media RSS, Dublin Core, {@code content:encoded} and
 * iTunes extensions feeds use for pictures, dates, full texts and podcasts.
 *
 * <p>Feeds in the wild are often not well-formed XML: a raw {@code &} in a title, HTML entities like
 * {@code &nbsp;} that XML does not know, control characters. When the strict parse fails, the document is repaired
 * and read once more. DTDs and external entities are never loaded.
 */
public final class FeedParser {
    private static final String ATOM = "http://www.w3.org/2005/Atom";
    private static final Pattern XML_ENCODING = Pattern.compile("^\\s*<\\?xml[^>]*encoding\\s*=\\s*[\"']([A-Za-z0-9._-]+)[\"']");
    private static final Pattern XML_DECLARATION = Pattern.compile("^\\s*<\\?xml[^>]*\\?>");
    private static final Pattern RAW_AMPERSAND = Pattern.compile("&(?!(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z][a-zA-Z0-9]{1,31});)");
    private static final Pattern NAMED_ENTITY = Pattern.compile("&([a-zA-Z][a-zA-Z0-9]{1,31});");
    private static final Pattern INVALID_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\uFFFE\\uFFFF]");
    private static final Pattern AUDIO_FILE = Pattern.compile("(?i).*\\.(mp3|m4a|aac|ogg|oga|opus|flac|wav)(\\?.*)?");
    private static final Pattern IMAGE_FILE = Pattern.compile("(?i).*\\.(jpe?g|png|gif|webp|avif)(\\?.*)?");

    private FeedParser() {
    }

    // --8<-- [start:parse]
    /**
     * Reads the document.
     *
     * @param bytes   the document as it came from the server
     * @param charset the charset the server named in Content-Type, or null; the XML declaration wins over it
     * @param base    the feed's address, for relative links
     */
    public static ParsedFeed parse(byte[] bytes, Charset charset, String base) throws FeedException {
        String start = new String(bytes, 0, Math.min(bytes.length, 512), StandardCharsets.ISO_8859_1).stripLeading()
                .toLowerCase(Locale.ROOT);
        if (start.startsWith("\ufeff")) start = start.substring(1);
        if (start.startsWith("<!doctype html") || start.startsWith("<html")) {
            throw new FeedException("The address is a web page, not a feed", true);
        }
        Element root;
        try {
            root = read(factory().createXMLStreamReader(new ByteArrayInputStream(bytes)));
        } catch (XMLStreamException | RuntimeException strict) {
            try {
                root = read(factory().createXMLStreamReader(new StringReader(repair(decode(bytes, charset)))));
            } catch (XMLStreamException | RuntimeException e) {
                throw new FeedException("Not a readable feed: " + e.getMessage(), e);
            }
        }
        return interpret(root, base);
    }
    // --8<-- [end:parse]

    // ---------------------------------------------------------------- repair

    static String decode(byte[] bytes, Charset fallback) {
        String head = new String(bytes, 0, Math.min(bytes.length, 200), StandardCharsets.ISO_8859_1);
        Matcher declared = XML_ENCODING.matcher(head.startsWith("\ufeff") ? head.substring(1) : head);
        Charset charset = fallback == null ? StandardCharsets.UTF_8 : fallback;
        if (declared.find()) {
            try {
                charset = Charset.forName(declared.group(1));
            } catch (IllegalArgumentException e) {
                // unknown name: keep the server's or UTF-8
            }
        }
        String text = new String(bytes, charset);
        return text.startsWith("\ufeff") ? text.substring(1) : text;
    }

    // --8<-- [start:repair]
    /** Escapes raw ampersands, turns HTML entities into numbers and drops characters XML forbids. */
    static String repair(String text) {
        String s = XML_DECLARATION.matcher(text.strip()).replaceFirst("");
        s = INVALID_CHARS.matcher(s).replaceAll("");
        s = RAW_AMPERSAND.matcher(s).replaceAll("&amp;");
        Matcher named = NAMED_ENTITY.matcher(s);
        StringBuilder out = new StringBuilder();
        while (named.find()) {
            String name = named.group(1);
            Integer code = switch (name) {
                case "amp", "lt", "gt", "quot", "apos" -> null;
                default -> HtmlText.NAMED.get(name);
            };
            String replacement = code != null ? "&#" + code + ";"
                    : switch (name) {
                        case "amp", "lt", "gt", "quot", "apos" -> named.group();
                        default -> "&amp;" + name + ";";
                    };
            named.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        named.appendTail(out);
        return out.toString();
    }
    // --8<-- [end:repair]

    // --8<-- [start:factory]
    private static XMLInputFactory factory() {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        return factory;
    }
    // --8<-- [end:factory]

    // ---------------------------------------------------------------- a small tree

    /** An element with its attributes (by local name, plus {@code xml:lang}), children and own text. */
    static final class Element {
        final String ns;
        final String name;
        final Map<String, String> attributes = new LinkedHashMap<>();
        final List<Element> children = new ArrayList<>();
        final StringBuilder text = new StringBuilder();
        /** Text and child elements in document order, as HTML: for Atom's {@code type="xhtml"} content. */
        final StringBuilder markup = new StringBuilder();

        Element(String ns, String name) {
            this.ns = ns == null ? "" : ns;
            this.name = name;
        }

        String attribute(String key) {
            return attributes.getOrDefault(key, "");
        }

        String text() {
            return text.toString().strip();
        }

        Optional<Element> child(String localName) {
            return children.stream().filter(c -> c.name.equals(localName)).findFirst();
        }

        String childText(String localName) {
            return child(localName).map(Element::text).orElse("");
        }

        List<Element> all(String localName) {
            return children.stream().filter(c -> c.name.equals(localName)).toList();
        }
    }

    private static Element read(XMLStreamReader reader) throws XMLStreamException {
        List<Element> stack = new ArrayList<>();
        Element root = null;
        try {
            while (reader.hasNext()) {
                int event = reader.next();
                switch (event) {
                    case XMLStreamConstants.START_ELEMENT -> {
                        Element e = new Element(reader.getNamespaceURI(), reader.getLocalName());
                        for (int i = 0; i < reader.getAttributeCount(); i++) {
                            String prefix = reader.getAttributePrefix(i);
                            String key = XMLConstants.XML_NS_URI.equals(reader.getAttributeNamespace(i))
                                    || "xml".equals(prefix) ? "xml:" + reader.getAttributeLocalName(i)
                                    : reader.getAttributeLocalName(i);
                            e.attributes.put(key, reader.getAttributeValue(i));
                        }
                        if (stack.isEmpty()) {
                            root = e;
                        } else {
                            Element parent = stack.getLast();
                            parent.children.add(e);
                            for (Element open : stack) open.markup.append(startTag(e));
                        }
                        stack.add(e);
                    }
                    case XMLStreamConstants.END_ELEMENT -> {
                        Element closed = stack.removeLast();
                        for (Element open : stack) open.markup.append("</").append(closed.name).append('>');
                    }
                    case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA, XMLStreamConstants.SPACE -> {
                        if (stack.isEmpty()) break;
                        String chars = reader.getText();
                        stack.getLast().text.append(chars);
                        String escaped = chars.replace("&", "&amp;").replace("<", "&lt;");
                        for (Element open : stack) open.markup.append(escaped);
                    }
                    default -> {
                        // comments, processing instructions, DTD
                    }
                }
            }
        } finally {
            reader.close();
        }
        if (root == null) throw new XMLStreamException("empty document");
        return root;
    }

    private static String startTag(Element e) {
        StringBuilder tag = new StringBuilder("<").append(e.name);
        for (String key : List.of("href", "src", "alt")) {
            if (e.attributes.containsKey(key)) {
                tag.append(' ').append(key).append("=\"").append(e.attribute(key).replace("\"", "&quot;")).append('"');
            }
        }
        return tag.append('>').toString();
    }

    // ---------------------------------------------------------------- RSS, RDF and Atom

    private static ParsedFeed interpret(Element root, String base) throws FeedException {
        return switch (root.name) {
            case "rss" -> {
                Element channel = root.child("channel").orElseThrow(() -> new FeedException("RSS without channel", false));
                yield rss(channel, channel.all("item"), base);
            }
            case "RDF" -> {
                Element channel = root.child("channel").orElse(new Element("", "channel"));
                yield rss(channel, root.all("item"), base);
            }
            case "feed" -> atom(root, base);
            default -> throw new FeedException("Not a feed: <" + root.name + ">", root.name.equalsIgnoreCase("html"));
        };
    }

    private static ParsedFeed rss(Element channel, List<Element> items, String base) {
        String site = HtmlText.resolve(base, channel.all("link").stream().filter(l -> l.ns.isEmpty()
                || !l.ns.equals(ATOM)).map(Element::text).filter(s -> !s.isEmpty()).findFirst().orElse(""));
        String language = language(channel.childText("language"));
        List<ParsedFeed.Entry> entries = new ArrayList<>();
        for (Element item : items) {
            String link = HtmlText.resolve(site.isEmpty() ? base : site, item.all("link").stream()
                    .filter(l -> !ATOM.equals(l.ns)).map(Element::text).filter(s -> !s.isEmpty()).findFirst()
                    .orElse(item.attribute("about")));
            Optional<Element> guid = item.child("guid");
            if (link.isEmpty() && guid.isPresent() && !"false".equalsIgnoreCase(guid.get().attribute("isPermaLink"))
                    && guid.get().text().startsWith("http")) {
                link = guid.get().text();
            }
            String html = longest(childTextNs(item, "encoded"), item.childText("description"));
            String title = HtmlText.inline(item.childText("title"));
            Instant published = Dates.parse(first(item.childText("pubDate"), item.childText("date"),
                    item.childText("published"), item.childText("updated"))).orElse(null);
            String id = first(guid.map(Element::text).orElse(""), link, made(title, published));
            String author = rssAuthor(first(item.childText("creator"), item.childText("author")));
            entries.add(new ParsedFeed.Entry(id, title, HtmlText.toText(html), link, published, author,
                    image(item, html, link.isEmpty() ? base : link), audio(item, base)));
        }
        return new ParsedFeed(HtmlText.inline(channel.childText("title")), site, language, entries);
    }

    private static ParsedFeed atom(Element feed, String base) {
        String feedBase = withBase(base, feed.attribute("xml:base"));
        String site = alternate(feed, feedBase);
        List<ParsedFeed.Entry> entries = new ArrayList<>();
        for (Element entry : feed.all("entry")) {
            String entryBase = withBase(feedBase, entry.attribute("xml:base"));
            String link = alternate(entry, entryBase);
            String content = entry.child("content").map(FeedParser::atomText).orElse("");
            String summary = entry.child("summary").map(FeedParser::atomText).orElse("");
            String html = longest(content, summary);
            String title = HtmlText.inline(entry.child("title").map(FeedParser::atomText).orElse(""));
            Instant published = Dates.parse(first(entry.childText("published"), entry.childText("updated"),
                    entry.childText("date"))).orElse(null);
            String author = entry.child("author").map(a -> a.childText("name")).filter(s -> !s.isEmpty())
                    .orElse(feed.child("author").map(a -> a.childText("name")).orElse(""));
            String id = first(entry.childText("id"), link, made(title, published));
            entries.add(new ParsedFeed.Entry(id, title, HtmlText.toText(html), link, published, HtmlText.inline(author),
                    image(entry, html, link.isEmpty() ? entryBase : link), audio(entry, entryBase)));
        }
        String language = language(feed.attribute("xml:lang"));
        return new ParsedFeed(HtmlText.inline(feed.child("title").map(FeedParser::atomText).orElse("")), site,
                language, entries);
    }

    private static String withBase(String base, String xmlBase) {
        return xmlBase.isBlank() ? base : HtmlText.resolve(base, xmlBase);
    }

    /** Atom text constructs: {@code type="xhtml"} carries markup as child elements, html and text carry text. */
    private static String atomText(Element e) {
        if ("xhtml".equals(e.attribute("type"))) return e.markup.toString();
        if (!e.children.isEmpty() && e.text().isEmpty()) return e.markup.toString();
        String type = e.attribute("type");
        if (type.isEmpty() || type.equals("text")) return e.text().replace("&", "&amp;").replace("<", "&lt;");
        return e.text();
    }

    private static String alternate(Element e, String base) {
        String fallback = "";
        for (Element link : e.all("link")) {
            String rel = link.attribute("rel");
            String href = link.attribute("href");
            if (href.isEmpty()) continue;
            if (rel.isEmpty() || rel.equals("alternate")) {
                if (link.attribute("type").isEmpty() || link.attribute("type").contains("html")) {
                    return HtmlText.resolve(base, href);
                }
                if (fallback.isEmpty()) fallback = HtmlText.resolve(base, href);
            }
        }
        return fallback;
    }

    // ---------------------------------------------------------------- pictures and audio

    private static String image(Element item, String html, String base) {
        for (Element enclosure : item.all("enclosure")) {
            String url = enclosure.attribute("url");
            if (!url.isEmpty() && (enclosure.attribute("type").startsWith("image") || IMAGE_FILE.matcher(url).matches())) {
                return HtmlText.resolve(base, url);
            }
        }
        for (Element link : item.all("link")) {
            if ("enclosure".equals(link.attribute("rel")) && link.attribute("type").startsWith("image")) {
                return HtmlText.resolve(base, link.attribute("href"));
            }
        }
        List<Element> media = new ArrayList<>(item.all("content"));
        item.all("group").forEach(g -> media.addAll(g.all("content")));
        Element best = null;
        for (Element m : media) {
            String url = m.attribute("url");
            if (url.isEmpty()) continue;
            boolean picture = "image".equals(m.attribute("medium")) || m.attribute("type").startsWith("image")
                    || (m.attribute("medium").isEmpty() && m.attribute("type").isEmpty() && IMAGE_FILE.matcher(url).matches());
            if (picture && (best == null || width(m) > width(best))) best = m;
        }
        if (best != null) return HtmlText.resolve(base, best.attribute("url"));
        List<Element> thumbnails = new ArrayList<>(item.all("thumbnail"));
        item.all("group").forEach(g -> thumbnails.addAll(g.all("thumbnail")));
        media.forEach(m -> thumbnails.addAll(m.all("thumbnail")));
        Element thumb = null;
        for (Element t : thumbnails) {
            if (!t.attribute("url").isEmpty() && (thumb == null || width(t) > width(thumb))) thumb = t;
        }
        if (thumb != null) return HtmlText.resolve(base, thumb.attribute("url"));
        Optional<Element> itunes = item.child("image").filter(i -> !i.attribute("href").isEmpty());
        if (itunes.isPresent()) return HtmlText.resolve(base, itunes.get().attribute("href"));
        return HtmlText.firstImage(html, base).orElse("");
    }

    private static int width(Element e) {
        try {
            return Integer.parseInt(e.attribute("width").strip());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String audio(Element item, String base) {
        for (Element enclosure : item.all("enclosure")) {
            String url = enclosure.attribute("url");
            if (!url.isEmpty() && (enclosure.attribute("type").startsWith("audio") || AUDIO_FILE.matcher(url).matches())) {
                return HtmlText.resolve(base, url);
            }
        }
        for (Element link : item.all("link")) {
            if ("enclosure".equals(link.attribute("rel")) && link.attribute("type").startsWith("audio")) {
                return HtmlText.resolve(base, link.attribute("href"));
            }
        }
        return "";
    }

    // ---------------------------------------------------------------- small helpers

    private static String childTextNs(Element e, String localName) {
        return e.children.stream().filter(c -> c.name.equals(localName)).map(Element::text).findFirst().orElse("");
    }

    /** {@code anna@example.org (Anna Berger)} becomes {@code Anna Berger}. */
    private static String rssAuthor(String author) {
        String a = HtmlText.inline(author);
        Matcher m = Pattern.compile("^\\S+@\\S+\\s*\\((.+)\\)$").matcher(a);
        return m.matches() ? m.group(1).strip() : a;
    }

    private static String language(String code) {
        if (code == null || code.isBlank()) return "";
        String c = code.strip().toLowerCase(Locale.ROOT);
        int dash = c.indexOf('-') >= 0 ? c.indexOf('-') : c.indexOf('_');
        return dash > 0 ? c.substring(0, dash) : c;
    }

    private static String first(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.strip();
        }
        return "";
    }

    private static String longest(String a, String b) {
        return a.strip().length() >= b.strip().length() ? a : b;
    }

    /** An id for entries without guid, id and link: title and date, hashed. */
    private static String made(String title, Instant published) {
        if (title.isEmpty()) return "";
        String key = title + "|" + (published == null ? "" : published.toString());
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(key.getBytes(StandardCharsets.UTF_8));
            return "sha1:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

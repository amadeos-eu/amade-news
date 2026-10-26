package eu.amadeos.apps.news.feed;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The bits of HTML that feeds carry, turned into plain text: tags removed, entities decoded, paragraphs kept as
 * empty lines. Not a full HTML parser; feeds only use simple markup in their descriptions.
 */
public final class HtmlText {
    private static final Pattern DROPPED = Pattern.compile(
            "(?is)<(script|style|noscript|iframe|object|svg|figcaption)\\b.*?</\\1\\s*>|<!--.*?-->");
    private static final Pattern BLOCK = Pattern.compile(
            "(?i)</?(p|div|section|article|blockquote|h[1-6]|ul|ol|table|tr|pre|figure|hr|header|footer)\\b[^>]*>");
    private static final Pattern BREAK = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern ITEM = Pattern.compile("(?i)<li\\b[^>]*>");
    private static final Pattern TAG = Pattern.compile("(?s)<[^>]*>");
    private static final Pattern ENTITY = Pattern.compile("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z][a-zA-Z0-9]{1,31});");
    private static final Pattern IMG = Pattern.compile("(?is)<img\\b([^>]*)>");
    private static final Pattern SRC = Pattern.compile("(?is)\\bsrc\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");
    private static final Pattern SIZE_ONE = Pattern.compile("(?is)\\b(width|height)\\s*=\\s*[\"']?[01][\"'\\s/>]");

    /** Named entities beyond the five of XML: the HTML 4 Latin-1, symbol and punctuation sets most feeds use. */
    static final Map<String, Integer> NAMED = new HashMap<>();

    static {
        String latin1 = "nbsp iexcl cent pound curren yen brvbar sect uml copy ordf laquo not shy reg macr deg plusmn "
                + "sup2 sup3 acute micro para middot cedil sup1 ordm raquo frac14 frac12 frac34 iquest Agrave Aacute "
                + "Acirc Atilde Auml Aring AElig Ccedil Egrave Eacute Ecirc Euml Igrave Iacute Icirc Iuml ETH Ntilde "
                + "Ograve Oacute Ocirc Otilde Ouml times Oslash Ugrave Uacute Ucirc Uuml Yacute THORN szlig agrave "
                + "aacute acirc atilde auml aring aelig ccedil egrave eacute ecirc euml igrave iacute icirc iuml eth "
                + "ntilde ograve oacute ocirc otilde ouml divide oslash ugrave uacute ucirc uuml yacute thorn yuml";
        String[] names = latin1.split(" ");
        for (int i = 0; i < names.length; i++) NAMED.put(names[i], 160 + i);
        Object[][] more = {
                {"amp", 38}, {"lt", 60}, {"gt", 62}, {"quot", 34}, {"apos", 39},
                {"OElig", 338}, {"oelig", 339}, {"Scaron", 352}, {"scaron", 353}, {"Yuml", 376}, {"fnof", 402},
                {"circ", 710}, {"tilde", 732}, {"ensp", 8194}, {"emsp", 8195}, {"thinsp", 8201}, {"zwnj", 8204},
                {"zwj", 8205}, {"lrm", 8206}, {"rlm", 8207}, {"ndash", 8211}, {"mdash", 8212}, {"lsquo", 8216},
                {"rsquo", 8217}, {"sbquo", 8218}, {"ldquo", 8220}, {"rdquo", 8221}, {"bdquo", 8222},
                {"dagger", 8224}, {"Dagger", 8225}, {"bull", 8226}, {"hellip", 8230}, {"permil", 8240},
                {"prime", 8242}, {"Prime", 8243}, {"lsaquo", 8249}, {"rsaquo", 8250}, {"euro", 8364},
                {"trade", 8482}, {"larr", 8592}, {"rarr", 8594}, {"uarr", 8593}, {"darr", 8595}, {"minus", 8722},
                {"le", 8804}, {"ge", 8805}, {"ne", 8800}, {"infin", 8734}, {"Alpha", 913}, {"Beta", 914},
                {"Gamma", 915}, {"Delta", 916}, {"alpha", 945}, {"beta", 946}, {"gamma", 947}, {"delta", 948},
                {"pi", 960}, {"mu", 956}, {"Omega", 937}, {"omega", 969}, {"Ccaron", 268}, {"ccaron", 269},
                {"Zcaron", 381}, {"zcaron", 382}, {"Lstrok", 321}, {"lstrok", 322}, {"nbhy", 8209},
        };
        for (Object[] e : more) NAMED.put((String) e[0], (Integer) e[1]);
    }

    private HtmlText() {
    }

    /** Plain text with paragraphs separated by one empty line; list items start with a bullet. */
    public static String toText(String html) {
        if (html == null || html.isBlank()) return "";
        String s = DROPPED.matcher(html).replaceAll(" ");
        s = BREAK.matcher(s).replaceAll("\n");
        s = ITEM.matcher(s).replaceAll("\n\n• ");
        s = BLOCK.matcher(s).replaceAll("\n\n");
        s = TAG.matcher(s).replaceAll("");
        s = decode(s);
        StringBuilder out = new StringBuilder();
        int blank = 0;
        for (String line : s.split("\n", -1)) {
            String clean = line.replace('\u00a0', ' ').replaceAll("[\\s&&[^\n]]+", " ").strip();
            if (clean.isEmpty()) {
                blank++;
                continue;
            }
            if (!out.isEmpty()) out.append(blank > 0 ? "\n\n" : "\n");
            out.append(clean);
            blank = 0;
        }
        return out.toString();
    }

    /** One line: tags removed, entities decoded, all white space one blank. For titles and names. */
    public static String inline(String html) {
        if (html == null) return "";
        String s = html;
        if (s.indexOf('<') >= 0) {
            s = DROPPED.matcher(s).replaceAll(" ");
            s = BLOCK.matcher(BREAK.matcher(s).replaceAll(" ")).replaceAll(" ");
            s = TAG.matcher(s).replaceAll("");
        }
        return decode(s).replace('\u00a0', ' ').replaceAll("\\s+", " ").strip();
    }

    /** Decodes numeric and named character references; unknown names stay as they are. */
    public static String decode(String s) {
        if (s == null || s.indexOf('&') < 0) return s == null ? "" : s;
        Matcher m = ENTITY.matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            Integer code = codeOf(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(code == null || !Character.isValidCodePoint(code)
                    ? m.group() : new String(Character.toChars(code))));
        }
        m.appendTail(out);
        return out.toString();
    }

    static Integer codeOf(String name) {
        if (name.startsWith("#x") || name.startsWith("#X")) return Integer.parseInt(name.substring(2), 16);
        if (name.startsWith("#")) return Integer.parseInt(name.substring(1));
        return NAMED.get(name);
    }

    /** The first picture in the HTML that is not a 1 × 1 tracking pixel, resolved against the base address. */
    public static Optional<String> firstImage(String html, String base) {
        if (html == null || html.indexOf('<') < 0) return Optional.empty();
        Matcher img = IMG.matcher(html);
        while (img.find()) {
            String attributes = img.group(1);
            if (SIZE_ONE.matcher(attributes).find()) continue;
            Matcher src = SRC.matcher(attributes);
            if (!src.find()) continue;
            String url = src.group(2) != null ? src.group(2) : src.group(3) != null ? src.group(3) : src.group(4);
            url = decode(url).strip();
            if (url.isEmpty() || url.startsWith("data:")) continue;
            return Optional.of(resolve(base, url));
        }
        return Optional.empty();
    }

    /** The address relative to the base; the address unchanged if that fails. */
    public static String resolve(String base, String url) {
        if (url == null || url.isBlank()) return "";
        try {
            if (base == null || base.isBlank()) return url.strip();
            return URI.create(base.strip()).resolve(url.strip().replace(" ", "%20")).toString();
        } catch (IllegalArgumentException e) {
            return url.strip();
        }
    }
}

package eu.amadeos.apps.news.catalog;

import eu.amadeos.apps.news.domain.Feed;
import eu.amadeos.commons.json.Json;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Europe's News: feeds of public broadcasters, news agencies and newspapers by country, from
 * {@code catalog/europe.json} in the program.
 */
public record Catalog(List<Country> countries) {
    public static final String RESOURCE = "/eu/amadeos/apps/news/catalog/europe.json";

    /** What kind of source a feed is; shown next to it. */
    public enum Kind {
        BROADCASTER, AGENCY, NEWSPAPER, INTERNATIONAL;

        static Kind of(String s) {
            return switch (s) {
                case "agency" -> AGENCY;
                case "newspaper" -> NEWSPAPER;
                case "international" -> INTERNATIONAL;
                default -> BROADCASTER;
            };
        }
    }

    /**
     * A country.
     *
     * @param code ISO 3166 alpha-2 in lower case ({@code xk} for Kosovo), also the flag's name
     * @param name the country's own name(s)
     */
    public record Country(String code, String name, List<Entry> feeds) {
        /** The name in the reader's language, e.g. {@code Germany} for {@code de}; the own name if Java has none. */
        public String localName(Locale locale) {
            String local = Locale.of("", code.toUpperCase(Locale.ROOT)).getDisplayCountry(locale);
            return local.isBlank() || local.equalsIgnoreCase(code) ? name : local;
        }
    }

    /** A feed of the catalog. */
    public record Entry(String title, String url, String language, Kind kind) {
        /** As a subscription in the folder, with the country's flag. */
        public Feed toFeed(String countryCode, String folder) {
            return new Feed(null, title, url, "", folder, countryCode, language);
        }
    }

    public static Catalog load() {
        try (InputStream in = Catalog.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IOException(RESOURCE + " missing");
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Catalog parse(String json) {
        Map<String, Object> root = Json.parseObject(json);
        List<Country> countries = new ArrayList<>();
        for (Object c : Json.list(root, "countries")) {
            List<Entry> feeds = new ArrayList<>();
            for (Object f : Json.list(c, "feeds")) {
                feeds.add(new Entry(Json.str(f, "title"), Json.str(f, "url"), Json.str(f, "language"),
                        Kind.of(Json.str(f, "kind"))));
            }
            countries.add(new Country(Json.str(c, "code"), Json.str(c, "name"), List.copyOf(feeds)));
        }
        return new Catalog(List.copyOf(countries));
    }

    /** The countries that have feeds, sorted by their name in the reader's language. */
    public List<Country> withFeeds(Locale locale) {
        Collator collator = Collator.getInstance(locale);
        collator.setStrength(Collator.PRIMARY);
        return countries.stream().filter(c -> !c.feeds().isEmpty())
                .sorted(Comparator.comparing(c -> c.localName(locale), collator)).toList();
    }

    public Optional<Country> country(String code) {
        return countries.stream().filter(c -> c.code().equals(code)).findFirst();
    }

    /**
     * The feeds of a country, those in the given languages first (in that order), the rest in catalog order.
     */
    public static List<Entry> ordered(Country country, List<String> languages) {
        List<Entry> list = new ArrayList<>(country.feeds());
        list.sort(Comparator.comparingInt(e -> {
            int i = languages.indexOf(e.language());
            return i < 0 ? languages.size() : i;
        }));
        return list;
    }

    public int feedCount() {
        return countries.stream().mapToInt(c -> c.feeds().size()).sum();
    }
}

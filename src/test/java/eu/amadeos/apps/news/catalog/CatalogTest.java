package eu.amadeos.apps.news.catalog;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogTest {
    private final Catalog catalog = Catalog.load();

    @Test
    void everyCountryHasAFlagAndEveryFeedIsHttpsAndUnique() {
        assertEquals(47, catalog.countries().size());
        Set<String> urls = new HashSet<>();
        for (Catalog.Country c : catalog.countries()) {
            assertNotNull(Catalog.class.getResource("/eu/amadeos/apps/news/flags/" + c.code() + ".png"), c.code());
            assertFalse(c.name().isBlank(), c.code());
            for (Catalog.Entry e : c.feeds()) {
                assertEquals("https", URI.create(e.url()).getScheme(), e.url());
                assertTrue(urls.add(e.url()), "twice: " + e.url());
                assertTrue(e.language().matches("[a-z]{2}"), e.title());
                assertFalse(e.title().isBlank());
            }
        }
        assertTrue(catalog.feedCount() >= 31);
    }

    @Test
    void countriesSortInTheReadersLanguage() {
        List<String> german = catalog.withFeeds(Locale.GERMAN).stream().map(c -> c.localName(Locale.GERMAN)).toList();
        assertTrue(german.indexOf("Österreich") < german.indexOf("Polen"), german.toString());
        assertTrue(german.indexOf("Tschechien") < german.indexOf("Ungarn"), german.toString());
        Catalog.Country de = catalog.country("de").orElseThrow();
        assertEquals("Germany", de.localName(Locale.ENGLISH));
        assertEquals("Deutschland", de.name());
    }

    @Test
    void myLanguagesFirst() {
        Catalog.Country de = catalog.country("de").orElseThrow();
        List<Catalog.Entry> english = Catalog.ordered(de, List.of("en"));
        assertEquals("en", english.getFirst().language());
        assertEquals("tagesschau", Catalog.ordered(de, List.of("de", "en")).getFirst().title());
        Catalog.Entry first = de.feeds().getFirst();
        assertEquals("de", first.toFeed("de", "Europe").country());
        assertEquals("Europe", first.toFeed("de", "Europe").folder());
    }
}

package eu.amadeos.apps.news.ui.dialogs;

import eu.amadeos.apps.news.catalog.Catalog;
import eu.amadeos.apps.news.domain.Settings;
import eu.amadeos.apps.news.ui.main.MainView;
import eu.amadeos.apps.news.update.FeedFinder;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopTheme;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static eu.amadeos.apps.news.Components.onEdt;
import static eu.amadeos.apps.news.TestTexts.DE;
import static eu.amadeos.apps.news.TestTexts.EN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Add Feed, Europe's News and Preferences, built but not shown. Needs a display. */
class DialogsTest {
    private static final FeedFinder.Candidate POSTS = new FeedFinder.Candidate("https://blog.example.org/feed.xml",
            "Posts", "https://blog.example.org/", "en", "RSS", 12, Instant.parse("2026-10-25T10:00:00Z"));
    private static final FeedFinder.Candidate COMMENTS = new FeedFinder.Candidate(
            "https://blog.example.org/comments.xml", "Comments", "https://blog.example.org/", "en", "Atom", 40, null);

    @BeforeEach
    void display() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        onEdt(() -> AmadeUI.install(DesktopTheme.WARM_MOTIF));
    }

    @Test
    void addFeedListsTheFoundFeedsAndReturnsTheChoice() throws Exception {
        onEdt(() -> {
            AddFeedDialog dialog = new AddFeedDialog(null, EN, "blog.example.org", List.of("Friends"),
                    input -> CompletableFuture.completedFuture(List.of(POSTS, COMMENTS)));
            try {
                assertEquals("Found 2 feeds:", dialog.statusText());
                assertEquals(2, dialog.radios().size());
                assertTrue(dialog.radios().getFirst().isSelected());
                assertEquals("Posts", dialog.nameField().getText());
                assertTrue(dialog.subscribeButton().isEnabled());
                dialog.radios().get(1).doClick();
                assertEquals("Comments", dialog.nameField().getText());
                dialog.nameField().setText("Blog comments");
                dialog.folderBox().setSelectedItem("Friends");
                dialog.pressSubscribe();
                MainView.AddFeedChoice choice = dialog.choice().orElseThrow();
                assertEquals(COMMENTS, choice.candidate());
                assertEquals("Blog comments", choice.title());
                assertEquals("Friends", choice.folder());
            } finally {
                dialog.dispose();
            }
        });
    }

    @Test
    void addFeedTellsWhyNothingWasFound() throws Exception {
        onEdt(() -> {
            AddFeedDialog dialog = new AddFeedDialog(null, EN, "", List.of(),
                    input -> CompletableFuture.failedFuture(new IllegalStateException("No connection to the server.")));
            try {
                assertFalse(dialog.subscribeButton().isEnabled());
                dialog.addressField().setText("nowhere.example.org");
                dialog.search();
                assertEquals("No connection to the server.", dialog.statusText());
                assertTrue(dialog.radios().isEmpty());
                assertFalse(dialog.subscribeButton().isEnabled());
                dialog.folderBox().setSelectedIndex(0);
                assertEquals(Optional.empty(), dialog.choice());
            } finally {
                dialog.dispose();
            }
        });
    }

    @Test
    void theCatalogStartsAtTheReadersCountryAndCountsTheTickedFeeds() throws Exception {
        onEdt(() -> {
            Catalog catalog = Catalog.load();
            CatalogDialog dialog = new CatalogDialog(null, DE, catalog, Set.of("https://www.tagesschau.de/xml/rss2/"),
                    List.of("de", "en"));
            try {
                assertEquals("de", dialog.countryList().getSelectedValue().code());
                assertTrue(dialog.boxes().getFirst().isSelected());
                assertEquals("1 Feeds aus 1 Ländern gewählt", dialog.summaryText());
                dialog.boxes().get(1).doClick();
                assertEquals("2 Feeds aus 1 Ländern gewählt", dialog.summaryText());
                dialog.filterField().setText("fran");
                assertEquals("fr", dialog.countryList().getSelectedValue().code());
                assertEquals(1, dialog.countryList().getModel().getSize());
                dialog.boxes().getFirst().doClick();
                assertEquals("3 Feeds aus 2 Ländern gewählt", dialog.summaryText());
                assertEquals(3, dialog.chosen().size());
            } finally {
                dialog.dispose();
            }
        });
    }

    @Test
    void englishFeedsComeFirstForAnEnglishReader() throws Exception {
        onEdt(() -> {
            CatalogDialog dialog = new CatalogDialog(null, EN, Catalog.load(), Set.of(), List.of("en"));
            try {
                dialog.filterField().setText("france");
                assertEquals("0 feeds from 0 countries chosen", dialog.summaryText());
                dialog.boxes().getFirst().doClick();
                assertEquals(Set.of("https://www.france24.com/en/rss"), dialog.chosen());
                assertEquals("France", dialog.headingText());
            } finally {
                dialog.dispose();
            }
        });
    }

    @Test
    void preferencesShowTheSettingsAndReturnTheChangedOnes() throws Exception {
        onEdt(() -> {
            Settings current = new Settings(60, true, 30, 15, true, true);
            PreferencesDialog dialog = new PreferencesDialog(null, EN, current);
            try {
                assertEquals("every hour", dialog.updateBox().getSelectedItem());
                assertEquals("Large", dialog.textSizeBox().getSelectedItem());
                assertEquals(current, dialog.settings(current));
                dialog.updateBox().setSelectedIndex(4);
                assertEquals("only by hand", dialog.updateBox().getSelectedItem());
                dialog.picturesBox().setSelected(false);
                assertEquals(new Settings(0, true, 30, 15, false, true), dialog.settings(current));
            } finally {
                dialog.dispose();
            }
        });
    }

    @Test
    void anOddValueFromAHandEditedFileShowsTheNearestChoice() throws Exception {
        onEdt(() -> {
            Settings odd = new Settings(45, false, 20, 12, false, false);
            PreferencesDialog dialog = new PreferencesDialog(null, EN, odd);
            try {
                Settings shown = dialog.settings(odd);
                assertEquals(30, shown.updateMinutes());
                assertEquals(14, shown.keepDays());
                assertEquals(11, shown.textSize());
            } finally {
                dialog.dispose();
            }
        });
    }
}

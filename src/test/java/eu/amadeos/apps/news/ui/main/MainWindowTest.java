package eu.amadeos.apps.news.ui.main;

import eu.amadeos.apps.news.Scene;
import eu.amadeos.apps.news.images.ImageCache;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopTheme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JLabel;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.List;

import static eu.amadeos.apps.news.Components.find;
import static eu.amadeos.apps.news.Components.onEdt;
import static eu.amadeos.apps.news.TestTexts.DE;
import static eu.amadeos.apps.news.TestTexts.EN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The real main window on the test scene. Needs a display. */
class MainWindowTest {
    @TempDir
    Path dir;

    private MainWindow window;
    private MainPresenter presenter;

    @BeforeEach
    void display() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
    }

    /** Opens the window as the program does; the network work runs straight away on the EDT. */
    private void open(Scene scene, Messages msg) throws Exception {
        onEdt(() -> {
            AmadeUI.install(DesktopTheme.WARM_MOTIF);
            ImageCache images = new ImageCache(scene.http, dir.resolve("images"), Runnable::run, Runnable::run,
                    scene.clock);
            window = new MainWindow(msg, null, images);
            presenter = new MainPresenter(window, msg, null, scene.services());
            window.bind(presenter);
            window.setVisible(true);
            presenter.start(List.of());
        });
    }

    @AfterEach
    void close() throws Exception {
        if (window != null) onEdt(window::dispose);
    }

    @Test
    void theFirstStartWelcomesWithLinksToTheCatalogAndAddFeed() throws Exception {
        open(new Scene(dir), EN);
        onEdt(() -> {
            assertEquals("News", window.getTitle());
            assertTrue(window.reading().showsPlaceholder());
            assertTrue(window.reading().placeholderText().contains("Choose from Europe's News"));
            assertEquals(4, window.sourceList().getModel().getSize());
            assertFalse(window.menu().item("menu.article.open").isEnabled());
        });
    }

    @Test
    void choosingAnArticleShowsItAndMarksItRead() throws Exception {
        open(new Scene(dir).withFeeds(), EN);
        onEdt(() -> {
            assertEquals("News (5)", window.getTitle());
            assertEquals(5, window.articleList().getModel().getSize());
            assertTrue(window.statusText().endsWith("Updated at 08:00: 5 new"), window.statusText());
            window.articleList().setSelectedIndex(0);
        });
        onEdt(() -> {
            assertEquals("Summit ends with a deal", window.reading().titleText());
            assertTrue(window.reading().bodyText().contains("Leaders agreed late at night."));
            assertTrue(window.reading().metaText().contains("World News"));
            assertEquals("News (4)", window.getTitle());
            assertTrue(window.menu().item("menu.article.open").isEnabled());
            assertEquals("Star", window.button("star").getText());
            presenter.toggleStar();
        });
        onEdt(() -> {
            assertEquals("Unstar", window.button("star").getText());
            assertEquals("Summit ends with a deal", window.reading().titleText(), "the article stays open");
        });
    }

    @Test
    void theSourceListGroupsFeedsInFoldersAndTheSearchFilters() throws Exception {
        open(new Scene(dir).withFeeds(), EN);
        onEdt(() -> {
            assertEquals(7, window.sourceList().getModel().getSize());
            assertEquals("Science", window.sourceList().getModel().getElementAt(5).label());
            window.sourceList().setSelectedIndex(5);
        });
        onEdt(() -> {
            assertEquals(2, window.articleList().getModel().getSize());
            window.searchField().setText("comet");
        });
        onEdt(() -> {
            assertEquals(1, window.articleList().getModel().getSize());
            assertEquals("1 found for \"comet\"", find(window, "status", JLabel.class).getText());
        });
    }

    @Test
    void theWindowSpeaksGerman() throws Exception {
        open(new Scene(dir).withFeeds(), DE);
        onEdt(() -> {
            assertEquals("Nachrichten (5)", window.getTitle());
            assertEquals("Alle Artikel", window.sourceList().getModel().getElementAt(0).label());
            assertEquals("Nachrichten", window.getJMenuBar().getMenu(0).getText());
        });
    }
}

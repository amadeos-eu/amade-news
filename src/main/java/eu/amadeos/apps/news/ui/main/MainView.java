package eu.amadeos.apps.news.ui.main;

import eu.amadeos.apps.news.catalog.Catalog;
import eu.amadeos.apps.news.domain.Settings;
import eu.amadeos.apps.news.update.FeedFinder;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** The window as the presenter sees it. All methods run on the EDT. */
public interface MainView {
    void showSettings(Settings settings);

    /** The list on the left and which line is selected. */
    void showSources(List<SourceItem> sources, int selected);

    /** The articles of the selected line that match the search; the selected one, if any. */
    void showArticles(List<ArticleRow> rows, Optional<String> selectedKey, String status);

    /** The reading pane: one article, or a text with links ({@code action:add}, {@code action:catalog}) when none. */
    void showArticle(Optional<ArticleRow> row, String placeholderHtml);

    /** Unread articles in all feeds, for the window title. */
    void showUnreadTotal(int unread);

    /** True while feeds are being updated; the text goes into the status line. */
    void showUpdating(boolean running, String status);

    /** The Add Feed dialog. The finder runs in the background; empty if cancelled. */
    Optional<AddFeedChoice> addFeed(String address, List<String> folders,
                                    Function<String, CompletableFuture<List<FeedFinder.Candidate>>> finder);

    /** Europe's News: the addresses that should be subscribed when the dialog closes; empty if cancelled. */
    Optional<Set<String>> chooseFromCatalog(Catalog catalog, Set<String> subscribed, List<String> languages);

    Optional<Settings> editPreferences(Settings settings);

    /** A line of text, e.g. a new name; empty if cancelled. */
    Optional<String> askText(String title, String label, String initial);

    /** A folder for the feed: one of the existing ones, a new name, or "" for none; empty if cancelled. */
    Optional<String> askFolder(String feedTitle, List<String> folders, String current);

    boolean confirm(String title, String message);

    Optional<Path> chooseImportFile();

    Optional<Path> chooseExportFile(String suggestedName);

    void copyText(String text);

    void focusSearch();

    void showError(String message);

    void showInfo(String message);

    void showAbout();

    void close();

    /**
     * What the user chose in Add Feed.
     *
     * @param folder "" for none
     */
    record AddFeedChoice(FeedFinder.Candidate candidate, String title, String folder) {
    }
}

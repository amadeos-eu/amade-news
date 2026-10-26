package eu.amadeos.apps.news.ui.main;

import eu.amadeos.apps.news.catalog.Catalog;
import eu.amadeos.apps.news.domain.Settings;
import eu.amadeos.apps.news.update.FeedFinder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Records what the presenter shows and answers its questions as the test set them up. */
class FakeView implements MainView {
    Settings settings;
    List<SourceItem> sources = List.of();
    int selectedSource;
    List<ArticleRow> articles = List.of();
    Optional<String> selectedArticle = Optional.empty();
    String status = "";
    Optional<ArticleRow> article = Optional.empty();
    String placeholder = "";
    int unread;
    boolean updating;
    String updateStatus = "";
    final List<String> errors = new ArrayList<>();
    final List<String> infos = new ArrayList<>();
    final List<String> copied = new ArrayList<>();
    final List<String> confirmations = new ArrayList<>();
    boolean closed;
    boolean searchFocused;
    boolean aboutShown;

    /** What the Add Feed dialog answers; the finder it was given, for the test to call. */
    Function<String, Optional<AddFeedChoice>> addFeedAnswer = a -> Optional.empty();
    Function<String, CompletableFuture<List<FeedFinder.Candidate>>> finder;
    List<String> addFeedFolders;
    Optional<Set<String>> catalogAnswer = Optional.empty();
    Set<String> catalogSubscribed;
    List<String> catalogLanguages;
    Optional<Settings> preferencesAnswer = Optional.empty();
    Optional<String> textAnswer = Optional.empty();
    Optional<String> folderAnswer = Optional.empty();
    boolean confirmAnswer = true;
    Optional<Path> importFile = Optional.empty();
    Optional<Path> exportFile = Optional.empty();

    @Override
    public void showSettings(Settings settings) {
        this.settings = settings;
    }

    @Override
    public void showSources(List<SourceItem> sources, int selected) {
        this.sources = sources;
        this.selectedSource = selected;
    }

    @Override
    public void showArticles(List<ArticleRow> rows, Optional<String> selectedKey, String status) {
        this.articles = rows;
        this.selectedArticle = selectedKey;
        this.status = status;
    }

    @Override
    public void showArticle(Optional<ArticleRow> row, String placeholderHtml) {
        this.article = row;
        this.placeholder = placeholderHtml;
    }

    @Override
    public void showUnreadTotal(int unread) {
        this.unread = unread;
    }

    @Override
    public void showUpdating(boolean running, String status) {
        this.updating = running;
        this.updateStatus = status;
    }

    @Override
    public Optional<AddFeedChoice> addFeed(String address, List<String> folders,
                                           Function<String, CompletableFuture<List<FeedFinder.Candidate>>> finder) {
        this.finder = finder;
        this.addFeedFolders = folders;
        return addFeedAnswer.apply(address);
    }

    @Override
    public Optional<Set<String>> chooseFromCatalog(Catalog catalog, Set<String> subscribed, List<String> languages) {
        catalogSubscribed = subscribed;
        catalogLanguages = languages;
        return catalogAnswer;
    }

    @Override
    public Optional<Settings> editPreferences(Settings settings) {
        return preferencesAnswer;
    }

    @Override
    public Optional<String> askText(String title, String label, String initial) {
        return textAnswer;
    }

    @Override
    public Optional<String> askFolder(String feedTitle, List<String> folders, String current) {
        return folderAnswer;
    }

    @Override
    public boolean confirm(String title, String message) {
        confirmations.add(message);
        return confirmAnswer;
    }

    @Override
    public Optional<Path> chooseImportFile() {
        return importFile;
    }

    @Override
    public Optional<Path> chooseExportFile(String suggestedName) {
        return exportFile;
    }

    @Override
    public void copyText(String text) {
        copied.add(text);
    }

    @Override
    public void focusSearch() {
        searchFocused = true;
    }

    @Override
    public void showError(String message) {
        errors.add(message);
    }

    @Override
    public void showInfo(String message) {
        infos.add(message);
    }

    @Override
    public void showAbout() {
        aboutShown = true;
    }

    @Override
    public void close() {
        closed = true;
    }

    List<String> sourceLabels() {
        return sources.stream().map(SourceItem::label).toList();
    }

    List<String> titles() {
        return articles.stream().map(r -> r.article().title()).toList();
    }

    SourceItem source(String label) {
        return sources.stream().filter(s -> s.label().equals(label)).findFirst().orElseThrow();
    }
}

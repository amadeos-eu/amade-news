package eu.amadeos.apps.news.ui.main;

import eu.amadeos.apps.news.catalog.Catalog;
import eu.amadeos.apps.news.domain.Article;
import eu.amadeos.apps.news.domain.Feed;
import eu.amadeos.apps.news.domain.Settings;
import eu.amadeos.apps.news.domain.Source;
import eu.amadeos.apps.news.feed.ParsedFeed;
import eu.amadeos.apps.news.store.ArticleStore;
import eu.amadeos.apps.news.store.FeedState;
import eu.amadeos.apps.news.store.NewsPreferences;
import eu.amadeos.apps.news.store.Opml;
import eu.amadeos.apps.news.store.Subscriptions;
import eu.amadeos.apps.news.update.FeedFinder;
import eu.amadeos.apps.news.update.FeedUpdater;
import eu.amadeos.commons.desktop.DesktopLauncher;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Everything the window does, without Swing: which articles the list shows, reading, starring, subscribing,
 * Europe's News, OPML, and updating the feeds in the background every few minutes.
 */
public class MainPresenter {
    private static final Logger LOG = LoggerFactory.getLogger(MainPresenter.class);
    /** Feeds fetched at the same time. */
    static final int PARALLEL = 4;

    /** What the presenter works with, so tests can pass their own. */
    public record Services(Subscriptions subscriptions, ArticleStore store, NewsPreferences preferences,
                           FeedUpdater updater, FeedFinder finder, Catalog catalog, DesktopLauncher launcher,
                           Executor background, Executor ui, Clock clock, ZoneId zone, Runnable exit) {
    }

    private final MainView view;
    private final Messages msg;
    private final LocaleSettings locales;
    private final Services s;
    private Settings settings = Settings.DEFAULT;
    private Source source = new Source.All();
    private String query = "";
    private List<ArticleRow> shown = List.of();
    private String selected;
    private boolean updating;
    private Instant lastUpdate;
    private final Map<String, String> errors = new HashMap<>();

    public MainPresenter(MainView view, Messages msg, LocaleSettings locales, Services services) {
        this.view = view;
        this.msg = msg;
        this.locales = locales;
        this.s = services;
    }

    /**
     * Reads subscriptions and articles and shows them; an address or OPML file from the command line is offered
     * for subscribing or imported.
     */
    public void start(List<String> args) {
        settings = s.preferences().settings();
        view.showSettings(settings);
        try {
            s.subscriptions().load();
        } catch (IOException e) {
            view.showError(msg.format("error.subscriptions", e.getMessage()));
        }
        s.store().load(s.subscriptions().feeds().stream().map(Feed::id).toList());
        s.subscriptions().feeds().forEach(f -> rememberError(f.id(), s.store().state(f.id())));
        refresh();
        for (String arg : args) {
            if (arg.toLowerCase(java.util.Locale.ROOT).endsWith(".opml") && Files.exists(Path.of(arg))) {
                importOpml(Path.of(arg));
            } else {
                addFeed(arg);
            }
        }
        if (settings.updateAtStart() && !s.subscriptions().feeds().isEmpty()) updateAll();
    }

    /** Called every minute by the window: updates all feeds when the time has come. */
    public void tick() {
        if (settings.updateMinutes() <= 0 || updating || s.subscriptions().feeds().isEmpty()) return;
        Instant now = s.clock().instant();
        if (lastUpdate == null || !now.isBefore(lastUpdate.plus(Duration.ofMinutes(settings.updateMinutes())))) {
            updateAll();
        }
    }

    // ---------------------------------------------------------------- what the lists show

    public void selectSource(Source newSource) {
        if (newSource == null || newSource.equals(source)) return;
        source = newSource;
        selected = null;
        refresh();
    }

    public void search(String text) {
        String q = text == null ? "" : text.strip();
        if (q.equals(query)) return;
        query = q;
        refresh();
    }

    public void selectArticle(String key) {
        if (key == null || key.equals(selected)) return;
        selected = key;
        Optional<ArticleRow> row = row(key);
        if (row.isPresent() && settings.markReadOnShow() && !row.get().article().read()) {
            change(a -> a.key().equals(key), a -> a.withRead(true));
            return;
        }
        view.showArticle(row, "");
    }

    private Optional<ArticleRow> row(String key) {
        return shown.stream().filter(r -> r.key().equals(key)).findFirst();
    }

    private Optional<Article> current() {
        return row(selected == null ? "" : selected).map(ArticleRow::article);
    }

    /** Shows the lists again from the store, keeping the selection where possible. */
    void refresh() {
        List<Feed> feeds = s.subscriptions().feeds();
        Map<String, Feed> byId = new HashMap<>();
        feeds.forEach(f -> byId.put(f.id(), f));
        List<Article> all = s.store().all().stream().filter(a -> byId.containsKey(a.feedId())).toList();

        List<SourceItem> items = sources(feeds, all);
        if (items.stream().noneMatch(i -> i.source().equals(source))) source = new Source.All();
        int index = 0;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).source().equals(source)) index = i;
        }
        view.showSources(items, index);
        view.showUnreadTotal((int) all.stream().filter(a -> !a.read()).count());

        Source current = source;
        List<ArticleRow> rows = new ArrayList<>();
        for (Article a : all) {
            Feed feed = byId.get(a.feedId());
            // Unread keeps showing the article being read, so it does not vanish while it is open.
            boolean keep = current instanceof Source.Unread && a.key().equals(selected);
            if ((current.includes(a, feed) || keep) && a.matches(query)) rows.add(row(a, feed));
        }
        shown = List.copyOf(rows);
        if (selected != null && row(selected).isEmpty()) selected = null;
        view.showArticles(shown, Optional.ofNullable(selected), status(all));
        if (selected != null) {
            view.showArticle(row(selected), "");
        } else {
            view.showArticle(Optional.empty(), placeholder(feeds));
        }
    }

    private String status(List<Article> all) {
        long unread = shown.stream().filter(r -> !r.article().read()).count();
        if (!query.isEmpty()) return msg.format("status.found", shown.size(), query);
        return msg.format("status.articles", shown.size(), unread);
    }

    private String placeholder(List<Feed> feeds) {
        if (feeds.isEmpty()) return msg.get("welcome.html");
        if (shown.isEmpty() && !query.isEmpty()) return "<p>" + html(msg.format("placeholder.nothing-found", query)) + "</p>";
        if (shown.isEmpty()) return "<p>" + html(msg.get("placeholder.empty")) + "</p>";
        return "<p>" + html(msg.get("placeholder.choose")) + "</p>";
    }

    private List<SourceItem> sources(List<Feed> feeds, List<Article> all) {
        List<SourceItem> items = new ArrayList<>();
        items.add(new SourceItem(new Source.All(), msg.get("source.all"), unread(all, new Source.All(), null), "", false, ""));
        items.add(new SourceItem(new Source.Unread(), msg.get("source.unread"), unread(all, new Source.All(), null), "", false, ""));
        items.add(new SourceItem(new Source.Starred(), msg.get("source.starred"), count(all, new Source.Starred()), "", false, ""));
        items.add(new SourceItem(new Source.Later(), msg.get("source.later"), count(all, new Source.Later()), "", false, ""));
        for (Feed f : feeds) {
            if (f.folder().isEmpty()) items.add(feedItem(f, all, false));
        }
        for (String folder : s.subscriptions().folders()) {
            Source.Folder fs = new Source.Folder(folder);
            int unread = (int) all.stream().filter(a -> !a.read() && feeds.stream()
                    .anyMatch(f -> f.id().equals(a.feedId()) && f.folder().equals(folder))).count();
            items.add(new SourceItem(fs, folder, unread, "", false, ""));
            for (Feed f : feeds) {
                if (f.folder().equals(folder)) items.add(feedItem(f, all, true));
            }
        }
        return items;
    }

    private SourceItem feedItem(Feed f, List<Article> all, boolean indent) {
        return new SourceItem(new Source.OneFeed(f.id()), f.title(), unread(all, new Source.OneFeed(f.id()), f),
                f.country(), indent, errors.getOrDefault(f.id(), ""));
    }

    private static int unread(List<Article> all, Source source, Feed feed) {
        return (int) all.stream().filter(a -> !a.read() && source.includes(a, feed)).count();
    }

    private static int count(List<Article> all, Source source) {
        return (int) all.stream().filter(a -> source.includes(a, null)).count();
    }

    private ArticleRow row(Article a, Feed feed) {
        return new ArticleRow(a, feed == null ? "" : feed.title(), feed == null ? "" : feed.country(), when(a.published()),
                fullDate(a.published()));
    }

    /** {@code 07:12} today, {@code yesterday 22:40}, {@code Sat 22:40} this week, {@code 24 Oct} this year. */
    String when(Instant instant) {
        ZonedDateTime t = instant.atZone(s.zone());
        LocalDate today = LocalDate.now(s.clock().withZone(s.zone()));
        String time = DateTimeFormatter.ofPattern("HH:mm", msg.locale()).format(t);
        LocalDate day = t.toLocalDate();
        if (day.equals(today)) return time;
        if (day.equals(today.minusDays(1))) return msg.format("time.yesterday", time);
        if (day.isAfter(today.minusDays(7)) && day.isBefore(today)) {
            return DateTimeFormatter.ofPattern("EEE HH:mm", msg.locale()).format(t);
        }
        if (day.getYear() == today.getYear()) return DateTimeFormatter.ofPattern("d MMM", msg.locale()).format(t);
        return DateTimeFormatter.ofPattern("d MMM yyyy", msg.locale()).format(t);
    }

    String fullDate(Instant instant) {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.SHORT).withLocale(msg.locale())
                .format(instant.atZone(s.zone()));
    }

    // ---------------------------------------------------------------- moving through the articles

    public void next() {
        move(1, false);
    }

    public void previous() {
        move(-1, false);
    }

    /** The next unread article after the selected one, in this list. */
    public void nextUnread() {
        move(1, true);
    }

    private void move(int step, boolean unreadOnly) {
        if (shown.isEmpty()) return;
        int at = -1;
        for (int i = 0; i < shown.size(); i++) {
            if (shown.get(i).key().equals(selected)) at = i;
        }
        for (int i = at + step; i >= 0 && i < shown.size(); i += step) {
            if (!unreadOnly || !shown.get(i).article().read()) {
                String key = shown.get(i).key();
                selectArticle(key);
                view.showArticles(shown, Optional.of(key), status(s.store().all()));
                return;
            }
        }
    }

    // ---------------------------------------------------------------- the selected article

    public void toggleStar() {
        current().ifPresent(a -> change(x -> x.key().equals(a.key()), x -> x.withStarred(!a.starred())));
    }

    public void toggleLater() {
        current().ifPresent(a -> change(x -> x.key().equals(a.key()), x -> x.withLater(!a.later())));
    }

    public void toggleRead() {
        current().ifPresent(a -> change(x -> x.key().equals(a.key()), x -> x.withRead(!a.read())));
    }

    /** Every article in the list is marked read. */
    public void markAllRead() {
        Set<String> keys = new HashSet<>();
        shown.forEach(r -> keys.add(r.key()));
        change(a -> keys.contains(a.key()), a -> a.withRead(true));
    }

    public void openInWeb() {
        current().filter(a -> !a.link().isEmpty()).ifPresent(a -> browse(a.link()));
    }

    public void copyLink() {
        current().filter(a -> !a.link().isEmpty()).ifPresent(a -> view.copyText(a.link()));
    }

    /** A link clicked in the reading pane: the actions of the welcome text, or a web address. */
    public void linkClicked(String url) {
        switch (url) {
            case "action:add" -> addFeed("");
            case "action:catalog" -> openCatalog();
            case "action:import" -> importOpml();
            default -> browse(url);
        }
    }

    private void browse(String url) {
        try {
            s.launcher().browse(url);
        } catch (IOException e) {
            view.showError(msg.format("error.browse", url, e.getMessage()));
        }
    }

    private void change(java.util.function.Predicate<Article> which, java.util.function.UnaryOperator<Article> how) {
        try {
            s.store().update(which, how);
        } catch (UncheckedIOException e) {
            view.showError(msg.format("error.save", e.getCause().getMessage()));
        }
        refresh();
    }

    // ---------------------------------------------------------------- updating

    public boolean updating() {
        return updating;
    }

    /** Fetches every feed in the background, {@value #PARALLEL} at a time. */
    public void updateAll() {
        update(s.subscriptions().feeds());
    }

    /** Fetches the feed selected on the left, or the feeds of the selected folder. */
    public void updateSelected() {
        List<Feed> feeds = s.subscriptions().feeds().stream().filter(f -> switch (source) {
            case Source.OneFeed one -> one.feedId().equals(f.id());
            case Source.Folder folder -> folder.name().equals(f.folder());
            default -> true;
        }).toList();
        update(feeds);
    }

    private void update(List<Feed> feeds) {
        if (updating || feeds.isEmpty()) return;
        updating = true;
        int keepDays = settings.keepDays();
        AtomicInteger done = new AtomicInteger();
        AtomicInteger added = new AtomicInteger();
        List<String> failed = java.util.Collections.synchronizedList(new ArrayList<>());
        Semaphore slots = new Semaphore(PARALLEL);
        view.showUpdating(true, msg.format("status.updating", 0, feeds.size()));
        for (Feed feed : feeds) {
            s.background().execute(() -> {
                FeedUpdater.Result result;
                try {
                    slots.acquire();
                    try {
                        result = s.updater().update(feed, keepDays);
                    } finally {
                        slots.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    result = new FeedUpdater.Result(feed.id(), 0, "interrupted", Optional.empty());
                } catch (RuntimeException e) {
                    LOG.warn("Updating {} failed", feed.url(), e);
                    result = new FeedUpdater.Result(feed.id(), 0, String.valueOf(e.getMessage()), Optional.empty());
                }
                FeedUpdater.Result r = result;
                s.ui().execute(() -> {
                    finished(feed, r);
                    added.addAndGet(r.added());
                    if (r.failed()) failed.add(feed.title());
                    int n = done.incrementAndGet();
                    if (n < feeds.size()) {
                        view.showUpdating(true, msg.format("status.updating", n, feeds.size()));
                    } else {
                        allFinished(added.get(), failed);
                    }
                });
            });
        }
    }

    private void finished(Feed feed, FeedUpdater.Result r) {
        rememberError(feed.id(), s.store().state(feed.id()));
        // A feed added by address alone gets the name and website the feed gives.
        r.parsed().ifPresent(p -> adopt(feed, p));
    }

    private void adopt(Feed feed, ParsedFeed parsed) {
        Optional<Feed> now = s.subscriptions().feed(feed.id());
        if (now.isEmpty()) return;
        Feed f = now.get();
        boolean nameless = f.title().equals(Feed.hostOf(f.url())) && !parsed.title().isEmpty();
        boolean siteless = f.siteUrl().isEmpty() && !parsed.siteUrl().isEmpty();
        if (!nameless && !siteless) return;
        try {
            s.subscriptions().change(f.id(), x -> {
                Feed y = nameless ? x.withTitle(parsed.title()) : x;
                return siteless ? y.withSiteUrl(parsed.siteUrl()) : y;
            });
        } catch (UncheckedIOException e) {
            LOG.warn("Cannot save the subscriptions", e);
        }
    }

    private void allFinished(int added, List<String> failed) {
        updating = false;
        lastUpdate = s.clock().instant();
        refresh();
        String time = DateTimeFormatter.ofPattern("HH:mm", msg.locale()).format(lastUpdate.atZone(s.zone()));
        String text = failed.isEmpty() ? msg.format("status.updated", time, added)
                : msg.format("status.updated-failed", time, added, failed.size());
        view.showUpdating(false, text);
    }

    private void rememberError(String feedId, FeedState state) {
        if (state.failed()) {
            errors.put(feedId, errorText(state.error()));
        } else {
            errors.remove(feedId);
        }
    }

    /** An error code from the updater or the finder in words. */
    String errorText(String code) {
        if (msg.has("error." + code)) return msg.get("error." + code);
        if (code.startsWith("http-")) return msg.format("error.http", code.substring(5));
        return msg.format("error.other", code);
    }

    // ---------------------------------------------------------------- subscribing

    public void addFeed() {
        addFeed("");
    }

    public void addFeed(String address) {
        Optional<MainView.AddFeedChoice> choice = view.addFeed(address, s.subscriptions().folders(), this::find);
        if (choice.isEmpty()) return;
        MainView.AddFeedChoice c = choice.get();
        if (s.subscriptions().byUrl(c.candidate().url()).isPresent()) {
            view.showInfo(msg.format("info.already", c.title()));
            return;
        }
        Feed feed = new Feed(null, c.title(), c.candidate().url(), c.candidate().siteUrl(), c.folder(), "",
                c.candidate().language());
        subscribe(List.of(feed));
        source = new Source.OneFeed(feed.id());
        refresh();
    }

    /** For the Add Feed dialog: looks in the background, answers on the EDT. */
    CompletableFuture<List<FeedFinder.Candidate>> find(String input) {
        CompletableFuture<List<FeedFinder.Candidate>> result = new CompletableFuture<>();
        s.background().execute(() -> {
            try {
                List<FeedFinder.Candidate> found = s.finder().find(input);
                s.ui().execute(() -> result.complete(found));
            } catch (FeedFinder.NotFound e) {
                String text = e.reason().equals("none") ? msg.get("add.none") : errorText(e.reason());
                s.ui().execute(() -> result.completeExceptionally(new IllegalStateException(text)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                s.ui().execute(() -> result.cancel(true));
            }
        });
        return result;
    }

    private void subscribe(List<Feed> feeds) {
        List<Feed> added;
        try {
            added = s.subscriptions().add(feeds);
        } catch (UncheckedIOException e) {
            view.showError(msg.format("error.save", e.getCause().getMessage()));
            return;
        }
        refresh();
        if (!added.isEmpty()) update(added);
    }

    /** Europe's News: subscribes what was ticked, unsubscribes what was unticked. */
    public void openCatalog() {
        Set<String> subscribed = new HashSet<>();
        s.subscriptions().feeds().forEach(f -> subscribed.add(f.url()));
        Optional<Set<String>> chosen = view.chooseFromCatalog(s.catalog(), Set.copyOf(subscribed), languages());
        if (chosen.isEmpty()) return;
        List<Feed> add = new ArrayList<>();
        String folder = msg.get("folder.europe");
        for (Catalog.Country country : s.catalog().countries()) {
            for (Catalog.Entry e : country.feeds()) {
                if (chosen.get().contains(e.url()) && !subscribed.contains(e.url())) {
                    add.add(e.toFeed(country.code(), folder));
                } else if (!chosen.get().contains(e.url()) && subscribed.contains(e.url())) {
                    s.subscriptions().byUrl(e.url()).ifPresent(f -> remove(f.id()));
                }
            }
        }
        subscribe(add);
    }

    /** The reader's language, then English: catalog feeds in these come first. */
    List<String> languages() {
        LinkedHashSet<String> list = new LinkedHashSet<>();
        list.add(msg.locale().getLanguage());
        list.add("en");
        return List.copyOf(list);
    }

    public void renameFeed(String feedId) {
        s.subscriptions().feed(feedId).ifPresent(f -> view.askText(msg.get("dialog.rename.title"),
                msg.get("dialog.rename.label"), f.title()).filter(t -> !t.isBlank())
                .ifPresent(t -> saveChange(() -> s.subscriptions().change(feedId, x -> x.withTitle(t)))));
    }

    public void moveFeed(String feedId) {
        s.subscriptions().feed(feedId).ifPresent(f -> view.askFolder(f.title(), s.subscriptions().folders(), f.folder())
                .ifPresent(folder -> saveChange(() -> s.subscriptions().change(feedId, x -> x.withFolder(folder)))));
    }

    public void renameFolder(String folder) {
        view.askText(msg.get("dialog.rename-folder.title"), msg.get("dialog.rename-folder.label"), folder)
                .filter(t -> !t.isBlank()).ifPresent(t -> {
                    // The list stays on the folder under its new name.
                    if (source.equals(new Source.Folder(folder))) source = new Source.Folder(t.strip());
                    saveChange(() -> s.subscriptions().renameFolder(folder, t.strip()));
                });
    }

    public void openWebsite(String feedId) {
        s.subscriptions().feed(feedId).ifPresent(f -> browse(f.siteUrl().isEmpty() ? f.url() : f.siteUrl()));
    }

    public void unsubscribe(String feedId) {
        s.subscriptions().feed(feedId).ifPresent(f -> {
            if (view.confirm(msg.get("dialog.unsubscribe.title"), msg.format("dialog.unsubscribe.text", f.title()))) {
                remove(feedId);
                refresh();
            }
        });
    }

    private void remove(String feedId) {
        try {
            s.subscriptions().remove(feedId);
            s.store().remove(feedId);
            errors.remove(feedId);
        } catch (UncheckedIOException e) {
            view.showError(msg.format("error.save", e.getCause().getMessage()));
        }
    }

    private void saveChange(Runnable change) {
        try {
            change.run();
        } catch (UncheckedIOException e) {
            view.showError(msg.format("error.save", e.getCause().getMessage()));
        }
        refresh();
    }

    // ---------------------------------------------------------------- OPML

    public void importOpml() {
        view.chooseImportFile().ifPresent(this::importOpml);
    }

    public void importOpml(Path file) {
        List<Feed> feeds;
        try {
            feeds = Opml.read(Files.readAllBytes(file));
        } catch (IOException | XMLStreamException e) {
            view.showError(msg.format("error.import", file.getFileName(), e.getMessage()));
            return;
        }
        int before = s.subscriptions().feeds().size();
        subscribe(feeds);
        int added = s.subscriptions().feeds().size() - before;
        view.showInfo(msg.format("info.imported", added, feeds.size() - added));
    }

    public void exportOpml() {
        view.chooseExportFile("subscriptions.opml").ifPresent(file -> {
            try {
                Files.writeString(file, s.subscriptions().toOpml(msg.get("window.title")));
            } catch (IOException e) {
                view.showError(msg.format("error.export", file, e.getMessage()));
            }
        });
    }

    // ---------------------------------------------------------------- the rest

    public void preferences() {
        view.editPreferences(settings).ifPresent(newSettings -> {
            settings = newSettings;
            try {
                s.preferences().save(newSettings);
            } catch (UncheckedIOException e) {
                view.showError(msg.format("error.save", e.getCause().getMessage()));
            }
            view.showSettings(newSettings);
            refresh();
        });
    }

    public Settings settings() {
        return settings;
    }

    public void focusSearch() {
        view.focusSearch();
    }

    /** The program's own language; applies at the next start. */
    public void setLanguage(Optional<Language> language) {
        if (locales == null) return;
        try {
            locales.setApplicationLanguage(language);
        } catch (IOException e) {
            LOG.warn("Could not save the language", e);
            view.showError(msg.format("error.save", e.getMessage()));
            return;
        }
        if (locales.restartRequired()) {
            view.showInfo(Messages.commons(msg.locale()).format("language.restart", "amade-news"));
        }
    }

    public void about() {
        view.showAbout();
    }

    public void exit() {
        view.close();
        s.exit().run();
    }

    private static String html(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ---------------------------------------------------------------- for tests

    List<ArticleRow> shown() {
        return shown;
    }

    Source source() {
        return source;
    }

    String selected() {
        return selected;
    }
}

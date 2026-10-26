package eu.amadeos.apps.news.ui.main;

import eu.amadeos.apps.news.Scene;
import eu.amadeos.apps.news.domain.Feed;
import eu.amadeos.apps.news.domain.Settings;
import eu.amadeos.apps.news.domain.Source;
import eu.amadeos.apps.news.update.FeedFinder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static eu.amadeos.apps.news.TestTexts.DE;
import static eu.amadeos.apps.news.TestTexts.EN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainPresenterTest {
    @TempDir
    Path dir;

    private final FakeView view = new FakeView();

    private MainPresenter start(Scene scene, String... args) {
        MainPresenter presenter = new MainPresenter(view, EN, null, scene.services());
        presenter.start(List.of(args));
        return presenter;
    }

    @Test
    void withoutSubscriptionsTheWelcomeOffersTheCatalog() {
        Scene scene = new Scene(dir);
        start(scene);
        assertEquals(List.of("All Articles", "Unread", "Starred", "Read Later"), view.sourceLabels());
        assertTrue(view.placeholder.contains("Welcome to News"));
        assertTrue(view.placeholder.contains("action:catalog"));
        assertTrue(scene.http.requests.isEmpty(), "nothing to update");
        assertEquals(Settings.DEFAULT, view.settings);
    }

    @Test
    void atStartTheFeedsAreUpdatedAndTheArticlesShowNewestFirst() throws Exception {
        Scene scene = new Scene(dir).withFeeds();
        start(scene);
        assertEquals(List.of("Summit ends with a deal", "Storm reaches the coast", "A comet in the morning sky",
                "Markets calm", "Bees count to four"), view.titles());
        assertEquals(5, view.unread);
        assertFalse(view.updating);
        assertEquals("Updated at 08:00: 5 new", view.updateStatus);
        assertEquals("5 articles, 5 unread", view.status);
        assertEquals(List.of("All Articles", "Unread", "Starred", "Read Later", "World News", "Science",
                "Science Today"), view.sourceLabels());
        assertTrue(view.source("Science Today").indent());
        assertEquals(2, view.source("Science").unread());
        assertEquals("07:30", view.articles.getFirst().when());
        assertEquals("World News", view.articles.getFirst().feedTitle());
    }

    @Test
    void showingAnArticleMarksItReadUnlessTheSettingIsOff() throws Exception {
        Scene scene = new Scene(dir).withFeeds();
        MainPresenter presenter = start(scene);
        String key = view.articles.getFirst().key();
        presenter.selectArticle(key);
        assertEquals("Summit ends with a deal", view.article.orElseThrow().article().title());
        assertTrue(view.article.get().article().read());
        assertEquals(4, view.unread);

        scene.preferences.save(new Settings(30, true, 30, 13, true, false));
        FakeView other = new FakeView();
        MainPresenter second = new MainPresenter(other, EN, null, scene.services());
        second.start(List.of());
        second.selectArticle(other.articles.get(1).key());
        assertFalse(other.article.orElseThrow().article().read());
    }

    @Test
    void theUnreadListKeepsTheOpenArticleUntilAnotherIsChosen() throws Exception {
        Scene scene = new Scene(dir).withFeeds();
        MainPresenter presenter = start(scene);
        presenter.selectSource(new Source.Unread());
        String first = view.articles.getFirst().key();
        presenter.selectArticle(first);
        assertTrue(view.articles.stream().anyMatch(r -> r.key().equals(first)));
        presenter.nextUnread();
        assertEquals("Storm reaches the coast", view.article.orElseThrow().article().title());
        assertFalse(view.articles.stream().anyMatch(r -> r.key().equals(first)), "the read one leaves the list");
    }

    @Test
    void starAndReadLaterHaveTheirOwnLists() throws Exception {
        Scene scene = new Scene(dir).withFeeds();
        MainPresenter presenter = start(scene);
        presenter.selectArticle(view.articles.get(1).key());
        presenter.toggleStar();
        presenter.selectArticle(view.articles.get(3).key());
        presenter.toggleLater();
        assertEquals(1, view.source("Starred").unread());
        assertEquals(1, view.source("Read Later").unread());
        presenter.selectSource(new Source.Starred());
        assertEquals(List.of("Storm reaches the coast"), view.titles());
        presenter.selectSource(new Source.Later());
        assertEquals(List.of("Markets calm"), view.titles());
        presenter.selectArticle(view.articles.getFirst().key());
        presenter.toggleLater();
        assertEquals(List.of(), view.titles());
        assertTrue(view.placeholder.contains("no articles here"));
    }

    @Test
    void searchFindsArticlesWithEveryWord() throws Exception {
        Scene scene = new Scene(dir).withFeeds();
        MainPresenter presenter = start(scene);
        presenter.search("storm north");
        assertEquals(List.of("Storm reaches the coast"), view.titles());
        assertEquals("1 found for \"storm north\"", view.status);
        presenter.search("volcano");
        assertTrue(view.placeholder.contains("No article contains \"volcano\""));
        presenter.search("");
        assertEquals(5, view.articles.size());
    }

    @Test
    void movingThroughTheListAndMarkingAllRead() throws Exception {
        Scene scene = new Scene(dir).withFeeds();
        MainPresenter presenter = start(scene);
        presenter.selectSource(new Source.OneFeed(Feed.idOf(Scene.WORLD)));
        assertEquals(3, view.articles.size());
        presenter.next();
        presenter.next();
        assertEquals("Storm reaches the coast", view.article.orElseThrow().article().title());
        presenter.previous();
        assertEquals("Summit ends with a deal", view.article.orElseThrow().article().title());
        presenter.markAllRead();
        assertEquals(0, view.source("World News").unread());
        assertEquals(2, view.unread);
    }

    @Test
    void theSelectedArticleOpensInTheBrowserAndItsLinkCanBeCopied() throws Exception {
        Scene scene = new Scene(dir).withFeeds();
        MainPresenter presenter = start(scene);
        presenter.selectArticle(view.articles.getFirst().key());
        presenter.openInWeb();
        presenter.copyLink();
        assertEquals(List.of("https://world.example.org/w1"), scene.browsed);
        assertEquals(List.of("https://world.example.org/w1"), view.copied);
        presenter.linkClicked("https://broken.example.org/");
        assertEquals(List.of("The web browser could not open https://broken.example.org/: no browser"), view.errors);
        presenter.openWebsite(Feed.idOf(Scene.SCIENCE));
        assertEquals("https://science.example.org/", scene.browsed.getLast());
    }

    @Test
    void aFeedThatFailsShowsWhyNextToItsName() throws Exception {
        Scene scene = new Scene(dir).withFeeds();
        scene.http.status(Scene.SCIENCE, 404, Map.of());
        start(scene);
        assertEquals("The server answered with error 404.", view.source("Science Today").error());
        assertEquals("", view.source("World News").error());
        assertEquals("Updated at 08:00: 3 new, 1 feeds could not be updated", view.updateStatus);
    }

    @Test
    void errorCodesInWords() {
        MainPresenter presenter = new MainPresenter(view, EN, null, new Scene(dir).services());
        assertEquals("The server did not answer in time.", presenter.errorText("timeout"));
        assertEquals("The server answered with error 503.", presenter.errorText("http-503"));
        assertEquals("Error: disk full", presenter.errorText("disk full"));
    }

    @Test
    void addingAWebsiteFindsItsFeedAndSubscribes() throws Exception {
        Scene scene = new Scene(dir).withFeeds();
        view.addFeedAnswer = address -> {
            List<FeedFinder.Candidate> found = view.finder.apply("https://blog.example.org/").join();
            FeedFinder.Candidate c = found.getFirst();
            return Optional.of(new MainView.AddFeedChoice(c, "My blog", "Friends"));
        };
        MainPresenter presenter = start(scene);
        presenter.addFeed();
        assertEquals(List.of("Science"), view.addFeedFolders);
        Feed blog = scene.subscriptions.byUrl("https://blog.example.org/feed.xml").orElseThrow();
        assertEquals("My blog", blog.title());
        assertEquals("Friends", blog.folder());
        assertEquals(new Source.OneFeed(blog.id()), presenter.source());
        assertEquals(List.of("Hello"), view.titles());

        presenter.addFeed();
        assertEquals(List.of("My blog is subscribed already."), view.infos);
    }

    @Test
    void theFinderTellsWhenThereIsNoFeed() throws Exception {
        Scene scene = new Scene(dir);
        scene.http.serve("https://plain.example.org/", "<html><title>x</title></html>", "text/html");
        List<String> messages = new ArrayList<>();
        view.addFeedAnswer = address -> {
            view.finder.apply("https://plain.example.org/").whenComplete((r, e) -> messages.add(e.getMessage()));
            view.finder.apply("nowhere.example.org").whenComplete((r, e) -> messages.add(e.getMessage()));
            return Optional.empty();
        };
        start(scene).addFeed();
        assertEquals(List.of(EN.get("add.none"), "No connection to the server."), messages);
    }

    @Test
    void aFeedAddressOnTheCommandLineOpensAddFeed() throws Exception {
        Scene scene = new Scene(dir);
        List<String> asked = new ArrayList<>();
        view.addFeedAnswer = address -> {
            asked.add(address);
            return Optional.empty();
        };
        start(scene, "feed:https://world.example.org/rss.xml");
        assertEquals(List.of("feed:https://world.example.org/rss.xml"), asked);
    }

    @Test
    void theCatalogSubscribesTickedFeedsIntoEuropeAndUnsubscribesUntickedOnes() throws Exception {
        Scene scene = new Scene(dir);
        String tagesschau = "https://www.tagesschau.de/xml/rss2/";
        String dw = "https://rss.dw.com/xml/rss-en-all";
        view.catalogAnswer = Optional.of(Set.of(tagesschau, dw));
        MainPresenter presenter = start(scene);
        presenter.openCatalog();
        assertEquals(List.of("en"), view.catalogLanguages);
        Feed feed = scene.subscriptions.byUrl(tagesschau).orElseThrow();
        assertEquals("Europe", feed.folder());
        assertEquals("de", feed.country());
        assertEquals("de", feed.language());
        assertTrue(view.sourceLabels().contains("Europe"));
        assertEquals("de", view.source("tagesschau").flag());

        view.catalogAnswer = Optional.of(Set.of(dw));
        presenter.openCatalog();
        assertEquals(Set.of(tagesschau, dw), view.catalogSubscribed);
        assertTrue(scene.subscriptions.byUrl(tagesschau).isEmpty());
        assertEquals(1, scene.subscriptions.feeds().size());
    }

    @Test
    void theCatalogFolderAndLanguagesFollowTheReadersLanguage() {
        MainPresenter presenter = new MainPresenter(view, DE, null, new Scene(dir).services());
        assertEquals(List.of("de", "en"), presenter.languages());
        assertEquals("Europa", DE.get("folder.europe"));
    }

    @Test
    void unsubscribingAsksFirstAndDeletesTheArticles() throws Exception {
        Scene scene = new Scene(dir).withFeeds();
        MainPresenter presenter = start(scene);
        String world = Feed.idOf(Scene.WORLD);
        view.confirmAnswer = false;
        presenter.unsubscribe(world);
        assertEquals(List.of("Unsubscribe from World News? Its articles are deleted, starred ones too."),
                view.confirmations);
        assertEquals(5, view.articles.size());
        view.confirmAnswer = true;
        presenter.unsubscribe(world);
        assertEquals(2, view.articles.size());
        assertTrue(scene.store.of(world).isEmpty());
        assertFalse(view.sourceLabels().contains("World News"));
    }

    @Test
    void feedsAndFoldersCanBeRenamedAndMoved() throws Exception {
        Scene scene = new Scene(dir).withFeeds();
        MainPresenter presenter = start(scene);
        String world = Feed.idOf(Scene.WORLD);
        view.textAnswer = Optional.of("The World");
        presenter.renameFeed(world);
        view.folderAnswer = Optional.of("Science");
        presenter.moveFeed(world);
        assertEquals("Science", scene.subscriptions.feed(world).orElseThrow().folder());
        presenter.selectSource(new Source.Folder("Science"));
        view.textAnswer = Optional.of("Knowledge");
        presenter.renameFolder("Science");
        assertEquals(new Source.Folder("Knowledge"), presenter.source());
        assertEquals(List.of("All Articles", "Unread", "Starred", "Read Later", "Knowledge", "The World",
                "Science Today"), view.sourceLabels());
    }

    @Test
    void subscriptionsGoOutAndComeBackAsOpml() throws Exception {
        Scene scene = new Scene(dir).withFeeds();
        MainPresenter presenter = start(scene);
        Path file = dir.resolve("export.opml");
        view.exportFile = Optional.of(file);
        presenter.exportOpml();
        assertTrue(Files.readString(file).contains("xmlUrl=\"" + Scene.SCIENCE + "\""));

        Scene other = new Scene(dir.resolve("other"));
        FakeView second = new FakeView();
        MainPresenter fresh = new MainPresenter(second, EN, null, other.services());
        fresh.start(List.of());
        second.importFile = Optional.of(file);
        fresh.importOpml();
        assertEquals(List.of("2 feeds imported, 0 were subscribed already."), second.infos);
        assertEquals("Science", other.subscriptions.byUrl(Scene.SCIENCE).orElseThrow().folder());
        assertEquals(5, second.articles.size());

        Path broken = Files.writeString(dir.resolve("broken.opml"), "<opml><body>");
        fresh.importOpml(broken);
        assertTrue(second.errors.getFirst().startsWith("broken.opml could not be imported"));
    }

    @Test
    void feedsAreUpdatedAgainWhenTheTimeHasCome() throws Exception {
        Scene scene = new Scene(dir).withFeeds();
        MainPresenter presenter = start(scene);
        int requests = scene.http.requests.size();
        scene.clock.advance(Duration.ofMinutes(20));
        presenter.tick();
        assertEquals(requests, scene.http.requests.size());
        scene.clock.advance(Duration.ofMinutes(10));
        presenter.tick();
        assertEquals(requests + 2, scene.http.requests.size());
    }

    @Test
    void preferencesAreSavedAndApplied() throws Exception {
        Scene scene = new Scene(dir).withFeeds();
        MainPresenter presenter = start(scene);
        Settings changed = new Settings(0, false, 90, 18, false, true);
        view.preferencesAnswer = Optional.of(changed);
        presenter.preferences();
        assertEquals(changed, view.settings);
        assertEquals(changed, scene.preferences.settings());
        int requests = scene.http.requests.size();
        scene.clock.advance(Duration.ofDays(1));
        presenter.tick();
        assertEquals(requests, scene.http.requests.size(), "updating only by hand");
    }

    @Test
    void datesReadLikeAClock() {
        MainPresenter presenter = new MainPresenter(view, EN, null, new Scene(dir).services());
        assertEquals("06:10", presenter.when(Instant.parse("2026-10-26T06:10:00Z")));
        assertEquals("yesterday 21:00", presenter.when(Instant.parse("2026-10-25T21:00:00Z")));
        assertEquals("Thu 12:00", presenter.when(Instant.parse("2026-10-22T12:00:00Z")));
        assertEquals("3 Feb", presenter.when(Instant.parse("2026-02-03T12:00:00Z")));
        assertEquals("3 Feb 2025", presenter.when(Instant.parse("2025-02-03T12:00:00Z")));
    }

    @Test
    void exitClosesTheWindow() {
        Scene scene = new Scene(dir);
        MainPresenter presenter = start(scene);
        presenter.about();
        presenter.focusSearch();
        presenter.exit();
        assertTrue(view.aboutShown);
        assertTrue(view.searchFocused);
        assertTrue(view.closed);
        assertEquals(1, scene.exits);
    }
}

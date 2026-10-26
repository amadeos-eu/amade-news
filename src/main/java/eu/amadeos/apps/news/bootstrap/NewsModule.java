package eu.amadeos.apps.news.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import eu.amadeos.apps.news.catalog.Catalog;
import eu.amadeos.apps.news.images.ImageCache;
import eu.amadeos.apps.news.net.Http;
import eu.amadeos.apps.news.net.JdkHttp;
import eu.amadeos.apps.news.store.ArticleStore;
import eu.amadeos.apps.news.store.NewsPreferences;
import eu.amadeos.apps.news.store.Subscriptions;
import eu.amadeos.apps.news.ui.main.MainPresenter;
import eu.amadeos.apps.news.ui.main.MainWindow;
import eu.amadeos.apps.news.update.FeedFinder;
import eu.amadeos.apps.news.update.FeedUpdater;
import eu.amadeos.commons.app.Ui;
import eu.amadeos.commons.config.AppDirectories;
import eu.amadeos.commons.config.XdgDirectories;
import eu.amadeos.commons.desktop.DesktopLauncher;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;

import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * amade-news bindings. Theme, language, directories and the other shared services come from
 * {@link eu.amadeos.commons.app.CommonsModule}.
 */
public final class NewsModule extends AbstractModule {
    /** The texts, {@code messages.properties} and its translations. */
    public static final String MESSAGES = "eu.amadeos.apps.news.messages";

    /** The texts in the language {@code AmadeApplication} chose. */
    @Provides
    @Singleton
    Messages messages(Locale locale) {
        return Messages.load(NewsModule.class, MESSAGES, locale);
    }

    @Provides
    @Singleton
    Http http() {
        return new JdkHttp("amade-news/" + AmadeNewsApplication.version() + " (amadeOS; +https://amadeos.eu)");
    }

    // --8<-- [start:places]
    /** {@code ~/.local/share/amadeos/news}: the articles, one JSON file per feed. */
    public static Path dataFolder(XdgDirectories xdg) {
        return xdg.dataHome().resolve("amadeos").resolve("news");
    }

    @Provides
    @Singleton
    Subscriptions subscriptions(AppDirectories directories) {
        return new Subscriptions(directories.configFile(Subscriptions.FILE), Clock.systemDefaultZone());
    }

    @Provides
    @Singleton
    ArticleStore store(XdgDirectories xdg) {
        return new ArticleStore(dataFolder(xdg).resolve("articles"));
    }

    @Provides
    @Singleton
    NewsPreferences preferences(AppDirectories directories) {
        return new NewsPreferences(directories.configFile(NewsPreferences.FILE));
    }

    /** {@code ~/.cache/amadeos/news/images}: the articles' pictures. */
    @Provides
    @Singleton
    ImageCache images(Http http, AppDirectories directories, ExecutorService background, @Ui Executor ui) {
        ImageCache cache = new ImageCache(http, directories.cache().resolve("images"), background, ui,
                Clock.systemDefaultZone());
        cache.cleanUp();
        return cache;
    }
    // --8<-- [end:places]

    @Provides
    @Singleton
    MainWindow window(Messages msg, LocaleSettings locales, ImageCache images) {
        return new MainWindow(msg, locales, images);
    }

    @Provides
    @Singleton
    MainPresenter presenter(MainWindow window, Messages msg, LocaleSettings locales, Subscriptions subscriptions,
                            ArticleStore store, NewsPreferences preferences, Http http, DesktopLauncher launcher,
                            ExecutorService background, @Ui Executor ui) {
        Clock clock = Clock.systemDefaultZone();
        return new MainPresenter(window, msg, locales, new MainPresenter.Services(subscriptions, store, preferences,
                new FeedUpdater(http, store, clock), new FeedFinder(http), Catalog.load(), launcher, background, ui,
                clock, ZoneId.systemDefault(), () -> System.exit(0)));
    }
}

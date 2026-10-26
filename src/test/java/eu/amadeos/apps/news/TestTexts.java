package eu.amadeos.apps.news;

import eu.amadeos.apps.news.bootstrap.NewsModule;
import eu.amadeos.commons.i18n.Messages;

import java.util.Locale;

/** The program's texts for tests, which compare English and German sentences. */
public final class TestTexts {
    public static final Messages EN = Messages.load(NewsModule.class, NewsModule.MESSAGES, Locale.UK);
    public static final Messages DE = Messages.load(NewsModule.class, NewsModule.MESSAGES, Locale.GERMANY);

    private TestTexts() {
    }
}

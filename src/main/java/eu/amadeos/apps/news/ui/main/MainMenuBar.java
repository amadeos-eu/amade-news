package eu.amadeos.apps.news.ui.main;

import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.EdtExecutor;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import javax.swing.border.BevelBorder;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** News, Article, View, Options and Help. */
public final class MainMenuBar extends JMenuBar {
    private static final int CTRL = InputEvent.CTRL_DOWN_MASK;
    private static final int SHIFT = InputEvent.SHIFT_DOWN_MASK;

    /** The entries that need a selected article. */
    static final List<String> ARTICLE_ITEMS = List.of("menu.article.open", "menu.article.star", "menu.article.later",
            "menu.article.read", "menu.article.copy");

    private final Messages msg;
    private final Map<String, JMenuItem> items = new HashMap<>();

    public MainMenuBar(MainPresenter presenter, Messages msg, LocaleSettings locales) {
        EdtExecutor.requireEdt();
        this.msg = msg;
        setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));

        JMenu news = menu("menu.news");
        news.add(item("menu.news.add", key(KeyEvent.VK_N, CTRL), presenter::addFeed));
        news.add(item("menu.news.catalog", key(KeyEvent.VK_E, CTRL), presenter::openCatalog));
        news.addSeparator();
        news.add(item("menu.news.update", key(KeyEvent.VK_F5, 0), presenter::updateAll));
        news.add(item("menu.news.update-selected", key(KeyEvent.VK_F5, SHIFT), presenter::updateSelected));
        news.addSeparator();
        news.add(item("menu.news.import", null, presenter::importOpml));
        news.add(item("menu.news.export", null, presenter::exportOpml));
        news.addSeparator();
        news.add(item("menu.news.quit", key(KeyEvent.VK_Q, CTRL), presenter::exit));

        JMenu article = menu("menu.article");
        article.add(item("menu.article.open", key(KeyEvent.VK_ENTER, CTRL), presenter::openInWeb));
        article.add(item("menu.article.star", key(KeyEvent.VK_D, CTRL), presenter::toggleStar));
        article.add(item("menu.article.later", key(KeyEvent.VK_L, CTRL), presenter::toggleLater));
        article.add(item("menu.article.read", key(KeyEvent.VK_U, CTRL), presenter::toggleRead));
        article.add(item("menu.article.copy", key(KeyEvent.VK_C, CTRL | SHIFT), presenter::copyLink));
        article.addSeparator();
        article.add(item("menu.article.all-read", key(KeyEvent.VK_A, CTRL | SHIFT), presenter::markAllRead));

        JMenu view = menu("menu.view");
        view.add(item("menu.view.next-unread", key(KeyEvent.VK_PERIOD, CTRL), presenter::nextUnread));
        view.add(item("menu.view.next", key(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK), presenter::next));
        view.add(item("menu.view.previous", key(KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK), presenter::previous));
        view.addSeparator();
        view.add(item("menu.view.find", key(KeyEvent.VK_F, CTRL), presenter::focusSearch));

        JMenu options = menu("menu.options");
        options.add(item("menu.options.preferences", null, presenter::preferences));
        if (locales != null) options.add(languageMenu(presenter, locales));

        JMenu help = menu("menu.help");
        help.add(item("menu.help.about", null, presenter::about));
    }

    void enableArticle(boolean selected) {
        for (String key : ARTICLE_ITEMS) items.get(key).setEnabled(selected);
    }

    JMenuItem item(String name) {
        return items.get(name);
    }

    /** "Desktop setting (Deutsch)" and the five languages; the choice applies at the next start. */
    private JMenu languageMenu(MainPresenter presenter, LocaleSettings locales) {
        JMenu menu = Mnemonics.apply(new JMenu(), msg.get("menu.options.language"));
        menu.setName("menu.options.language");
        Messages commons = Messages.commons(msg.locale());
        List<Optional<Language>> choices = new ArrayList<>();
        choices.add(Optional.empty());
        for (Language language : Language.values()) choices.add(Optional.of(language));
        ButtonGroup group = new ButtonGroup();
        Optional<Language> current = locales.applicationLanguage();
        for (Optional<Language> choice : choices) {
            String label = choice.map(Language::nativeName).orElseGet(() -> commons.format("language.follow-desktop",
                    locales.desktopResolution().language().nativeName()));
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(label, choice.equals(current));
            item.setName(choice.map(Language::code).orElse(LocaleSettings.FOLLOW_DESKTOP));
            item.addActionListener(e -> presenter.setLanguage(choice));
            group.add(item);
            menu.add(item);
            if (choice.isEmpty()) menu.addSeparator();
        }
        return menu;
    }

    private static KeyStroke key(int code, int modifiers) {
        return KeyStroke.getKeyStroke(code, modifiers);
    }

    private JMenu menu(String key) {
        JMenu menu = Mnemonics.apply(new JMenu(), msg.get(key));
        menu.setName(key);
        add(menu);
        return menu;
    }

    private JMenuItem item(String key, KeyStroke accelerator, Runnable action) {
        JMenuItem item = Mnemonics.apply(new JMenuItem(), msg.get(key));
        item.setName(key);
        if (accelerator != null) item.setAccelerator(accelerator);
        item.addActionListener(e -> action.run());
        items.put(key, item);
        return item;
    }
}

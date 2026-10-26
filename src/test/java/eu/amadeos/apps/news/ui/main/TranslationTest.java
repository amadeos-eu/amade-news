package eu.amadeos.apps.news.ui.main;

import eu.amadeos.apps.news.bootstrap.NewsModule;
import eu.amadeos.apps.news.catalog.Catalog;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.i18n.TranslationCheck;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationTest {
    static final Messages EN = texts(Language.ENGLISH);

    static Messages texts(Language language) {
        return Messages.load(NewsModule.class, NewsModule.MESSAGES, Locale.forLanguageTag(language.code()));
    }

    @Test
    void everyLanguageHasEveryText() {
        assertEquals(Map.of(), TranslationCheck.problems(NewsModule.class, NewsModule.MESSAGES));
    }

    /** Every literal key in the code, e.g. msg.get("menu.news"), has a text. */
    @Test
    void everyKeyTheCodeUsesExists() throws IOException {
        Pattern call = Pattern.compile("(?:msg\\.(?:get|format|has)\\(|popupItem\\(|item\\(|menu\\()\"([a-z][a-z0-9.-]*[a-z0-9])\"");
        TreeSet<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher m = call.matcher(Files.readString(file));
                while (m.find()) if (!EN.has(m.group(1))) missing.add(m.group(1));
            }
        }
        assertEquals(new TreeSet<>(), missing);
    }

    /** Keys put together from enums and lists. */
    @Test
    void everyComposedKeyExists() {
        List<String> keys = new ArrayList<>();
        for (Catalog.Kind k : Catalog.Kind.values()) keys.add("catalog.kind." + k.name().toLowerCase(Locale.ROOT));
        for (String s : List.of("small", "normal", "large", "larger")) keys.add("prefs.text." + s);
        for (String b : List.of("open", "star", "unstar", "later", "not-later", "copy")) keys.add("button." + b);
        for (String e : List.of("timeout", "unknown-host", "no-connection", "web-page", "not-a-feed", "interrupted")) {
            keys.add("error." + e);
        }
        assertEquals(List.of(), keys.stream().filter(k -> !EN.has(k)).toList());
    }

    /** No two entries share a mnemonic letter in the menu bar, one menu or one dialog, in any language. */
    @Test
    void mnemonicsAreUnique() {
        Map<String, List<String>> groups = new HashMap<>();
        List<String> bar = List.of("menu.news", "menu.article", "menu.view", "menu.options", "menu.help", "button.open",
                "button.copy");
        groups.put("bar", plus(bar, "button.star", "button.later"));
        groups.put("bar-undo", plus(bar, "button.unstar", "button.not-later"));
        groups.put("news", List.of("menu.news.add", "menu.news.catalog", "menu.news.update", "menu.news.update-selected",
                "menu.news.import", "menu.news.export", "menu.news.quit"));
        groups.put("article", List.of("menu.article.open", "menu.article.star", "menu.article.later",
                "menu.article.read", "menu.article.copy", "menu.article.all-read"));
        groups.put("view", List.of("menu.view.next-unread", "menu.view.next", "menu.view.previous", "menu.view.find"));
        groups.put("options", List.of("menu.options.preferences", "menu.options.language"));
        groups.put("help", List.of("menu.help.about"));
        groups.put("popup-feed", List.of("popup.update", "popup.website", "popup.rename", "popup.move",
                "popup.all-read", "popup.unsubscribe"));
        groups.put("popup-folder", List.of("popup.update", "popup.rename-folder", "popup.all-read"));
        groups.put("add", List.of("add.address", "add.find", "add.name", "add.folder", "add.subscribe"));
        groups.put("prefs", List.of("prefs.update", "prefs.update.at-start", "prefs.keep", "prefs.text",
                "prefs.pictures", "prefs.mark-read", "prefs.ok"));
        groups.put("catalog", List.of("catalog.filter", "catalog.ok"));
        for (Language language : Language.values()) {
            Messages texts = texts(language);
            for (Map.Entry<String, List<String>> group : groups.entrySet()) {
                Map<Integer, String> used = new HashMap<>();
                for (String key : group.getValue()) {
                    Mnemonics.Text text = Mnemonics.parse(texts.get(key));
                    assertTrue(text.index() >= 0, language + " " + key + " has no mnemonic");
                    String before = used.put(text.keyCode(), key);
                    assertEquals(null, before, language + " " + group.getKey() + ": " + key + " and " + before
                            + " share a letter");
                }
            }
        }
    }

    private static List<String> plus(List<String> list, String... more) {
        List<String> all = new ArrayList<>(list);
        all.addAll(List.of(more));
        return all;
    }

    @Test
    void textsWithApostrophesAndChoicesComeOutRight() {
        assertEquals("Actualisé à 08:00 : 3 nouveaux, 1 flux n'ont pas pu être actualisés",
                texts(Language.FRENCH).format("status.updated-failed", "08:00", 3, 1));
        assertEquals("every hour", EN.format("prefs.update.hours", 1));
        assertEquals("alle 2 Stunden", texts(Language.GERMAN).format("prefs.update.hours", 2));
        assertEquals("Notizie", texts(Language.ITALIAN).get("window.title"));
        assertEquals("Noticias (4)", texts(Language.SPANISH).format("window.title.unread", 4));
    }
}

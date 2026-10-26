package eu.amadeos.apps.news.store;

import eu.amadeos.apps.news.domain.Settings;
import eu.amadeos.commons.config.PropertiesFile;

import java.nio.file.Path;
import java.util.Properties;

/** The preferences in {@code ~/.config/amadeos/news/news.properties}. */
public final class NewsPreferences {
    public static final String FILE = "news.properties";

    private final Path file;

    public NewsPreferences(Path file) {
        this.file = file;
    }

    public Settings settings() {
        Properties p = PropertiesFile.load(file);
        Settings d = Settings.DEFAULT;
        return new Settings(number(p, "update.minutes", d.updateMinutes()), flag(p, "update.at-start", d.updateAtStart()),
                number(p, "keep.days", d.keepDays()), number(p, "text.size", d.textSize()),
                flag(p, "pictures", d.showPictures()), flag(p, "mark-read-on-show", d.markReadOnShow()));
    }

    public void save(Settings s) {
        Properties p = PropertiesFile.load(file);
        p.setProperty("update.minutes", Integer.toString(s.updateMinutes()));
        p.setProperty("update.at-start", Boolean.toString(s.updateAtStart()));
        p.setProperty("keep.days", Integer.toString(s.keepDays()));
        p.setProperty("text.size", Integer.toString(s.textSize()));
        p.setProperty("pictures", Boolean.toString(s.showPictures()));
        p.setProperty("mark-read-on-show", Boolean.toString(s.markReadOnShow()));
        PropertiesFile.store(file, p, "amadeOS News");
    }

    private static int number(Properties p, String key, int fallback) {
        try {
            return Integer.parseInt(p.getProperty(key, "").strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean flag(Properties p, String key, boolean fallback) {
        String v = p.getProperty(key, "").strip();
        return v.isEmpty() ? fallback : Boolean.parseBoolean(v);
    }
}

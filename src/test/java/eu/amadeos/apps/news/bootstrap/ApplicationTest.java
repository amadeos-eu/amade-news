package eu.amadeos.apps.news.bootstrap;

import eu.amadeos.apps.news.store.Subscriptions;
import eu.amadeos.commons.config.XdgDirectories;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationTest {
    @Test
    void theCommandLineGivesAddressesAndFiles() {
        assertEquals(List.of("https://example.org/feed.xml", "/home/ralf/Downloads/feeds list.opml",
                        "feed://example.org/rss"),
                AmadeNewsApplication.arguments(new String[]{"--verbose", "https://example.org/feed.xml", " ",
                        "file:///home/ralf/Downloads/feeds%20list.opml", "feed://example.org/rss"}));
    }

    @Test
    void theArticlesLiveUnderTheDataFolder() {
        XdgDirectories xdg = new XdgDirectories(java.util.Map.of(), "/home/ralf");
        assertEquals(Path.of("/home/ralf/.local/share/amadeos/news"), NewsModule.dataFolder(xdg));
        assertEquals("subscriptions.opml", Subscriptions.FILE);
        assertEquals("dev", AmadeNewsApplication.version());
    }
}

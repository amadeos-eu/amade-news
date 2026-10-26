package eu.amadeos.apps.news.store;

import eu.amadeos.apps.news.domain.Feed;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * The subscribed feeds in {@code ~/.config/amadeos/news/subscriptions.opml}, in the order the user sees them.
 * Every change is written at once.
 */
public final class Subscriptions {
    public static final String FILE = "subscriptions.opml";

    private final Path file;
    private final Clock clock;
    private final List<Feed> feeds = new ArrayList<>();
    private boolean existed;

    public Subscriptions(Path file, Clock clock) {
        this.file = file;
        this.clock = clock;
    }

    /** Reads the file; a missing file means no subscriptions yet. */
    public void load() throws IOException {
        feeds.clear();
        existed = Files.exists(file);
        if (!existed) return;
        try {
            feeds.addAll(Opml.read(Files.readAllBytes(file)));
        } catch (XMLStreamException e) {
            throw new IOException(file + ": " + e.getMessage(), e);
        }
    }

    /** False before the first subscription was ever saved: News shows its welcome then. */
    public boolean existed() {
        return existed;
    }

    public Path file() {
        return file;
    }

    public List<Feed> feeds() {
        return List.copyOf(feeds);
    }

    public Optional<Feed> feed(String id) {
        return feeds.stream().filter(f -> f.id().equals(id)).findFirst();
    }

    public Optional<Feed> byUrl(String url) {
        return feeds.stream().filter(f -> f.url().equals(url.strip())).findFirst();
    }

    /** The folders in the order of their first feed. */
    public List<String> folders() {
        LinkedHashSet<String> folders = new LinkedHashSet<>();
        feeds.forEach(f -> {
            if (!f.folder().isEmpty()) folders.add(f.folder());
        });
        return List.copyOf(folders);
    }

    /** Adds the feeds whose address is not subscribed yet; returns those added. */
    public List<Feed> add(List<Feed> newFeeds) {
        List<Feed> added = new ArrayList<>();
        for (Feed feed : newFeeds) {
            if (byUrl(feed.url()).isPresent() || added.stream().anyMatch(a -> a.url().equals(feed.url()))) continue;
            added.add(feed);
        }
        if (added.isEmpty()) return added;
        // A feed joins the end of its folder, so folders stay together.
        for (Feed feed : added) {
            int at = feeds.size();
            if (!feed.folder().isEmpty()) {
                for (int i = 0; i < feeds.size(); i++) {
                    if (feeds.get(i).folder().equals(feed.folder())) at = i + 1;
                }
            }
            feeds.add(at, feed);
        }
        save();
        return added;
    }

    public void remove(String id) {
        if (feeds.removeIf(f -> f.id().equals(id))) save();
    }

    public void change(String id, UnaryOperator<Feed> change) {
        for (int i = 0; i < feeds.size(); i++) {
            if (feeds.get(i).id().equals(id)) {
                feeds.set(i, change.apply(feeds.get(i)));
                save();
                return;
            }
        }
    }

    /** Renames a folder; merging into an existing folder of that name is fine. */
    public void renameFolder(String from, String to) {
        boolean changed = false;
        for (int i = 0; i < feeds.size(); i++) {
            if (feeds.get(i).folder().equals(from)) {
                feeds.set(i, feeds.get(i).withFolder(to));
                changed = true;
            }
        }
        if (changed) save();
    }

    public String toOpml(String title) {
        return Opml.write(feeds, title, ZonedDateTime.now(clock));
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, toOpml("amadeOS News"));
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            existed = true;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

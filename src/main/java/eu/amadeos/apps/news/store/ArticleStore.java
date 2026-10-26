package eu.amadeos.apps.news.store;

import eu.amadeos.apps.news.domain.Article;
import eu.amadeos.commons.json.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The articles of every feed, one JSON file per feed in {@code ~/.local/share/amadeos/news/articles/}, with the
 * fetch state of the feed. Kept in memory; every change is written at once. Safe to use from the update threads and
 * the window at the same time.
 */
public final class ArticleStore {
    private static final Logger LOG = LoggerFactory.getLogger(ArticleStore.class);

    private final Path dir;
    private final Map<String, Map<String, Article>> articles = new HashMap<>();
    private final Map<String, FeedState> states = new HashMap<>();

    public ArticleStore(Path dir) {
        this.dir = dir;
    }

    public Path dir() {
        return dir;
    }

    /** Reads the files of these feeds; a broken file counts as empty and is logged. */
    public synchronized void load(Collection<String> feedIds) {
        articles.clear();
        states.clear();
        for (String id : feedIds) {
            Path file = file(id);
            if (!Files.exists(file)) continue;
            try {
                read(id, Json.parseObject(Files.readString(file)));
            } catch (IOException | RuntimeException e) {
                LOG.warn("Cannot read {}: {}", file, e.getMessage());
            }
        }
    }

    private void read(String feedId, Map<String, Object> json) {
        states.put(feedId, new FeedState(Json.str(json, "etag"), Json.str(json, "lastModified"),
                instant(Json.str(json, "checked")), Json.str(json, "error"), instant(Json.str(json, "notBefore"))));
        Map<String, Article> map = new LinkedHashMap<>();
        for (Object o : Json.list(json, "articles")) {
            Instant published = instant(Json.str(o, "published"));
            String id = Json.str(o, "id");
            if (id.isEmpty() || published == null) continue;
            map.put(id, new Article(id, feedId, Json.str(o, "title"), Json.str(o, "text"), Json.str(o, "link"),
                    published, Json.str(o, "author"), Json.str(o, "image"), Json.str(o, "audio"),
                    flag(o, "read"), flag(o, "starred"), flag(o, "later")));
        }
        articles.put(feedId, map);
    }

    private static boolean flag(Object o, String key) {
        return Boolean.TRUE.equals(Json.member(o, key));
    }

    private static Instant instant(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- reading

    /** All articles of all feeds, newest first. */
    public synchronized List<Article> all() {
        List<Article> all = new ArrayList<>();
        articles.values().forEach(m -> all.addAll(m.values()));
        all.sort(Comparator.comparing(Article::published).reversed().thenComparing(Article::key));
        return all;
    }

    public synchronized List<Article> of(String feedId) {
        List<Article> list = new ArrayList<>(articles.getOrDefault(feedId, Map.of()).values());
        list.sort(Comparator.comparing(Article::published).reversed());
        return list;
    }

    public synchronized FeedState state(String feedId) {
        return states.getOrDefault(feedId, FeedState.NEW);
    }

    // ---------------------------------------------------------------- changes from the feed

    // --8<-- [start:merge]
    /**
     * Takes in what the feed has now. New entries are added unread; known ones get the new text but keep read,
     * starred and Read Later. Articles older than {@code keepDays} that are not starred, not on Read Later and no
     * longer in the feed are removed (still in the feed, they would come back as unread).
     *
     * @return how many articles are new
     */
    public synchronized int merge(String feedId, List<Article> fresh, FeedState state, Instant now, int keepDays) {
        Map<String, Article> map = articles.computeIfAbsent(feedId, k -> new LinkedHashMap<>());
        int added = 0;
        Set<String> inFeed = new HashSet<>();
        for (Article a : fresh) {
            if (a.id().isEmpty() || !inFeed.add(a.id())) continue;
            Article known = map.get(a.id());
            if (known == null) {
                map.put(a.id(), a);
                added++;
            } else {
                map.put(a.id(), known.updatedFrom(a));
            }
        }
        Instant limit = now.minus(Duration.ofDays(keepDays));
        map.values().removeIf(a -> !a.kept() && !inFeed.contains(a.id()) && a.published().isBefore(limit));
        states.put(feedId, state);
        save(feedId);
        return added;
    }
    // --8<-- [end:merge]

    /**
     * Only the state changes: 304 Not Modified, or an error. Nothing is pruned, because News does not know which
     * articles are still in the feed.
     */
    public synchronized void touch(String feedId, FeedState state) {
        states.put(feedId, state);
        save(feedId);
    }

    // ---------------------------------------------------------------- changes by the user

    /** Applies the change to the articles that match; returns how many changed. */
    public synchronized int update(Predicate<Article> which, java.util.function.UnaryOperator<Article> change) {
        Set<String> changedFeeds = new HashSet<>();
        int count = 0;
        for (Map.Entry<String, Map<String, Article>> feed : articles.entrySet()) {
            for (Map.Entry<String, Article> e : feed.getValue().entrySet()) {
                Article a = e.getValue();
                if (!which.test(a)) continue;
                Article changed = change.apply(a);
                if (changed.equals(a)) continue;
                e.setValue(changed);
                changedFeeds.add(feed.getKey());
                count++;
            }
        }
        changedFeeds.forEach(this::save);
        return count;
    }

    /** Forgets the feed and deletes its file. */
    public synchronized void remove(String feedId) {
        articles.remove(feedId);
        states.remove(feedId);
        try {
            Files.deleteIfExists(file(feedId));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---------------------------------------------------------------- files

    private Path file(String feedId) {
        return dir.resolve(feedId + ".json");
    }

    private void save(String feedId) {
        Map<String, Object> json = new LinkedHashMap<>();
        FeedState state = states.getOrDefault(feedId, FeedState.NEW);
        json.put("etag", state.etag());
        json.put("lastModified", state.lastModified());
        json.put("checked", state.checked() == null ? "" : state.checked().toString());
        json.put("error", state.error());
        json.put("notBefore", state.notBefore() == null ? "" : state.notBefore().toString());
        List<Object> list = new ArrayList<>();
        for (Article a : of(feedId)) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", a.id());
            o.put("title", a.title());
            o.put("published", a.published().toString());
            if (!a.link().isEmpty()) o.put("link", a.link());
            if (!a.author().isEmpty()) o.put("author", a.author());
            if (!a.imageUrl().isEmpty()) o.put("image", a.imageUrl());
            if (!a.audioUrl().isEmpty()) o.put("audio", a.audioUrl());
            if (a.read()) o.put("read", true);
            if (a.starred()) o.put("starred", true);
            if (a.later()) o.put("later", true);
            o.put("text", a.text());
            list.add(o);
        }
        json.put("articles", list);
        try {
            Files.createDirectories(dir);
            Path temp = dir.resolve(feedId + ".json.tmp");
            Files.writeString(temp, Json.write(json));
            Files.move(temp, file(feedId), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

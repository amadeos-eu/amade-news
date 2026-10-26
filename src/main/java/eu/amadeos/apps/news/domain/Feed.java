package eu.amadeos.apps.news.domain;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * One subscription.
 *
 * @param id       stable name for the article file, derived from the address ({@link #idOf(String)})
 * @param title    the name shown in the list; the user can change it
 * @param url      the feed's address
 * @param siteUrl  the website the feed belongs to; empty if unknown
 * @param folder   the folder in the list; empty for none
 * @param country  ISO 3166 code for a feed from Europe's News, shown as a flag; empty otherwise
 * @param language ISO 639-1 code of the articles, if known; empty otherwise
 */
public record Feed(String id, String title, String url, String siteUrl, String folder, String country, String language) {
    public Feed {
        Objects.requireNonNull(url, "url");
        id = id == null || id.isBlank() ? idOf(url) : id;
        title = title == null || title.isBlank() ? hostOf(url) : title.strip();
        siteUrl = siteUrl == null ? "" : siteUrl.strip();
        folder = folder == null ? "" : folder.strip();
        country = country == null ? "" : country.strip().toLowerCase(java.util.Locale.ROOT);
        language = language == null ? "" : language.strip().toLowerCase(java.util.Locale.ROOT);
    }

    public static Feed of(String title, String url, String folder) {
        return new Feed(null, title, url, "", folder, "", "");
    }

    public Feed withTitle(String newTitle) {
        return new Feed(id, newTitle, url, siteUrl, folder, country, language);
    }

    public Feed withFolder(String newFolder) {
        return new Feed(id, title, url, siteUrl, newFolder, country, language);
    }

    public Feed withSiteUrl(String newSiteUrl) {
        return new Feed(id, title, url, newSiteUrl, folder, country, language);
    }

    /** The first 16 hex digits of the SHA-1 of the address: short, file-name safe and the same on every computer. */
    public static String idOf(String url) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(url.strip().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** {@code www.example.org} for {@code https://www.example.org/feed}; the address itself if it has no host. */
    public static String hostOf(String url) {
        try {
            String host = URI.create(url.strip()).getHost();
            return host == null ? url : host;
        } catch (IllegalArgumentException e) {
            return url;
        }
    }
}

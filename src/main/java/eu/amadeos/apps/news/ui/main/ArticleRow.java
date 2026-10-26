package eu.amadeos.apps.news.ui.main;

import eu.amadeos.apps.news.domain.Article;

/**
 * An article as the list and the reading pane show it.
 *
 * @param feedTitle the name of its feed
 * @param flag      country code of the feed, for its flag; empty if none
 * @param when      short time for the list, e.g. {@code 07:12} or {@code yesterday 22:40}
 * @param fullDate  date and time for the reading pane
 */
public record ArticleRow(Article article, String feedTitle, String flag, String when, String fullDate) {
    public String key() {
        return article.key();
    }
}

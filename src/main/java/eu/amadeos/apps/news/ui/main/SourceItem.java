package eu.amadeos.apps.news.ui.main;

import eu.amadeos.apps.news.domain.Source;

/**
 * One line of the list on the left.
 *
 * @param label  the name shown
 * @param unread unread articles in it
 * @param flag   country code of a feed from Europe's News, for its flag; empty otherwise
 * @param indent true for a feed inside a folder
 * @param error  why the feed's last update failed, already in words; empty if it worked
 */
public record SourceItem(Source source, String label, int unread, String flag, boolean indent, String error) {
}

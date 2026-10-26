package eu.amadeos.apps.news.domain;

import java.util.List;

/**
 * The preferences.
 *
 * @param updateMinutes  how often all feeds are updated; 0 for only by hand
 * @param updateAtStart  update when News starts
 * @param keepDays       articles older than this are removed, unless starred or on Read Later
 * @param textSize       text size of the reading pane in points
 * @param showPictures   load and show the articles' pictures
 * @param markReadOnShow an article counts as read once it was shown in the reading pane
 */
public record Settings(int updateMinutes, boolean updateAtStart, int keepDays, int textSize, boolean showPictures,
                       boolean markReadOnShow) {
    public static final List<Integer> UPDATE_CHOICES = List.of(15, 30, 60, 120, 0);
    public static final List<Integer> KEEP_CHOICES = List.of(7, 14, 30, 90, 365);
    public static final List<Integer> TEXT_SIZES = List.of(11, 13, 15, 18);
    public static final Settings DEFAULT = new Settings(30, true, 30, 13, true, true);

    public Settings {
        updateMinutes = Math.max(0, updateMinutes);
        keepDays = Math.max(1, keepDays);
        textSize = Math.clamp(textSize, 8, 32);
    }
}

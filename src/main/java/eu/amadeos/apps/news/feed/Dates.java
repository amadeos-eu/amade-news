package eu.amadeos.apps.news.feed;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The dates feeds use: RFC 822 in RSS ({@code Mon, 26 Oct 2026 07:12:00 +0100}) and ISO 8601 in Atom and Dublin Core
 * ({@code 2026-10-26T07:12:00Z}), read leniently: missing weekday or seconds, two-digit years, zone names like
 * {@code CEST}, and German, French, Italian and Spanish month abbreviations that some European feeds write.
 */
public final class Dates {
    private static final Pattern RFC822 = Pattern.compile(
            "(?:[\\p{L}]+\\.?,?\\s+)?(\\d{1,2})[\\s-]+([\\p{L}]{3,})\\.?[\\s-]+(\\d{2,4})\\s+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?"
                    + "(?:\\.\\d+)?\\s*([+-]\\d{2}:?\\d{2}|[A-Za-z]{1,5})?.*");
    private static final Pattern ISO_NO_COLON = Pattern.compile("(.*T\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d+)?)?)([+-]\\d{2})(\\d{2})");
    private static final Map<String, Integer> ZONES = Map.ofEntries(
            Map.entry("GMT", 0), Map.entry("UT", 0), Map.entry("UTC", 0), Map.entry("Z", 0),
            Map.entry("WET", 0), Map.entry("WEST", 60), Map.entry("BST", 60), Map.entry("IST", 60),
            Map.entry("CET", 60), Map.entry("MET", 60), Map.entry("CEST", 120), Map.entry("MEST", 120),
            Map.entry("EET", 120), Map.entry("EEST", 180), Map.entry("MSK", 180),
            Map.entry("EST", -300), Map.entry("EDT", -240), Map.entry("CST", -360), Map.entry("CDT", -300),
            Map.entry("MST", -420), Map.entry("MDT", -360), Map.entry("PST", -480), Map.entry("PDT", -420));
    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("jan", 1), Map.entry("feb", 2), Map.entry("mar", 3), Map.entry("apr", 4), Map.entry("may", 5),
            Map.entry("jun", 6), Map.entry("jul", 7), Map.entry("aug", 8), Map.entry("sep", 9), Map.entry("oct", 10),
            Map.entry("nov", 11), Map.entry("dec", 12),
            // German
            Map.entry("jän", 1), Map.entry("mär", 3), Map.entry("mrz", 3), Map.entry("mai", 5), Map.entry("okt", 10),
            Map.entry("dez", 12),
            // French
            Map.entry("fév", 2), Map.entry("fev", 2), Map.entry("avr", 4), Map.entry("juin", 6), Map.entry("juil", 7),
            Map.entry("aoû", 8), Map.entry("aou", 8), Map.entry("déc", 12),
            // Italian and Spanish
            Map.entry("gen", 1), Map.entry("ene", 1), Map.entry("abr", 4), Map.entry("mag", 5), Map.entry("giu", 6),
            Map.entry("lug", 7), Map.entry("ago", 8), Map.entry("set", 9), Map.entry("ott", 10), Map.entry("dic", 12));

    private Dates() {
    }

    /** The moment the text names; empty if it is not a date News can read. */
    public static Optional<Instant> parse(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        String s = text.strip();
        if (!s.isEmpty() && Character.isDigit(s.charAt(0)) && s.length() >= 10 && s.charAt(4) == '-') {
            return iso(s);
        }
        return rfc822(s);
    }

    private static Optional<Instant> iso(String text) {
        String s = text.replace(' ', 'T');
        Matcher noColon = ISO_NO_COLON.matcher(s);
        if (noColon.matches()) s = noColon.group(1) + noColon.group(2) + ":" + noColon.group(3);
        try {
            return Optional.of(OffsetDateTime.parse(s).toInstant());
        } catch (DateTimeParseException e) {
            // no offset, or only a date
        }
        try {
            return Optional.of(LocalDateTime.parse(s).toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException e) {
            // only a date
        }
        try {
            return Optional.of(LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s).atStartOfDay().toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static Optional<Instant> rfc822(String text) {
        Matcher m = RFC822.matcher(text);
        if (!m.matches()) return Optional.empty();
        Integer month = month(m.group(2));
        if (month == null) return Optional.empty();
        int year = Integer.parseInt(m.group(3));
        if (year < 100) year += year < 70 ? 2000 : 1900;
        int seconds = m.group(6) == null ? 0 : Integer.parseInt(m.group(6));
        try {
            LocalDateTime local = LocalDateTime.of(year, month, Integer.parseInt(m.group(1)),
                    Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)), seconds);
            return Optional.of(local.toInstant(offset(m.group(7))));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }

    private static Integer month(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.length() >= 4 && MONTHS.containsKey(n.substring(0, 4))) return MONTHS.get(n.substring(0, 4));
        return MONTHS.get(n.substring(0, 3));
    }

    private static ZoneOffset offset(String zone) {
        if (zone == null || zone.isBlank()) return ZoneOffset.UTC;
        if (zone.startsWith("+") || zone.startsWith("-")) {
            String digits = zone.replace(":", "");
            int hours = Integer.parseInt(digits.substring(1, 3));
            int minutes = Integer.parseInt(digits.substring(3, 5));
            int sign = zone.startsWith("-") ? -1 : 1;
            return ZoneOffset.ofHoursMinutes(sign * hours, sign * minutes);
        }
        Integer minutes = ZONES.get(zone.toUpperCase(Locale.ROOT));
        return minutes == null ? ZoneOffset.UTC : ZoneOffset.ofTotalSeconds(minutes * 60);
    }
}

package com.disha;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hides road and place names in a log before it is shared. Pure Java so it can be tested on a PC.
 *
 * Only the Google Maps text fields (title=[...] text=[...] ...) carry names. In those, numbers,
 * units, times and the usual navigation words are kept, so the parser can still be debugged;
 * every other run of words becomes [name].
 */
final class LogRedactor {
    private LogRedactor() {}

    static final String NAME = "[name]";

    /** Words Google Maps uses around the names; everything else in a field is treated as a name. */
    private static final Set<String> KEEP = new HashSet<>(Arrays.asList(
            "turn", "left", "right", "slight", "sharp", "keep", "stay", "make", "u-turn", "uturn",
            "continue", "straight", "head", "go", "take", "exit", "exits", "roundabout", "merge",
            "ramp", "fork", "onto", "on", "to", "toward", "towards", "via", "the", "a", "an", "at",
            "and", "then", "of", "in", "for", "your", "you", "will", "be", "is", "are", "have",
            "has", "arrive", "arrived", "arriving", "destination", "drive", "walk", "rerouting",
            "north", "south", "east", "west", "northeast", "northwest", "southeast", "southwest",
            "first", "second", "third", "fourth", "fifth", "sixth",
            "m", "km", "mi", "ft", "min", "mins", "hr", "hrs", "h", "am", "pm", "eta"));

    /** A Maps field: key=[value]. Values never contain ']' in practice. */
    private static final Pattern FIELD = Pattern.compile("\\b(title|text|sub|big)=\\[([^\\]]*)\\]");
    /** A word starts with a letter and may contain digits, so road numbers (A2, NH44) are words. */
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{M}][\\p{L}\\p{M}\\p{N}'’-]*");
    /** Ordinal endings: kept only straight after a digit (2nd exit), not as a word (St. Mary's). */
    private static final Set<String> ORDINAL = new HashSet<>(Arrays.asList("st", "nd", "rd", "th"));
    private static final Pattern NAME_RUN =
            Pattern.compile(Pattern.quote(NAME) + "(?:[\\s  ]+" + Pattern.quote(NAME) + ")+");

    /** The whole log with names hidden. */
    static String redact(String log) {
        StringBuilder out = new StringBuilder(log.length());
        for (String line : log.split("\n", -1)) {
            if (out.length() > 0) out.append('\n');
            out.append(redactLine(line));
        }
        return out.toString();
    }

    static String redactLine(String line) {
        // Older versions saved arrow pictures named after the road.
        int saved = line.indexOf("Saved icon ");
        if (saved >= 0) return line.substring(0, saved) + "Saved icon " + NAME;
        if (!line.contains("=[")) return line;
        Matcher m = FIELD.matcher(line);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + "=[" + redactText(m.group(2)) + "]"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Keeps numbers, units and navigation words; each run of other words becomes [name]. */
    static String redactText(String s) {
        Matcher m = WORD.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String w = m.group();
            String lw = w.toLowerCase(Locale.ROOT);
            boolean afterDigit = m.start() > 0 && Character.isDigit(s.charAt(m.start() - 1));
            String keep = KEEP.contains(lw) || (afterDigit && ORDINAL.contains(lw)) ? w : NAME;
            m.appendReplacement(sb, Matcher.quoteReplacement(keep));
        }
        m.appendTail(sb);
        return NAME_RUN.matcher(sb).replaceAll(Matcher.quoteReplacement(NAME));
    }
}

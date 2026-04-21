package com.cleanmediamanager.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FilenameParser {

    /**
     * Stopwords signal the start of release metadata — everything from the first
     * stopword onwards is noise (FileBot's "substringBefore" approach).
     * Ordered longest-first within each group to avoid partial matches.
     */
    private static final Pattern STOPWORD_PATTERN = Pattern.compile(
            "(?i)(?<![a-zA-Z\\d])(" +
            // Resolution
            "2160p|1080[pi]|720p|480[pi]|360p|240p|4k|uhd|" +
            // Video source (longest variants first)
            "blu[\\-.]?ray|bdrip|brrip|dvdrip|dvdscr|dvdr|web[\\-.]?dl|webrip|hdrip|hdtv|pdtv|remux|" +
            // Video codec
            "x\\.?265|x\\.?264|h\\.?265|h\\.?264|hevc|avc|xvid|divx|" +
            // Audio codec (longest variants first)
            "truehd|eac3|dts[\\-.]?(?:hd|ma|x)?|atmos|flac|aac|ac3|mp3|" +
            // Release tags
            "proper|repack|extended|theatrical|unrated|limited|dubbed|multi|hdr10\\+?|hdr|sdr|10bit|8bit|dovi|dolby" +
            ")(?![a-zA-Z\\d])"
    );

    // Strict: year explicitly wrapped in brackets or parens, e.g. (2014) or [2014]
    private static final Pattern BRACKET_YEAR_PATTERN = Pattern.compile(
            "[\\[\\(]((?:19|20)\\d{2})[\\]\\)]"
    );

    // Non-strict: non-greedy capture of title + first year occurrence.
    // (.+?) ensures at least one character before the year, which handles titles
    // that themselves contain a year (e.g. "2001: A Space Odyssey 1968").
    private static final Pattern TITLE_YEAR_PATTERN = Pattern.compile(
            "^(.+?)((?:19|20)\\d{2})(?!\\d)"
    );

    // Any remaining bracket content, e.g. "(Director's Cut)" left after extraction
    private static final Pattern BRACKET_CONTENT = Pattern.compile(
            "\\[[^\\[\\]]*\\]|\\([^()]*\\)|\\{[^{}]*\\}"
    );

    // Trailing separators/punctuation
    private static final Pattern TRAILING_SEPARATORS = Pattern.compile(
            "[\\s\\-_,;:!?.]+$"
    );

    public static class ParseResult {
        private final String title;
        private final String year;

        public ParseResult(String title, String year) {
            this.title = title;
            this.year = year;
        }

        public String getTitle() { return title; }
        public String getYear() { return year; }
    }

    public ParseResult parse(String filename) {
        String name = removeExtension(filename);

        // Normalize word separators to spaces (dots and underscores)
        name = name.replace('.', ' ').replace('_', ' ');

        String year = null;
        String title;

        // Phase 1 (strict): year is explicitly in brackets/parens → most reliable
        Matcher bracketYear = BRACKET_YEAR_PATTERN.matcher(name);
        if (bracketYear.find()) {
            year = bracketYear.group(1);
            title = name.substring(0, bracketYear.start()).trim();
        } else {
            // Phase 2: cut at the first release-info stopword (FileBot's substringBefore)
            String head = substringBefore(name);

            // Phase 3: extract year from the cleaned head using non-greedy match
            Matcher titleYear = TITLE_YEAR_PATTERN.matcher(head);
            if (titleYear.find()) {
                title = titleYear.group(1);
                year = titleYear.group(2);
            } else {
                title = head;
            }
        }

        title = cleanTitle(title);
        return new ParseResult(title.isEmpty() ? removeExtension(filename) : title, year);
    }

    /**
     * Returns the substring before the first stopword match (FileBot approach).
     * Falls back to the original string when the remaining text would be too short.
     */
    private String substringBefore(String name) {
        Matcher m = STOPWORD_PATTERN.matcher(name);
        if (m.find()) {
            String before = name.substring(0, m.start()).trim();
            if (before.length() >= 2) {
                return before;
            }
        }
        return name;
    }

    /**
     * Removes leftover bracket content and trailing punctuation/separators,
     * then normalises internal whitespace.
     */
    private String cleanTitle(String title) {
        title = BRACKET_CONTENT.matcher(title).replaceAll(" ");
        title = TRAILING_SEPARATORS.matcher(title).replaceAll("");
        title = title.replaceAll("\\s+", " ").trim();
        return title;
    }

    private String removeExtension(String filename) {
        int dotIdx = filename.lastIndexOf('.');
        if (dotIdx > 0) {
            return filename.substring(0, dotIdx);
        }
        return filename;
    }
}

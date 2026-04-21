package com.cleanmediamanager.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FilenameParser {

    // Patterns to remove noise
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b((?:19|20)\\d{2})\\b");
    private static final Pattern RESOLUTION_PATTERN = Pattern.compile(
            "(?i)\\b(4k|2160p|1080p|720p|480p|360p|uhd|hd)\\b");
    private static final Pattern ENCODING_PATTERN = Pattern.compile(
            "(?i)\\b(x264|x265|h\\.264|h264|h\\.265|h265|hevc|avc|xvid|divx|aac|ac3|dts|mp3|flac)\\b");
    private static final Pattern SOURCE_PATTERN = Pattern.compile(
            "(?i)\\b(bluray|blu-ray|bdrip|brrip|dvdrip|dvdscr|webrip|web-dl|webdl|hdrip|hdtv|pdtv|ts|cam|remux)\\b");
    private static final Pattern RELEASE_GROUP_PATTERN = Pattern.compile("\\[.*?\\]|\\{.*?\\}");
    private static final Pattern NOISE_EXTRAS_PATTERN = Pattern.compile(
            "(?i)\\b(proper|repack|extended|theatrical|directors\\.cut|unrated|limited|dubbed|multi|hdr|sdr|10bit|dovi|atmos|truehd)\\b");

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
        // Remove file extension
        String name = removeExtension(filename);

        // Replace dots and underscores with spaces
        name = name.replace('.', ' ').replace('_', ' ');

        // Extract year before removing it
        String year = extractYear(name);

        // Remove everything from year onwards if found, or clean noise
        if (year != null) {
            int yearIdx = name.indexOf(year);
            if (yearIdx > 0) {
                name = name.substring(0, yearIdx);
                // Remove any trailing open bracket/parenthesis left by year extraction
                name = name.replaceAll("[\\(\\[\\{]\\s*$", "").trim();
            }
        }

        // Remove remaining noise patterns
        name = RESOLUTION_PATTERN.matcher(name).replaceAll(" ");
        name = ENCODING_PATTERN.matcher(name).replaceAll(" ");
        name = SOURCE_PATTERN.matcher(name).replaceAll(" ");
        name = RELEASE_GROUP_PATTERN.matcher(name).replaceAll(" ");
        name = NOISE_EXTRAS_PATTERN.matcher(name).replaceAll(" ");

        // Remove parentheses/brackets content
        name = name.replaceAll("\\(.*?\\)", " ");
        name = name.replaceAll("-\\s*$", "").trim();

        // Clean extra spaces
        name = name.replaceAll("\\s+", " ").trim();

        return new ParseResult(name.isEmpty() ? filename : name, year);
    }

    private String extractYear(String name) {
        Matcher m = YEAR_PATTERN.matcher(name);
        String lastYear = null;
        while (m.find()) {
            lastYear = m.group(1);
        }
        return lastYear;
    }

    private String removeExtension(String filename) {
        int dotIdx = filename.lastIndexOf('.');
        if (dotIdx > 0) {
            return filename.substring(0, dotIdx);
        }
        return filename;
    }
}

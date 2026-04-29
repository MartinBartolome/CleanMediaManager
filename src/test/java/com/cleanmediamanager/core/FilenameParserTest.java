package com.cleanmediamanager.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class FilenameParserTest {

    private final FilenameParser parser = new FilenameParser();

    @Test
    public void testInceptionWithYear() {
        FilenameParser.ParseResult result = parser.parse("Inception.2010.1080p.BluRay.x264.mkv");
        assertEquals("Inception", result.getTitle());
        assertEquals("2010", result.getYear());
    }

    @Test
    public void testTheMatrixWithYear() {
        FilenameParser.ParseResult result = parser.parse("The.Matrix.1999.720p.WEBRip.mp4");
        assertEquals("The Matrix", result.getTitle());
        assertEquals("1999", result.getYear());
    }

    @Test
    public void testInterstellarWithBrackets() {
        FilenameParser.ParseResult result = parser.parse("Interstellar (2014) [1080p].mkv");
        assertEquals("Interstellar", result.getTitle());
        assertEquals("2014", result.getYear());
    }

    @Test
    public void testAvatarWithSpace() {
        FilenameParser.ParseResult result = parser.parse("avatar 2009.mkv");
        assertEquals("avatar", result.getTitle());
        assertEquals("2009", result.getYear());
    }

    @Test
    public void testUnknownMovieNoYear() {
        FilenameParser.ParseResult result = parser.parse("unknownmovie.mkv");
        assertEquals("unknownmovie", result.getTitle());
        assertNull(result.getYear());
    }

    // --- Additional edge cases ---

    @Test
    public void testMovieTitleContainingYear() {
        // Non-greedy pattern must pick up the film year, not the title year
        FilenameParser.ParseResult result = parser.parse("2001.A.Space.Odyssey.1968.mkv");
        assertEquals("2001 A Space Odyssey", result.getTitle());
        assertEquals("1968", result.getYear());
    }

    @Test
    public void testSubstringBeforeStopword() {
        // No year – stopword "BluRay" signals end of title
        FilenameParser.ParseResult result = parser.parse("Blade.Runner.BluRay.x264.mkv");
        assertEquals("Blade Runner", result.getTitle());
        assertNull(result.getYear());
    }

    @Test
    public void testRemux4K() {
        FilenameParser.ParseResult result = parser.parse("Dune.2021.2160p.UHD.BluRay.REMUX.mkv");
        assertEquals("Dune", result.getTitle());
        assertEquals("2021", result.getYear());
    }

    @Test
    public void testHyphenatedSource() {
        FilenameParser.ParseResult result = parser.parse("The.Dark.Knight.2008.WEB-DL.x264.mkv");
        assertEquals("The Dark Knight", result.getTitle());
        assertEquals("2008", result.getYear());
    }

    @Test
    public void testBracketYearSquare() {
        FilenameParser.ParseResult result = parser.parse("Joker [2019] [BluRay].mkv");
        assertEquals("Joker", result.getTitle());
        assertEquals("2019", result.getYear());
    }

    @Test
    public void testTrailingDashCleanup() {
        // Release-group suffix after year should not bleed into title
        FilenameParser.ParseResult result = parser.parse("Fight.Club.1999.BluRay.x264-GROUP.mkv");
        assertEquals("Fight Club", result.getTitle());
        assertEquals("1999", result.getYear());
    }

    // --- Absolute / zero-padded episode number ---

    @Test
    public void testAbsoluteEpisodeZeroPadded3Digits() {
        // "DB 001 - Title" → series title "DB", season 1, episode 1
        FilenameParser.EpisodeParseResult r = parser.parseEpisode("DB 001 - Das Geheimnis der Dragonballs.avi");
        assertEquals("DB", r.getTitle());
        assertEquals(1, r.getSeason());
        assertEquals(1, r.getEpisode());
        assertTrue(r.hasEpisodeInfo());
    }

    @Test
    public void testAbsoluteEpisodeZeroPadded2Digits() {
        // "Show 01 - Title" → season 1, episode 1
        FilenameParser.EpisodeParseResult r = parser.parseEpisode("MyShow 01 - Episode Title.mkv");
        assertEquals("MyShow", r.getTitle());
        assertEquals(1, r.getSeason());
        assertEquals(1, r.getEpisode());
    }

    @Test
    public void testAbsoluteEpisode3DigitsNoLeadingZero() {
        // "Series 123 - Title" → season 1, episode 123
        FilenameParser.EpisodeParseResult r = parser.parseEpisode("Series 123 - Episode Title.mkv");
        assertEquals("Series", r.getTitle());
        assertEquals(1, r.getSeason());
        assertEquals(123, r.getEpisode());
    }

    @Test
    public void testStandardSxxExxNotAffected() {
        // Standard S01E05 must still take priority
        FilenameParser.EpisodeParseResult r = parser.parseEpisode("Breaking Bad S01E05 720p.mkv");
        assertEquals(1, r.getSeason());
        assertEquals(5, r.getEpisode());
    }

    @Test
    public void testYearNotMistakenForEpisode() {
        // 4-digit years must not be matched as episode numbers
        FilenameParser.EpisodeParseResult r = parser.parseEpisode("Series 1999 Episode.mkv");
        assertEquals(0, r.getSeason());
        assertEquals(0, r.getEpisode());
    }

    // --- Umlaut / non-ASCII title tests ---

    @Test
    public void testMovieTitleWithUmlaut() {
        // Standard dot-separated file with umlaut in title
        FilenameParser.ParseResult r = parser.parse("Ärger.im.Amt.2021.1080p.BluRay.mkv");
        assertEquals("Ärger im Amt", r.getTitle());
        assertEquals("2021", r.getYear());
    }

    @Test
    public void testMovieTitleUmlautAdjacentToStopword() {
        // Umlaut immediately before a stopword without separator - stopword must NOT be matched
        // because the adjacent non-ASCII char is still a letter (\p{L})
        FilenameParser.ParseResult r = parser.parse("Schüöhevc.mkv");
        assertEquals("Schüöhevc", r.getTitle());
        assertNull(r.getYear());
    }

    @Test
    public void testMovieTitleStopwordFollowedByUmlaut() {
        // Stopword that is immediately followed by a non-ASCII char must NOT be treated as stopword
        FilenameParser.ParseResult r = parser.parse("Filmtitel.multiübergang.mkv");
        assertEquals("Filmtitel multiübergang", r.getTitle());
        assertNull(r.getYear());
    }

    @Test
    public void testSeriesTitleWithUmlaut() {
        FilenameParser.EpisodeParseResult r = parser.parseEpisode("Löwenherz.S01E02.1080p.mkv");
        assertEquals("Löwenherz", r.getTitle());
        assertEquals(1, r.getSeason());
        assertEquals(2, r.getEpisode());
    }

    @Test
    public void testSeriesTitleWithMultipleUmlauts() {
        FilenameParser.EpisodeParseResult r = parser.parseEpisode("Tschüss.Ärger.S03E07.720p.mkv");
        assertEquals("Tschüss Ärger", r.getTitle());
        assertEquals(3, r.getSeason());
        assertEquals(7, r.getEpisode());
    }

    @Test
    public void testMovieTitleWithUmlautAndBracketYear() {
        FilenameParser.ParseResult r = parser.parse("Über die Grenze (2019) [BluRay].mkv");
        assertEquals("Über die Grenze", r.getTitle());
        assertEquals("2019", r.getYear());
    }
}

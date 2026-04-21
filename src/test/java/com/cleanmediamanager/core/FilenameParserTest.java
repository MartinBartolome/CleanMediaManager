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

    // --- Additional FileBot-inspired cases ---

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
}

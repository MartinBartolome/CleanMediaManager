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
}

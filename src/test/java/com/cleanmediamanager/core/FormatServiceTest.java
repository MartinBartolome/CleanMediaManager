package com.cleanmediamanager.core;

import com.cleanmediamanager.model.MediaFile;
import com.cleanmediamanager.model.MovieMatch;
import org.junit.Test;

import java.nio.file.Paths;

import static org.junit.Assert.*;

public class FormatServiceTest {

    private final FormatService service = new FormatService();

    @Test
    public void testInceptionFormat() {
        MediaFile file = new MediaFile(Paths.get("Inception.2010.1080p.mkv"));
        MovieMatch match = new MovieMatch(1, "Inception", "2010", "A dream within a dream.");
        String result = service.format(file, match);
        assertEquals("Inception (2010).mkv", result);
    }

    @Test
    public void testTheMatrixFormat() {
        MediaFile file = new MediaFile(Paths.get("The.Matrix.1999.mp4"));
        MovieMatch match = new MovieMatch(2, "The Matrix", "1999", "Follow the white rabbit.");
        String result = service.format(file, match);
        assertEquals("The Matrix (1999).mp4", result);
    }

    @Test
    public void testNullMatchReturnsNull() {
        MediaFile file = new MediaFile(Paths.get("unknown.avi"));
        String result = service.format(file, null);
        assertNull(result);
    }
}

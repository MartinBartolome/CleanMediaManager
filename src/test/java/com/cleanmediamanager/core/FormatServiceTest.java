package com.cleanmediamanager.core;

import com.cleanmediamanager.model.EpisodeMatch;
import com.cleanmediamanager.model.MediaFile;
import com.cleanmediamanager.model.MovieMatch;
import com.cleanmediamanager.model.SeriesMatch;
import org.junit.Test;
import org.junit.Before;
import java.util.prefs.Preferences;

import java.nio.file.Paths;

import static org.junit.Assert.*;

public class FormatServiceTest {

    private final FormatService service = new FormatService();

    @Before
    public void resetPreferences() {
        Preferences node = Preferences.userRoot().node("com/cleanmediamanager");
        node.put("format.movie", "{title} ({year}){ext}");
        node.put("format.episode", "{series} - S{season:02d}E{episode:02d} - {title}{ext}");
    }

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

    // --- sanitizeFilename tests ---

    @Test
    public void testSanitizeColon() {
        assertEquals("Batman - Begins", service.sanitizeFilename("Batman: Begins"));
    }

    @Test
    public void testSanitizeQuestionMark() {
        assertEquals("Who Am I", service.sanitizeFilename("Who Am I?"));
    }

    @Test
    public void testSanitizeForbiddenChars() {
        assertEquals("title", service.sanitizeFilename("ti*t\"le<>|"));
    }

    @Test
    public void testSanitizeBackslash() {
        assertEquals("ACDC", service.sanitizeFilename("AC\\DC"));
    }

    @Test
    public void testSanitizeCollapseSpaces() {
        assertEquals("A B", service.sanitizeFilename("A   B"));
    }

    @Test
    public void testSanitizeTrimDotsAndSpaces() {
        assertEquals("title", service.sanitizeFilename("..title.."));
    }

    @Test
    public void testSanitizeNull() {
        assertNull(service.sanitizeFilename(null));
    }

    @Test
    public void testFormatMovieTitleWithColon() {
        MediaFile file = new MediaFile(Paths.get("batman.begins.2005.mkv"));
        MovieMatch match = new MovieMatch(3, "Batman: Begins", "2005", "");
        String result = service.format(file, match);
        assertEquals("Batman - Begins (2005).mkv", result);
    }

    @Test
    public void testFormatMovieWithTmdbId() {
        Preferences node = Preferences.userRoot().node("com/cleanmediamanager");
        node.put("format.movie", "{title} ({year}) {tmdbid}{ext}");
        MediaFile file = new MediaFile(Paths.get("movie.mkv"));
        MovieMatch match = new MovieMatch(12345, "Best Movie Ever", "2019", "");
        String result = service.format(file, match);
        assertEquals("Best Movie Ever (2019) [tmdbid-12345].mkv", result);
    }

    @Test
    public void testFormatEpisodeWithTmdbId() {
        Preferences node = Preferences.userRoot().node("com/cleanmediamanager");
        node.put("format.episode", "{series} {tmdbid} - S{season:02d}E{episode:02d}{ext}");
        MediaFile file = new MediaFile(Paths.get("s01e01.mkv"));
        SeriesMatch series = new SeriesMatch(67890, "Series Name A", "2010", "");
        EpisodeMatch episode = new EpisodeMatch(1, 1, "Pilot", "");
        String result = service.formatEpisode(file, series, episode);
        assertEquals("Series Name A [tmdbid-67890] - S01E01.mkv", result);
    }

    @Test
    public void testFormatMovieWithImdbId() {
        Preferences node = Preferences.userRoot().node("com/cleanmediamanager");
        node.put("format.movie", "{title} ({year}) {imdbid}{ext}");
        MediaFile file = new MediaFile(Paths.get("movie.mkv"));
        MovieMatch match = new MovieMatch(12345, "Best Movie Ever", "2019", "");
        match.setImdbId("tt12801262");
        String result = service.format(file, match);
        assertEquals("Best Movie Ever (2019) [imdbid-tt12801262].mkv", result);
    }

    @Test
    public void testFormatEpisodeWithImdbId() {
        Preferences node = Preferences.userRoot().node("com/cleanmediamanager");
        node.put("format.episode", "{series} {imdbid} - S{season:02d}E{episode:02d}{ext}");
        MediaFile file = new MediaFile(Paths.get("s01e01.mkv"));
        SeriesMatch series = new SeriesMatch(67890, "Series Name A", "2010", "");
        series.setImdbId("tt9876543");
        EpisodeMatch episode = new EpisodeMatch(1, 1, "Pilot", "");
        String result = service.formatEpisode(file, series, episode);
        assertEquals("Series Name A [imdbid-tt9876543] - S01E01.mkv", result);
    }

    @Test
    public void testFormatMovieWithImdbIdMissing() {
        Preferences node = Preferences.userRoot().node("com/cleanmediamanager");
        node.put("format.movie", "{title} ({year}) {imdbid}{ext}");
        MediaFile file = new MediaFile(Paths.get("movie.mkv"));
        MovieMatch match = new MovieMatch(12345, "Best Movie Ever", "2019", "");
        String result = service.format(file, match);
        assertEquals("Best Movie Ever (2019) .mkv", result);
    }

    @Test
    public void testFormatEpisodeTitleWithColon() {
        MediaFile file = new MediaFile(Paths.get("s01e01.mkv"));
        SeriesMatch series = new SeriesMatch(1, "The X-Files", "1993", "");
        EpisodeMatch episode = new EpisodeMatch(1, 1, "Pilot: The Beginning", "");
        String result = service.formatEpisode(file, series, episode);
        assertEquals("The X-Files - S01E01 - Pilot - The Beginning.mkv", result);
    }
}

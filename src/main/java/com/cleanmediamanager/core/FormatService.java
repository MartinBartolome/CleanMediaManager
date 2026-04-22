package com.cleanmediamanager.core;

import com.cleanmediamanager.model.EpisodeMatch;
import com.cleanmediamanager.model.MediaFile;
import com.cleanmediamanager.model.MovieMatch;
import com.cleanmediamanager.model.SeriesMatch;

public class FormatService {

    public String format(MediaFile file, MovieMatch match) {
        if (match == null) {
            return null;
        }
        String title = sanitizeFilename(match.getTitle());
        String year = match.getYear();
        String ext = getExtension(file.getOriginalName());

        if (year != null && !year.isEmpty()) {
            return title + " (" + year + ")" + ext;
        } else {
            return title + ext;
        }
    }

    public String getExtension(String filename) {
        int dotIdx = filename.lastIndexOf('.');
        if (dotIdx > 0 && dotIdx < filename.length() - 1) {
            return filename.substring(dotIdx);
        }
        return "";
    }

    /**
     * Formats an episode filename as:
     * {@code Series Name - S01E02 - Episode Title.ext}
     */
    public String formatEpisode(MediaFile file, SeriesMatch series, EpisodeMatch episode) {
        String ext = getExtension(file.getOriginalName());
        String seriesName = sanitizeFilename(series.getName());
        String seEp = String.format("S%02dE%02d", episode.getSeason(), episode.getEpisodeNumber());
        String epName = sanitizeFilename(episode.getName());

        if (epName != null && !epName.isBlank()) {
            return seriesName + " - " + seEp + " - " + epName + ext;
        } else {
            return seriesName + " - " + seEp + ext;
        }
    }

    /**
     * Removes or replaces characters that are forbidden in filenames on common
     * operating systems (Windows, Linux, macOS).
     * <ul>
     *   <li>{@code :} is replaced with {@code  -} (common convention for media titles)</li>
     *   <li>{@code \ / * ? " < > |} are removed</li>
     *   <li>ASCII control characters (0x00–0x1F, 0x7F) are removed</li>
     *   <li>Multiple consecutive spaces are collapsed to one</li>
     *   <li>Leading/trailing spaces and dots are trimmed</li>
     * </ul>
     */
    public String sanitizeFilename(String name) {
        if (name == null) return null;
        String s = name.replace(":", " -");
        s = s.replaceAll("[\\\\/*?\"<>|]", "");
        s = s.replaceAll("[\\x00-\\x1F\\x7F]", "");
        s = s.replaceAll(" {2,}", " ");
        s = s.strip();
        s = s.replaceAll("^[.]+|[.]+$", "");
        return s;
    }
}

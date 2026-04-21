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
        String title = match.getTitle();
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
        String seriesName = series.getName();
        String seEp = String.format("S%02dE%02d", episode.getSeason(), episode.getEpisodeNumber());
        String epName = episode.getName();

        if (epName != null && !epName.isBlank()) {
            return seriesName + " - " + seEp + " - " + epName + ext;
        } else {
            return seriesName + " - " + seEp + ext;
        }
    }
}

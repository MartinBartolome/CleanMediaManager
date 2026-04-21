package com.cleanmediamanager.core;

import com.cleanmediamanager.model.MediaFile;
import com.cleanmediamanager.model.MovieMatch;

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
}

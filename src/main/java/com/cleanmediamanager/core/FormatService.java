package com.cleanmediamanager.core;

import com.cleanmediamanager.model.EpisodeMatch;
import com.cleanmediamanager.model.MediaFile;
import com.cleanmediamanager.model.MovieMatch;
import com.cleanmediamanager.model.SeriesMatch;
import java.util.Map;
import java.util.HashMap;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FormatService {

    public String format(MediaFile file, MovieMatch match) {
        if (match == null) {
            return null;
        }
        Preferences node = Preferences.userRoot().node("com/cleanmediamanager");
        String template = node.get("format.movie", "{title} ({year}){ext}");
        Map<String,String> ctx = new HashMap<>();
        ctx.put("title", sanitizeFilename(match.getTitle()));
        ctx.put("year", match.getYear() == null ? "" : match.getYear());
        ctx.put("ext", getExtension(file.getOriginalName()));
        return applyTemplate(template, ctx);
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
        Preferences node = Preferences.userRoot().node("com/cleanmediamanager");
        String template = node.get("format.episode", "{series} - S{season:02d}E{episode:02d} - {title}{ext}");
        Map<String,String> ctx = new HashMap<>();
        ctx.put("series", sanitizeFilename(series.getName()));
        ctx.put("season", String.valueOf(episode.getSeason()));
        ctx.put("episode", String.valueOf(episode.getEpisodeNumber()));
        ctx.put("title", sanitizeFilename(episode.getName()));
        ctx.put("ext", getExtension(file.getOriginalName()));
        return applyTemplate(template, ctx);
    }

    /**
     * Apply a template with simple placeholders like {title}, {series}, {year}, {ext}
     * and optional zero-padding for numeric fields: {season:02d}, {episode:03d}.
     */
    private String applyTemplate(String template, Map<String,String> ctx) {
        if (template == null) return "";
        // Pattern captures name and optional padding like :02d
        Pattern p = Pattern.compile("\\{(\\w+)(?::0(\\d+)d)?\\}");
        Matcher m = p.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            String pad = m.group(2);
            String val = ctx.getOrDefault(key, "");
            if (pad != null && !pad.isEmpty()) {
                try {
                    int width = Integer.parseInt(pad);
                    // try numeric padding
                    long num = Long.parseLong(val.isEmpty() ? "0" : val);
                    String fmt = String.format("%0" + width + "d", num);
                    val = fmt;
                } catch (Exception e) {
                    // fallback: left-pad with zeros to width
                    int width = Integer.parseInt(pad);
                    if (val.length() < width) {
                        val = String.format("%" + width + "s", val).replace(' ', '0');
                    }
                }
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(val));
        }
        m.appendTail(sb);
        return sb.toString();
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

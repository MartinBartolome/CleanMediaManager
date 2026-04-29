package com.cleanmediamanager.core;

import com.cleanmediamanager.api.TmdbClient;
import com.cleanmediamanager.model.EpisodeMatch;
import com.cleanmediamanager.model.MatchStatus;
import com.cleanmediamanager.model.MediaFile;
import com.cleanmediamanager.model.MediaType;
import com.cleanmediamanager.model.SeriesMatch;

import java.util.ArrayList;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class SeriesMatcher {

    private final TmdbClient tmdbClient;
    private final FilenameParser parser;
    private final FormatService formatService;

    public SeriesMatcher(TmdbClient tmdbClient) {
        this.tmdbClient = tmdbClient;
        this.parser = new FilenameParser();
        this.formatService = new FormatService();
    }

    /**
     * Matches all files, batching TMDB requests:
     *  - 1 series search per unique parsed title
     *  - 1 season request per unique (seriesId, season) combination
     */
    public CompletableFuture<Void> matchFiles(List<MediaFile> files, Consumer<MediaFile> onFileUpdated) {
        // Group parseable files by lowercased title; mark unparseable ones immediately
        Map<String, List<MediaFile>> byTitle = new LinkedHashMap<>();
        for (MediaFile file : files) {
            file.setMediaType(MediaType.EPISODE);
            FilenameParser.EpisodeParseResult parsed = parser.parseEpisode(file.getOriginalName());
            if (!parsed.hasEpisodeInfo()) {
                file.setStatus(MatchStatus.UNMATCHED);
                if (onFileUpdated != null) onFileUpdated.accept(file);
            } else {
                byTitle.computeIfAbsent(parsed.getTitle().toLowerCase(Locale.ROOT), k -> new ArrayList<>())
                       .add(file);
            }
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (List<MediaFile> group : byTitle.values()) {
            String title = parser.parseEpisode(group.get(0).getOriginalName()).getTitle();
            futures.add(
                tmdbClient.searchSeries(title, null)
                    .thenCompose(results -> {
                        if (results.isEmpty()) {
                            group.forEach(f -> {
                                f.setStatus(MatchStatus.UNMATCHED);
                                if (onFileUpdated != null) onFileUpdated.accept(f);
                            });
                            return CompletableFuture.completedFuture(null);
                        }
                        SeriesMatch series = results.get(0);
                        group.forEach(f -> f.setSeriesMatch(series));
                        return matchGroupBySeason(group, series, onFileUpdated);
                    })
                    .exceptionally(ex -> {
                        group.forEach(f -> {
                            f.setStatus(MatchStatus.ERROR);
                            if (onFileUpdated != null) onFileUpdated.accept(f);
                        });
                        return null;
                    })
            );
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * Re-matches files against a user-supplied series.
     * Groups by season and fetches each season only once.
     */
    public CompletableFuture<Void> matchFilesWithSeries(List<MediaFile> files,
            SeriesMatch series, Consumer<MediaFile> onFileUpdated) {
        List<MediaFile> parseable = new ArrayList<>();
        for (MediaFile file : files) {
            file.setMediaType(MediaType.EPISODE);
            FilenameParser.EpisodeParseResult parsed = parser.parseEpisode(file.getOriginalName());
            if (!parsed.hasEpisodeInfo()) {
                file.setStatus(MatchStatus.UNMATCHED);
                if (onFileUpdated != null) onFileUpdated.accept(file);
            } else {
                file.setSeriesMatch(series);
                parseable.add(file);
            }
        }
        return matchGroupBySeason(parseable, series, onFileUpdated);
    }

    /**
     * Groups files by season number, fetches each season once, then applies
     * the episode map to every file in that season group.
     */
    private CompletableFuture<Void> matchGroupBySeason(List<MediaFile> files,
            SeriesMatch series, Consumer<MediaFile> onFileUpdated) {
        Map<Integer, List<MediaFile>> bySeason = new LinkedHashMap<>();
        for (MediaFile file : files) {
            int season = parser.parseEpisode(file.getOriginalName()).getSeason();
            bySeason.computeIfAbsent(season, k -> new ArrayList<>()).add(file);
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Map.Entry<Integer, List<MediaFile>> entry : bySeason.entrySet()) {
            int season = entry.getKey();
            List<MediaFile> seasonFiles = entry.getValue();

            futures.add(
                tmdbClient.getSeasonEpisodes(series.getId(), season)
                    .thenAccept(episodeMap -> {
                        for (MediaFile file : seasonFiles) {
                            int epNum = parser.parseEpisode(file.getOriginalName()).getEpisode();
                            EpisodeMatch em = episodeMap.getOrDefault(
                                    epNum, new EpisodeMatch(season, epNum, "", ""));
                            file.setEpisodeMatch(em);
                            file.setStatus(MatchStatus.MATCHED);
                            file.setNewName(formatService.formatEpisode(file, series, em));
                            if (onFileUpdated != null) onFileUpdated.accept(file);
                        }
                    })
                    .exceptionally(ex -> {
                        seasonFiles.forEach(f -> {
                            f.setStatus(MatchStatus.ERROR);
                            if (onFileUpdated != null) onFileUpdated.accept(f);
                        });
                        return null;
                    })
            );
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
}

package com.cleanmediamanager.core;

import com.cleanmediamanager.api.TmdbClient;
import com.cleanmediamanager.model.EpisodeMatch;
import com.cleanmediamanager.model.MatchStatus;
import com.cleanmediamanager.model.MediaFile;
import com.cleanmediamanager.model.MediaType;
import com.cleanmediamanager.model.SeriesMatch;

import java.util.List;
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

    public CompletableFuture<Void> matchFiles(List<MediaFile> files, Consumer<MediaFile> onFileUpdated) {
        CompletableFuture<?>[] futures = files.stream()
                .map(file -> matchFile(file, onFileUpdated))
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    private CompletableFuture<Void> matchFile(MediaFile file, Consumer<MediaFile> onFileUpdated) {
        file.setMediaType(MediaType.EPISODE);

        FilenameParser.EpisodeParseResult parsed = parser.parseEpisode(file.getOriginalName());
        String seriesTitle = parsed.getTitle();
        int season = parsed.getSeason();
        int episode = parsed.getEpisode();

        if (!parsed.hasEpisodeInfo()) {
            file.setStatus(MatchStatus.UNMATCHED);
            if (onFileUpdated != null) onFileUpdated.accept(file);
            return CompletableFuture.completedFuture(null);
        }

        return tmdbClient.searchSeries(seriesTitle, null)
                .thenCompose(seriesResults -> {
                    if (seriesResults.isEmpty()) {
                        file.setStatus(MatchStatus.UNMATCHED);
                        if (onFileUpdated != null) onFileUpdated.accept(file);
                        return CompletableFuture.completedFuture(null);
                    }

                    SeriesMatch bestSeries = seriesResults.get(0);
                    file.setSeriesMatch(bestSeries);

                    return tmdbClient.getEpisodeDetails(bestSeries.getId(), season, episode)
                            .thenAccept(episodeMatch -> {
                                if (episodeMatch == null) {
                                    // Series found but episode details unavailable — still use series info
                                    episodeMatch = new EpisodeMatch(season, episode, "", "");
                                }
                                file.setEpisodeMatch(episodeMatch);
                                file.setStatus(MatchStatus.MATCHED);
                                file.setNewName(formatService.formatEpisode(file, bestSeries, episodeMatch));
                                if (onFileUpdated != null) onFileUpdated.accept(file);
                            });
                })
                .exceptionally(ex -> {
                    file.setStatus(MatchStatus.ERROR);
                    if (onFileUpdated != null) onFileUpdated.accept(file);
                    return null;
                });
    }
}

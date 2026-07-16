package com.cleanmediamanager.core;

import com.cleanmediamanager.api.MetadataProvider;
import com.cleanmediamanager.model.MediaFile;
import com.cleanmediamanager.model.MatchStatus;
import com.cleanmediamanager.model.MovieMatch;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class MovieMatcher {

    private final MetadataProvider tmdbClient;
    private final FilenameParser parser;
    private final FormatService formatService;
    private final MatchScorer scorer;

    public MovieMatcher(MetadataProvider tmdbClient) {
        this.tmdbClient = tmdbClient;
        this.parser = new FilenameParser();
        this.formatService = new FormatService();
        this.scorer = new MatchScorer();
    }

    public CompletableFuture<Void> matchFiles(List<MediaFile> files, Consumer<MediaFile> onFileUpdated) {
        CompletableFuture<?>[] futures = files.stream()
                .map(file -> matchFile(file, onFileUpdated))
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    private CompletableFuture<Void> matchFile(MediaFile file, Consumer<MediaFile> onFileUpdated) {
        FilenameParser.ParseResult parsed = parser.parse(file.getOriginalName());
        String title = parsed.getTitle();
        String year = parsed.getYear();

        return tmdbClient.searchMovie(title, year)
                .thenCompose(matches -> {
                    if (matches != null && !matches.isEmpty()) {
                        MovieMatch best = matches.get(0);
                        double score = scoreBest(title, year, best);
                        file.setMatch(best);
                        file.setMatchScore(score);
                        if (score >= MatchScorer.DEFAULT_THRESHOLD) {
                            file.setStatus(MatchStatus.MATCHED);
                            return tmdbClient.getMovieImdbId(best.getId())
                                    .thenAccept(imdbId -> {
                                        if (imdbId != null) best.setImdbId(imdbId);
                                        String newName = formatService.format(file, best);
                                        file.setNewName(newName);
                                        if (onFileUpdated != null) onFileUpdated.accept(file);
                                    });
                        } else {
                            file.setStatus(MatchStatus.UNMATCHED);
                        }
                        if (onFileUpdated != null) onFileUpdated.accept(file);
                        return CompletableFuture.completedFuture(null);
                    }
                    // Try folder name as fallback when title lookup failed
                    try {
                        if (file.getPath() != null && file.getPath().getParent() != null) {
                            String folder = file.getPath().getParent().getFileName().toString();
                            if (folder != null && !folder.isBlank()) {
                                // Parse the folder name to extract a cleaner title/year
                                FilenameParser.ParseResult folderParsed = parser.parse(folder);
                                String folderTitle = folderParsed.getTitle();
                                String folderYear = folderParsed.getYear();
                                String useYear = folderYear != null ? folderYear : year;
                                return tmdbClient.searchMovie(folderTitle, useYear)
                                        .thenCompose(fm -> {
                                            if (fm != null && !fm.isEmpty()) {
                                                MovieMatch best = fm.get(0);
                                                double score = scoreBest(folderTitle, useYear, best);
                                                file.setMatch(best);
                                                file.setMatchScore(score);
                                                if (score >= MatchScorer.DEFAULT_THRESHOLD) {
                                                    file.setStatus(MatchStatus.MATCHED);
                                                    return tmdbClient.getMovieImdbId(best.getId())
                                                            .thenAccept(imdbId -> {
                                                                if (imdbId != null) best.setImdbId(imdbId);
                                                                String newName = formatService.format(file, best);
                                                                file.setNewName(newName);
                                                                if (onFileUpdated != null) onFileUpdated.accept(file);
                                                            });
                                                } else {
                                                    file.setStatus(MatchStatus.UNMATCHED);
                                                }
                                            } else {
                                                file.setStatus(MatchStatus.UNMATCHED);
                                            }
                                            if (onFileUpdated != null) onFileUpdated.accept(file);
                                            return CompletableFuture.<Void>completedFuture(null);
                                        });
                            }
                        }
                    } catch (Exception ignored) {}
                    file.setStatus(MatchStatus.UNMATCHED);
                    if (onFileUpdated != null) onFileUpdated.accept(file);
                    return CompletableFuture.completedFuture(null);
                })
                .exceptionally(ex -> {
                    file.setStatus(MatchStatus.ERROR);
                    if (onFileUpdated != null) {
                        onFileUpdated.accept(file);
                    }
                    return null;
                });
    }

    /**
     * Scores the top search result, trusting it as a confident match whenever the
     * provider doesn't localize titles (e.g. IMDb): its own server-side AKA/fuzzy
     * matching already resolved the (possibly foreign-language) query, so a plain
     * text-similarity comparison against the original-language title would
     * otherwise wrongly reject a correct match.
     */
    private double scoreBest(String title, String year, MovieMatch best) {
        double score = scorer.scoreMovie(title, year, best);
        if (!tmdbClient.supportsTitleLocalization()) {
            score = Math.max(score, MatchScorer.DEFAULT_THRESHOLD);
        }
        return score;
    }
}

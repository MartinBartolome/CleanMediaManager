package com.cleanmediamanager.core;

import com.cleanmediamanager.api.TmdbClient;
import com.cleanmediamanager.model.MediaFile;
import com.cleanmediamanager.model.MatchStatus;
import com.cleanmediamanager.model.MovieMatch;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class MovieMatcher {

    private final TmdbClient tmdbClient;
    private final FilenameParser parser;
    private final FormatService formatService;

    public MovieMatcher(TmdbClient tmdbClient) {
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
        FilenameParser.ParseResult parsed = parser.parse(file.getOriginalName());
        String title = parsed.getTitle();
        String year = parsed.getYear();

        return tmdbClient.searchMovie(title, year)
                .thenCompose(matches -> {
                    if (matches != null && !matches.isEmpty()) {
                        MovieMatch best = matches.get(0);
                        file.setMatch(best);
                        file.setStatus(MatchStatus.MATCHED);
                        String newName = formatService.format(file, best);
                        file.setNewName(newName);
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
                                        .thenAccept(fm -> {
                                            if (fm != null && !fm.isEmpty()) {
                                                MovieMatch best = fm.get(0);
                                                file.setMatch(best);
                                                file.setStatus(MatchStatus.MATCHED);
                                                String newName = formatService.format(file, best);
                                                file.setNewName(newName);
                                            } else {
                                                file.setStatus(MatchStatus.UNMATCHED);
                                            }
                                            if (onFileUpdated != null) onFileUpdated.accept(file);
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
}

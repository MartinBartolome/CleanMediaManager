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
                .thenAccept(matches -> {
                    if (matches.isEmpty()) {
                        file.setStatus(MatchStatus.UNMATCHED);
                    } else {
                        MovieMatch best = matches.get(0);
                        file.setMatch(best);
                        file.setStatus(MatchStatus.MATCHED);
                        String newName = formatService.format(file, best);
                        file.setNewName(newName);
                    }
                    if (onFileUpdated != null) {
                        onFileUpdated.accept(file);
                    }
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

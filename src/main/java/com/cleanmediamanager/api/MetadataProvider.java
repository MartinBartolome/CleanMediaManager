package com.cleanmediamanager.api;

import com.cleanmediamanager.model.EpisodeMatch;
import com.cleanmediamanager.model.MovieMatch;
import com.cleanmediamanager.model.SeriesMatch;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Abstraction over a metadata/search backend (e.g. TMDB or IMDb) used to look
 * up movies, series, episodes and IMDB ids. Implementations: {@link TmdbClient}
 * (requires an API key) and {@link ImdbClient} (key-free, uses IMDb's public
 * search suggestion endpoint).
 */
public interface MetadataProvider {

    CompletableFuture<List<MovieMatch>> searchMovie(String title, String year);

    CompletableFuture<List<SeriesMatch>> searchSeries(String title, String year);

    CompletableFuture<Map<Integer, EpisodeMatch>> getSeasonEpisodes(int seriesId, int season);

    CompletableFuture<String> getMovieImdbId(int id);

    CompletableFuture<String> getSeriesImdbId(int id);

    /**
     * Whether search results come back with titles localized to the requested
     * display language (as TMDB does via its {@code language} parameter).
     * Returns {@code false} for providers (like {@link ImdbClient}) that always
     * return the original title, relying on their own server-side fuzzy/AKA
     * matching to resolve foreign-language queries instead. Callers use this to
     * avoid penalizing correct matches with a text-similarity based confidence
     * score when the returned title is expected to be in a different language
     * than the parsed filename.
     */
    default boolean supportsTitleLocalization() {
        return true;
    }
}

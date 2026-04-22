package com.cleanmediamanager.api;

import com.cleanmediamanager.model.EpisodeMatch;
import com.cleanmediamanager.model.MovieMatch;
import com.cleanmediamanager.model.SeriesMatch;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

public class TmdbClient {

    private static final String BASE_URL = "https://api.themoviedb.org/3";
    // TMDB allows ~40 requests/10 s; keep concurrent requests well below that.
    private static final int MAX_CONCURRENT = 8;
    private final String apiKey;
    private final String language;
    private final HttpClient httpClient;
    private final Semaphore semaphore = new Semaphore(MAX_CONCURRENT, true);

    public TmdbClient(String apiKey, String language) {
        this.apiKey = apiKey;
        this.language = language != null ? language : "en-US";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** Sends a throttled GET request, respecting the concurrency limit. */
    private CompletableFuture<String> throttledGet(String url) {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CompletableFuture.failedFuture(e);
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .whenComplete((body, ex) -> semaphore.release());
    }

    public CompletableFuture<List<MovieMatch>> searchMovie(String title, String year) {
        if (apiKey == null || apiKey.isBlank()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        try {
            String encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8);
            StringBuilder url = new StringBuilder(BASE_URL)
                    .append("/search/movie")
                    .append("?api_key=").append(apiKey)
                    .append("&query=").append(encodedTitle)
                    .append("&language=").append(language);

            if (year != null && !year.isBlank()) {
                url.append("&year=").append(year);
            }

            return throttledGet(url.toString())
                    .thenApply(body -> parseResponse(body))
                    .exceptionally(ex -> Collections.emptyList());

        } catch (Exception e) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
    }

    private List<MovieMatch> parseResponse(String body) {
        List<MovieMatch> results = new ArrayList<>();
        try {
            JSONObject json = new JSONObject(body);
            JSONArray array = json.optJSONArray("results");
            if (array == null) return results;

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                int id = obj.optInt("id", 0);
                String title = obj.optString("title", "Unknown");
                String releaseDate = obj.optString("release_date", "");
                String year = releaseDate.length() >= 4 ? releaseDate.substring(0, 4) : "";
                String overview = obj.optString("overview", "");
                results.add(new MovieMatch(id, title, year, overview));
            }
        } catch (Exception e) {
            System.err.println("[TmdbClient] Failed to parse API response: " + e.getMessage());
        }
        return results;
    }

    public CompletableFuture<List<SeriesMatch>> searchSeries(String title, String year) {
        if (apiKey == null || apiKey.isBlank()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        try {
            String encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8);
            StringBuilder url = new StringBuilder(BASE_URL)
                    .append("/search/tv")
                    .append("?api_key=").append(apiKey)
                    .append("&query=").append(encodedTitle)
                    .append("&language=").append(language);

            if (year != null && !year.isBlank()) {
                url.append("&first_air_date_year=").append(year);
            }

            return throttledGet(url.toString())
                    .thenApply(body -> parseSeriesResponse(body))
                    .exceptionally(ex -> Collections.emptyList());

        } catch (Exception e) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
    }

    private List<SeriesMatch> parseSeriesResponse(String body) {
        List<SeriesMatch> results = new ArrayList<>();
        try {
            JSONObject json = new JSONObject(body);
            JSONArray array = json.optJSONArray("results");
            if (array == null) return results;

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                int id = obj.optInt("id", 0);
                String name = obj.optString("name", "Unknown");
                String firstAirDate = obj.optString("first_air_date", "");
                String firstAirYear = firstAirDate.length() >= 4 ? firstAirDate.substring(0, 4) : "";
                String overview = obj.optString("overview", "");
                results.add(new SeriesMatch(id, name, firstAirYear, overview));
            }
        } catch (Exception e) {
            System.err.println("[TmdbClient] Failed to parse series response: " + e.getMessage());
        }
        return results;
    }

    public CompletableFuture<Map<Integer, EpisodeMatch>> getSeasonEpisodes(int seriesId, int season) {
        if (apiKey == null || apiKey.isBlank()) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        try {
            String url = BASE_URL
                    + "/tv/" + seriesId
                    + "/season/" + season
                    + "?api_key=" + apiKey
                    + "&language=" + language;

            return throttledGet(url)
                    .thenApply(body -> parseSeasonResponse(body))
                    .exceptionally(ex -> Collections.emptyMap());

        } catch (Exception e) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }
    }

    private Map<Integer, EpisodeMatch> parseSeasonResponse(String body) {
        Map<Integer, EpisodeMatch> result = new LinkedHashMap<>();
        try {
            JSONObject json = new JSONObject(body);
            JSONArray episodes = json.optJSONArray("episodes");
            if (episodes == null) return result;
            for (int i = 0; i < episodes.length(); i++) {
                JSONObject ep = episodes.getJSONObject(i);
                int epNum = ep.optInt("episode_number", 0);
                int seNum = ep.optInt("season_number", 0);
                String name = ep.optString("name", "");
                String overview = ep.optString("overview", "");
                result.put(epNum, new EpisodeMatch(seNum, epNum, name, overview));
            }
        } catch (Exception e) {
            System.err.println("[TmdbClient] Failed to parse season response: " + e.getMessage());
        }
        return result;
    }
}

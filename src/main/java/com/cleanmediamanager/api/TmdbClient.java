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
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class TmdbClient {

    private static final String BASE_URL = "https://api.themoviedb.org/3";
    private final String apiKey;
    private final String language;
    private final HttpClient httpClient;

    public TmdbClient(String apiKey, String language) {
        this.apiKey = apiKey;
        this.language = language != null ? language : "en-US";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
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

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> parseResponse(response.body()))
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

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> parseSeriesResponse(response.body()))
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

    public CompletableFuture<EpisodeMatch> getEpisodeDetails(int seriesId, int season, int episode) {
        if (apiKey == null || apiKey.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            String url = BASE_URL
                    + "/tv/" + seriesId
                    + "/season/" + season
                    + "/episode/" + episode
                    + "?api_key=" + apiKey
                    + "&language=" + language;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> parseEpisodeResponse(response.body(), season, episode))
                    .exceptionally(ex -> null);

        } catch (Exception e) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private EpisodeMatch parseEpisodeResponse(String body, int season, int episode) {
        try {
            JSONObject obj = new JSONObject(body);
            String name = obj.optString("name", "");
            String overview = obj.optString("overview", "");
            int epNum = obj.optInt("episode_number", episode);
            int seNum = obj.optInt("season_number", season);
            return new EpisodeMatch(seNum, epNum, name, overview);
        } catch (Exception e) {
            System.err.println("[TmdbClient] Failed to parse episode response: " + e.getMessage());
            return null;
        }
    }
}

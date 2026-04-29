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
import java.text.Normalizer;
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
    private final boolean debug;
    private final Semaphore semaphore = new Semaphore(MAX_CONCURRENT, true);

    public TmdbClient(String apiKey, String language) {
        this.apiKey = apiKey;
        this.language = language != null ? language : "en-US";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        String prop = System.getProperty("tmdb.debug");
        if (prop == null) prop = System.getenv().getOrDefault("TMDB_DEBUG", "false");
        this.debug = Boolean.parseBoolean(prop);
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
            List<String> tried = new ArrayList<>();
            String trimmed = title != null ? title.trim() : "";
            String withoutTrailingNumber = stripTrailingNumberSuffix(trimmed);

            return searchMovieOnce(trimmed, year, this.language)
                    .thenCompose(list -> {
                        tried.add(makeSearchMovieUrl(trimmed, year, this.language, true));
                        if (!list.isEmpty()) return CompletableFuture.completedFuture(list);
                        // Try restoring umlauts from 'ae/oe/ue' -> 'ä/ö/ü'
                        String uml = restoreGermanUmlauts(trimmed);
                        if (uml != null && !uml.equalsIgnoreCase(trimmed)) {
                            tried.add(makeSearchMovieUrl(uml, year, this.language, true));
                            return searchMovieOnce(uml, year, this.language);
                        }
                        if (withoutTrailingNumber != null && !withoutTrailingNumber.equalsIgnoreCase(trimmed)) {
                            tried.add(makeSearchMovieUrl(withoutTrailingNumber, year, this.language, true));
                            return searchMovieOnce(withoutTrailingNumber, year, this.language);
                        }
                        return CompletableFuture.completedFuture(list);
                    })
                    .thenCompose(list -> {
                        if (!list.isEmpty()) return CompletableFuture.completedFuture(list);
                        String g = toGermanAscii(trimmed);
                        if (!g.equalsIgnoreCase(trimmed)) {
                            tried.add(makeSearchMovieUrl(g, year, this.language, true));
                            return searchMovieOnce(g, year, this.language);
                        }
                        return CompletableFuture.completedFuture(list);
                    })
                    // If still empty, try without the year parameter (some TMDB entries have different years)
                    .thenCompose(list -> {
                        if (!list.isEmpty()) return CompletableFuture.completedFuture(list);
                        if (year != null && !year.isBlank()) {
                            tried.add(makeSearchMovieUrl(trimmed, null, this.language, true));
                            return searchMovieOnce(trimmed, null, this.language);
                        }
                        return CompletableFuture.completedFuture(list);
                    })
                    .thenCompose(list -> {
                        if (!list.isEmpty()) return CompletableFuture.completedFuture(list);
                        String stripped = toAsciiBase(trimmed);
                        if (!stripped.equalsIgnoreCase(trimmed)) {
                            tried.add(makeSearchMovieUrl(stripped, year, this.language, true));
                            return searchMovieOnce(stripped, year, this.language);
                        }
                        if (!"de-DE".equalsIgnoreCase(this.language)) {
                            tried.add(makeSearchMovieUrl(trimmed, year, "de-DE", true));
                            return searchMovieOnce(trimmed, year, "de-DE");
                        }
                        return CompletableFuture.completedFuture(list);
                    })
                    .thenCompose(list -> {
                        if (!list.isEmpty()) return CompletableFuture.completedFuture(list);
                        if (!"de-DE".equalsIgnoreCase(this.language)) {
                            String g2 = toGermanAscii(trimmed);
                            if (!g2.equalsIgnoreCase(trimmed)) {
                                tried.add(makeSearchMovieUrl(g2, year, "de-DE", true));
                                return searchMovieOnce(g2, year, "de-DE");
                            }
                        }
                        return CompletableFuture.completedFuture(list);
                    })
                    .thenCompose(list -> {
                        if (!list.isEmpty()) return CompletableFuture.completedFuture(list);
                        if (!"de-DE".equalsIgnoreCase(this.language)) {
                            String stripped2 = toAsciiBase(trimmed);
                            if (!stripped2.equalsIgnoreCase(trimmed)) {
                                tried.add(makeSearchMovieUrl(stripped2, year, "de-DE", true));
                                return searchMovieOnce(stripped2, year, "de-DE");
                            }
                        }
                        return CompletableFuture.completedFuture(list);
                    })
                    // Try simpler query variants (remove articles/punctuation, shorter word sets)
                    .thenCompose(list -> {
                        if (!list.isEmpty()) return CompletableFuture.completedFuture(list);
                        List<String> vars = generateTitleVariants(trimmed);
                        CompletableFuture<List<MovieMatch>> acc = CompletableFuture.completedFuture(Collections.emptyList());
                        for (String v : vars) {
                            final String q = v;
                            acc = acc.thenCompose(prev -> {
                                if (!prev.isEmpty()) return CompletableFuture.completedFuture(prev);
                                tried.add(makeSearchMovieUrl(q, year, this.language, true));
                                return searchMovieOnce(q, year, this.language);
                            });
                        }
                        return acc;
                    })
                    // Fallback: search English 'en-US' for the first token and filter by mapped keywords
                    .thenCompose(list -> {
                        if (!list.isEmpty()) return CompletableFuture.completedFuture(list);
                        return searchByTokenWithKeywords(trimmed);
                    })
                    // Try translated English phrase (simple German->English token map)
                    .thenCompose(list -> {
                        if (!list.isEmpty()) return CompletableFuture.completedFuture(list);
                        String eng = translateGermanToEnglishPhrase(trimmed);
                        if (eng != null && !eng.isBlank() && !eng.equalsIgnoreCase(trimmed)) {
                            tried.add(makeSearchMovieUrl(eng, year, "en-US", true));
                            return searchMovieOnce(eng, year, "en-US")
                                    .thenCompose(l -> {
                                        if (!l.isEmpty()) return CompletableFuture.completedFuture(l);
                                        tried.add(makeSearchMultiUrl(eng, "en-US", true));
                                        return searchMultiForMovies(eng, "en-US");
                                    });
                        }
                        return CompletableFuture.completedFuture(list);
                    })
                    // Final fallback: try the multi-search endpoint and look for movie results.
                    // First try restoring umlauts (e.g. 'Kuehe' -> 'Kühe'), then the original.
                    .thenCompose(list -> {
                        if (!list.isEmpty()) return CompletableFuture.completedFuture(list);
                        String uml = restoreGermanUmlauts(trimmed);
                        if (uml != null && !uml.equalsIgnoreCase(trimmed)) {
                            tried.add(makeSearchMultiUrl(uml, this.language, true));
                            return searchMultiForMovies(uml, this.language)
                                    .thenCompose(m -> {
                                        if (!m.isEmpty()) return CompletableFuture.completedFuture(m);
                                        tried.add(makeSearchMultiUrl(trimmed, this.language, true));
                                        return searchMultiForMovies(trimmed, this.language);
                                    });
                        }
                        tried.add(makeSearchMultiUrl(trimmed, this.language, true));
                        return searchMultiForMovies(trimmed, this.language);
                    })
                    .thenApply(list -> {
                        if (list.isEmpty() && debug) {
                            System.err.println("[TmdbClient] No movie results for '" + title + "'. Tried URLs:");
                            tried.forEach(u -> System.err.println("  " + u));
                        }
                        return list;
                    })
                    .exceptionally(ex -> {
                        if (debug) ex.printStackTrace();
                        return Collections.emptyList();
                    });

        } catch (Exception e) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
    }

    private CompletableFuture<List<MovieMatch>> searchMovieOnce(String title, String year, String lang) {
        try {
            String encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8);
            StringBuilder url = new StringBuilder(BASE_URL)
                    .append("/search/movie")
                    .append("?api_key=").append(apiKey)
                    .append("&query=").append(encodedTitle);

            if (lang != null && !lang.isBlank()) {
                url.append("&language=").append(lang);
            }

            if (year != null && !year.isBlank()) {
                url.append("&year=").append(year);
            }

            return throttledGet(url.toString())
                    .thenApply(body -> {
                        if (debug) {
                            try {
                                String redacted = makeSearchMovieUrl(title, year, lang, true);
                                String snippet = body == null ? "<null>" : body.substring(0, Math.min(body.length(), 1000));
                                System.err.println("[TmdbClient] Response for '" + title + "' (movie) from: " + redacted + "\n" + snippet);
                            } catch (Exception ignored) {}
                        }
                        return parseResponse(body);
                    })
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

    /** Try a focused English search for the first token and filter results by keywords (German->English map). */
    private CompletableFuture<List<MovieMatch>> searchByTokenWithKeywords(String title) {
        try {
            if (title == null || title.isBlank()) return CompletableFuture.completedFuture(Collections.emptyList());
            String cleaned = title.replaceAll("[,:!()?\"]", " ").replaceAll("\\s+", " ").trim();
            String[] tokens = cleaned.split("\\s+");
            if (tokens.length == 0) return CompletableFuture.completedFuture(Collections.emptyList());
            String first = tokens[0];
            List<String> keywords = new ArrayList<>();
            for (String t : tokens) {
                String l = t.toLowerCase();
                keywords.add(l);
            }
            // map some German words to English equivalents
            Map<String,String> map = Map.of(
                    "maeuse", "mouse",
                    "mäuse", "mouse",
                    "maus", "mouse",
                    "detektiv", "detective",
                    "grosse", "great",
                    "grose", "great",
                    "große", "great"
            );
            for (String t : tokens) {
                String l = t.toLowerCase();
                if (map.containsKey(l)) keywords.add(map.get(l));
            }

            return searchMovieOnce(first, null, "en-US")
                    .thenApply(list -> {
                        if (list == null || list.isEmpty()) return Collections.<MovieMatch>emptyList();
                        List<MovieMatch> out = new ArrayList<>();
                        // Use the results returned by searchMovieOnce and filter by title/overview content.
                        for (MovieMatch m : list) {
                            String t = (m.getTitle() + " " + m.getOverview() + " " + m.getYear()).toLowerCase();
                            for (String k : keywords) {
                                if (t.contains(k)) { out.add(m); break; }
                            }
                        }
                        return out;
                    }).exceptionally(ex -> Collections.<MovieMatch>emptyList());
        } catch (Exception e) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
    }

    private String translateGermanToEnglishPhrase(String title) {
        if (title == null) return null;
        String cleaned = title.replaceAll("[,:!()?\"]", " ").replaceAll("\\s+", " ").trim();
        String[] tokens = cleaned.split("\\s+");
        if (tokens.length == 0) return null;
        Map<String,String> map = new LinkedHashMap<>();
        map.put("maeuse", "mouse");
        map.put("mäuse", "mouse");
        map.put("maus", "mouse");
        map.put("detektiv", "detective");
        map.put("grosse", "great");
        map.put("grose", "great");
        map.put("große", "great");
        map.put("der", "");

        List<String> out = new ArrayList<>();
        for (String t : tokens) {
            String low = t.toLowerCase();
            if (map.containsKey(low)) {
                String repl = map.get(low);
                if (repl != null && !repl.isBlank()) out.add(repl);
            } else {
                out.add(t);
            }
        }
        return String.join(" ", out).trim();
    }

    /** Search the multi endpoint and return any movie matches. */
    private CompletableFuture<List<MovieMatch>> searchMultiForMovies(String title, String lang) {
        try {
            String encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8);
            StringBuilder url = new StringBuilder(BASE_URL)
                    .append("/search/multi")
                    .append("?api_key=").append(apiKey)
                    .append("&query=").append(encodedTitle);

            if (lang != null && !lang.isBlank()) url.append("&language=").append(lang);

            return throttledGet(url.toString())
                    .thenApply(body -> {
                        if (debug) {
                            try {
                                String redacted = makeSearchMultiUrl(title, lang, true);
                                String snippet = body == null ? "<null>" : body.substring(0, Math.min(body.length(), 1000));
                                System.err.println("[TmdbClient] Response for '" + title + "' (multi->movie) from: " + redacted + "\n" + snippet);
                            } catch (Exception ignored) {}
                        }
                        return parseMultiMovieResponse(body);
                    })
                    .exceptionally(ex -> Collections.emptyList());
        } catch (Exception e) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
    }

    private List<MovieMatch> parseMultiMovieResponse(String body) {
        List<MovieMatch> results = new ArrayList<>();
        try {
            JSONObject json = new JSONObject(body);
            JSONArray array = json.optJSONArray("results");
            if (array == null) return results;

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String mediaType = obj.optString("media_type", "");
                if (!"movie".equalsIgnoreCase(mediaType)) continue;
                int id = obj.optInt("id", 0);
                String title = obj.optString("title", obj.optString("name", "Unknown"));
                String releaseDate = obj.optString("release_date", "");
                String year = releaseDate.length() >= 4 ? releaseDate.substring(0, 4) : "";
                String overview = obj.optString("overview", "");
                results.add(new MovieMatch(id, title, year, overview));
            }
        } catch (Exception e) {
            System.err.println("[TmdbClient] Failed to parse multi response for movies: " + e.getMessage());
        }
        return results;
    }

    public CompletableFuture<List<SeriesMatch>> searchSeries(String title, String year) {
        if (apiKey == null || apiKey.isBlank()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        try {
            // Try several variants in sequence: original, German-transliteration, stripped ASCII,
            // and if necessary try again with German language preference.
            String trimmed = title != null ? title.trim() : "";
            String withoutTrailingNumber = stripTrailingNumberSuffix(trimmed);
            List<String> tried = new ArrayList<>();

            // Try original
            return searchSeriesOnce(trimmed, year, this.language)
                    .thenCompose(list -> {
                        tried.add(makeSearchSeriesUrl(trimmed, year, this.language, true));
                        if (!list.isEmpty()) return CompletableFuture.completedFuture(list);
                        // Try restoring umlauts (e.g. "Baeren" -> "Bären")
                        String uml = restoreGermanUmlauts(trimmed);
                        if (uml != null && !uml.equalsIgnoreCase(trimmed)) {
                            tried.add(makeSearchSeriesUrl(uml, year, this.language, true));
                            return searchSeriesOnce(uml, year, this.language);
                        }
                        if (withoutTrailingNumber != null && !withoutTrailingNumber.equalsIgnoreCase(trimmed)) {
                            tried.add(makeSearchSeriesUrl(withoutTrailingNumber, year, this.language, true));
                            return searchSeriesOnce(withoutTrailingNumber, year, this.language);
                        }
                        return CompletableFuture.completedFuture(list);
                    })
                    // If still empty, try variants without year (some TMDB matches fail when year provided)
                    .thenCompose(list -> {
                        if (!list.isEmpty()) return CompletableFuture.completedFuture(list);
                        tried.add(makeSearchSeriesUrl(trimmed, null, this.language, true));
                        return searchSeriesOnce(trimmed, null, this.language);
                    })
                    .thenCompose(list -> {
                        if (!list.isEmpty()) return CompletableFuture.completedFuture(list);
                        String g = toGermanAscii(trimmed);
                        if (!g.equalsIgnoreCase(trimmed)) {
                            tried.add(makeSearchSeriesUrl(g, year, this.language, true));
                            return searchSeriesOnce(g, year, this.language);
                        }
                        return CompletableFuture.completedFuture(list);
                    })
                    .thenCompose(list -> {
                        if (!list.isEmpty()) return CompletableFuture.completedFuture(list);
                        String stripped = toAsciiBase(trimmed);
                        if (!stripped.equalsIgnoreCase(trimmed)) {
                            tried.add(makeSearchSeriesUrl(stripped, year, this.language, true));
                            return searchSeriesOnce(stripped, year, this.language);
                        }
                        if (!"de-DE".equalsIgnoreCase(this.language)) {
                            tried.add(makeSearchSeriesUrl(trimmed, year, "de-DE", true));
                            return searchSeriesOnce(trimmed, year, "de-DE");
                        }
                        return CompletableFuture.completedFuture(list);
                    })
                    .thenCompose(list -> {
                        if (!list.isEmpty()) return CompletableFuture.completedFuture(list);
                        if (!"de-DE".equalsIgnoreCase(this.language)) {
                            String g2 = toGermanAscii(trimmed);
                            if (!g2.equalsIgnoreCase(trimmed)) {
                                tried.add(makeSearchSeriesUrl(g2, year, "de-DE", true));
                                return searchSeriesOnce(g2, year, "de-DE");
                            }
                        }
                        return CompletableFuture.completedFuture(list);
                    })
                    .thenCompose(list -> {
                        if (!list.isEmpty()) return CompletableFuture.completedFuture(list);
                        if (!"de-DE".equalsIgnoreCase(this.language)) {
                            String stripped2 = toAsciiBase(trimmed);
                            if (!stripped2.equalsIgnoreCase(trimmed)) {
                                tried.add(makeSearchSeriesUrl(stripped2, year, "de-DE", true));
                                return searchSeriesOnce(stripped2, year, "de-DE");
                            }
                        }
                        return CompletableFuture.completedFuture(list);
                    })
                    // Final fallback: try the multi-search endpoint and look for TV results
                    .thenCompose(list -> {
                        if (!list.isEmpty()) return CompletableFuture.completedFuture(list);
                        tried.add(makeSearchMultiUrl(trimmed, this.language, true));
                        return searchMultiForSeries(trimmed, this.language);
                    })
                    .thenApply(list -> {
                        if (list.isEmpty() && debug) {
                            System.err.println("[TmdbClient] No series results for '" + title + "'. Tried URLs:");
                            tried.forEach(u -> System.err.println("  " + u));
                        }
                        return list;
                    })
                    .exceptionally(ex -> Collections.emptyList());
        } catch (Exception e) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
    }

    private CompletableFuture<List<SeriesMatch>> searchSeriesOnce(String title, String year, String lang) {
        try {
            String encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8);
            StringBuilder url = new StringBuilder(BASE_URL)
                    .append("/search/tv")
                    .append("?api_key=").append(apiKey)
                    .append("&query=").append(encodedTitle);

            if (lang != null && !lang.isBlank()) {
                url.append("&language=").append(lang);
            }

            if (year != null && !year.isBlank()) {
                url.append("&first_air_date_year=").append(year);
            }

            return throttledGet(url.toString())
                    .thenApply(body -> {
                        if (debug) {
                            try {
                                String redacted = makeSearchSeriesUrl(title, year, lang, true);
                                String snippet = body == null ? "<null>" : body.substring(0, Math.min(body.length(), 1000));
                                System.err.println("[TmdbClient] Response for '" + title + "' (series) from: " + redacted + "\n" + snippet);
                            } catch (Exception ignored) {}
                        }
                        return parseSeriesResponse(body);
                    })
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

    private String makeSearchSeriesUrl(String title, String year, String lang, boolean redactKey) {
        try {
            String encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8);
            StringBuilder url = new StringBuilder(BASE_URL)
                    .append("/search/tv")
                    .append("?api_key=").append(redactKey ? "REDACTED" : apiKey)
                    .append("&query=").append(encodedTitle);
            if (lang != null && !lang.isBlank()) url.append("&language=").append(lang);
            if (year != null && !year.isBlank()) url.append("&first_air_date_year=").append(year);
            return url.toString();
        } catch (Exception e) {
            return "";
        }
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

    /**
     * Convert common German characters to ASCII-friendly alternatives and strip
     * other diacritics. Examples: "Bären" -> "Baeren".
     */
    private String toGermanAscii(String input) {
        if (input == null || input.isBlank()) return input == null ? null : "";
        String replaced = input
                .replace("Ä", "Ae").replace("ä", "ae")
                .replace("Ö", "Oe").replace("ö", "oe")
                .replace("Ü", "Ue").replace("ü", "ue")
                .replace("ß", "ss");
        String normalized = Normalizer.normalize(replaced, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "");
    }

    /**
     * Try to restore common German umlaut spellings from ASCII transliterations.
     * E.g. "Baerenbr ue der" -> "Bärenbrüder" when applicable.
     */
    private String restoreGermanUmlauts(String input) {
        if (input == null || input.isBlank()) return input == null ? null : "";
        String s = input;
        // Replace common ae/oe/ue sequences back to umlauts when they look like transliterations
        s = s.replaceAll("(?i)ae", "ä");
        s = s.replaceAll("(?i)oe", "ö");
        s = s.replaceAll("(?i)ue", "ü");
        // Don't auto-convert 'ss' to ß because ambiguous; only if original contains 'ss' and no 'ß'
        return s;
    }

    /** Remove all diacritics by normalizing and stripping marks. */
    private String toAsciiBase(String input) {
        if (input == null || input.isBlank()) return input == null ? null : "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "");
    }

    private String makeSearchMovieUrl(String title, String year, String lang, boolean redactKey) {
        try {
            String encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8);
            StringBuilder url = new StringBuilder(BASE_URL)
                    .append("/search/movie")
                    .append("?api_key=").append(redactKey ? "REDACTED" : apiKey)
                    .append("&query=").append(encodedTitle);
            if (lang != null && !lang.isBlank()) url.append("&language=").append(lang);
            if (year != null && !year.isBlank()) url.append("&year=").append(year);
            return url.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String makeSearchMultiUrl(String title, String lang, boolean redactKey) {
        try {
            String encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8);
            StringBuilder url = new StringBuilder(BASE_URL)
                    .append("/search/multi")
                    .append("?api_key=").append(redactKey ? "REDACTED" : apiKey)
                    .append("&query=").append(encodedTitle);
            if (lang != null && !lang.isBlank()) url.append("&language=").append(lang);
            return url.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** Search the multi endpoint and return any series matches. */
    private CompletableFuture<List<SeriesMatch>> searchMultiForSeries(String title, String lang) {
        try {
            String encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8);
            StringBuilder url = new StringBuilder(BASE_URL)
                    .append("/search/multi")
                    .append("?api_key=").append(apiKey)
                    .append("&query=").append(encodedTitle);

            if (lang != null && !lang.isBlank()) url.append("&language=").append(lang);

            return throttledGet(url.toString())
                        .thenApply(body -> {
                            if (debug) {
                                try {
                                    String redacted = makeSearchMultiUrl(title, lang, true);
                                    String snippet = body == null ? "<null>" : body.substring(0, Math.min(body.length(), 1000));
                                    System.err.println("[TmdbClient] Response for '" + title + "' (multi->series) from: " + redacted + "\n" + snippet);
                                } catch (Exception ignored) {}
                            }
                            return parseMultiSeriesResponse(body);
                        })
                    .exceptionally(ex -> Collections.emptyList());
        } catch (Exception e) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
    }

    private List<SeriesMatch> parseMultiSeriesResponse(String body) {
        List<SeriesMatch> results = new ArrayList<>();
        try {
            JSONObject json = new JSONObject(body);
            JSONArray array = json.optJSONArray("results");
            if (array == null) return results;

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String mediaType = obj.optString("media_type", "");
                if (!"tv".equalsIgnoreCase(mediaType)) continue;
                int id = obj.optInt("id", 0);
                String name = obj.optString("name", obj.optString("title", "Unknown"));
                String firstAirDate = obj.optString("first_air_date", "");
                String firstAirYear = firstAirDate.length() >= 4 ? firstAirDate.substring(0, 4) : "";
                String overview = obj.optString("overview", "");
                results.add(new SeriesMatch(id, name, firstAirYear, overview));
            }
        } catch (Exception e) {
            System.err.println("[TmdbClient] Failed to parse multi response for series: " + e.getMessage());
        }
        return results;
    }

    /**
     * Remove simple trailing numeric suffixes like " 1", " (1)", " - 01", "part 1".
     */
    private String stripTrailingNumberSuffix(String title) {
        if (title == null) return null;
        String t = title.trim();
        // remove patterns like ' (1)', ' - 1', '._01', ' part 1'
        t = t.replaceAll("\\s*\\(\\d{1,3}\\)$", "");
        t = t.replaceAll("[\\s._-]+\\d{1,3}$", "");
        t = t.replaceAll("(?i)\\s*part\\s+\\d{1,3}$", "");
        return t.trim();
    }

    /** Generate simple query variants by removing common German stopwords and punctuation. */
    private List<String> generateTitleVariants(String title) {
        List<String> out = new ArrayList<>();
        if (title == null || title.isBlank()) return out;
        String cleaned = title.replaceAll("[,:!()?\"]", " ").replaceAll("\\s+", " ").trim();
        out.add(cleaned);

        // Lowercase tokens and remove short/common German articles
        String[] stop = new String[]{"der","die","das","den","dem","ein","eine","eines","und","von","zu","mit","für","auf"};
        String[] tokens = cleaned.split("\\s+");
        List<String> sig = new ArrayList<>();
        for (String t : tokens) {
            String l = t.toLowerCase();
            boolean isStop = false;
            for (String s : stop) if (s.equals(l)) { isStop = true; break; }
            if (!isStop) sig.add(t);
        }
        if (!sig.isEmpty()) out.add(String.join(" ", sig));

        // first token
        if (tokens.length > 0) out.add(tokens[0]);
        // last two tokens
        if (tokens.length > 1) out.add(tokens[tokens.length-2] + " " + tokens[tokens.length-1]);

        // unique preserve order
        List<String> uniq = new ArrayList<>();
        for (String v : out) if (v != null && !v.isBlank() && !uniq.contains(v)) uniq.add(v);
        return uniq;
    }

}

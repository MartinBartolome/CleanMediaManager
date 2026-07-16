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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * {@link MetadataProvider} backed by IMDb's public, key-free search
 * suggestion endpoint (the same one that powers the autocomplete box on
 * imdb.com). No account or API key is required.
 *
 * <p>Limitation: IMDb does not expose a free, unauthenticated endpoint for
 * per-episode data (imdb.com itself blocks plain HTTP scraping via bot
 * protection), so {@link #getSeasonEpisodes(int, int)} always returns an
 * empty map. Episode files are still renamed correctly using the season and
 * episode numbers parsed from the filename; only the optional {@code
 * {title}} placeholder (episode name) stays blank when IMDb is selected as
 * the provider.</p>
 *
 * <p>IMDb itself never localizes the search result title (no {@code
 * language} parameter). To still honor the user's chosen display language,
 * each result's IMDb id is looked up on Wikidata (also free/key-less; {@code
 * query.wikidata.org}) which stores multilingual title labels linked via the
 * "IMDb ID" property (P345); the English title is kept as a fallback when
 * Wikidata has no entry or no label in that language.</p>
 */
public class ImdbClient implements MetadataProvider {

    private static final String SUGGEST_URL = "https://v3.sg.media-imdb.com/suggestion/x/";
    private static final String WIKIDATA_SPARQL_URL = "https://query.wikidata.org/sparql?format=json&query=";
    private static final int MAX_CONCURRENT = 8;

    private final HttpClient httpClient;
    private final boolean debug;
    private final Semaphore semaphore = new Semaphore(MAX_CONCURRENT, true);
    // 2-letter Wikidata language code to localize titles to, or null when the
    // configured language is English (in which case the original IMDb title
    // returned by the suggestion endpoint is already correct, no lookup needed).
    private final String wikidataLangCode;

    // Maps the synthetic int id (derived from the numeric part of a "tt..." id)
    // back to the full IMDb id string, populated as search results are produced.
    private final Map<Integer, String> idToImdbId = new ConcurrentHashMap<>();

    public ImdbClient(String language) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        String prop = System.getProperty("imdb.debug");
        if (prop == null) prop = System.getenv().getOrDefault("IMDB_DEBUG", "false");
        this.debug = Boolean.parseBoolean(prop);
        String code = language != null && language.length() >= 2
                ? language.substring(0, 2).toLowerCase(Locale.ROOT) : "";
        this.wikidataLangCode = (!code.isBlank() && !code.equals("en")) ? code : null;
    }

    @Override
    public CompletableFuture<List<MovieMatch>> searchMovie(String title, String year) {
        String trimmed = title != null ? title.trim() : "";
        if (trimmed.isBlank()) return CompletableFuture.completedFuture(Collections.emptyList());

        return fetchSuggestions(trimmed)
                .thenCompose(items -> {
                    List<MovieMatch> movies = toMovies(items, year);
                    if (!movies.isEmpty()) return CompletableFuture.completedFuture(movies);
                    String stripped = stripParenthetical(trimmed);
                    if (!stripped.equalsIgnoreCase(trimmed)) {
                        return fetchSuggestions(stripped).thenApply(items2 -> toMovies(items2, year));
                    }
                    return CompletableFuture.completedFuture(movies);
                })
                .thenCompose(this::localizeMovies)
                .exceptionally(ex -> {
                    if (debug) ex.printStackTrace();
                    return Collections.emptyList();
                });
    }

    @Override
    public CompletableFuture<List<SeriesMatch>> searchSeries(String title, String year) {
        String trimmed = title != null ? title.trim() : "";
        if (trimmed.isBlank()) return CompletableFuture.completedFuture(Collections.emptyList());

        return fetchSuggestions(trimmed)
                .thenCompose(items -> {
                    List<SeriesMatch> series = toSeries(items, year);
                    if (!series.isEmpty()) return CompletableFuture.completedFuture(series);
                    String stripped = stripParenthetical(trimmed);
                    if (!stripped.equalsIgnoreCase(trimmed)) {
                        return fetchSuggestions(stripped).thenApply(items2 -> toSeries(items2, year));
                    }
                    return CompletableFuture.completedFuture(series);
                })
                .thenCompose(this::localizeSeries)
                .exceptionally(ex -> {
                    if (debug) ex.printStackTrace();
                    return Collections.emptyList();
                });
    }

    /**
     * IMDb does not offer a free, key-less endpoint for per-episode listings
     * (the imdb.com episode pages are blocked for plain HTTP clients by bot
     * protection). Always returns an empty map; callers fall back to the
     * season/episode numbers parsed from the filename.
     */
    @Override
    public CompletableFuture<Map<Integer, EpisodeMatch>> getSeasonEpisodes(int seriesId, int season) {
        return CompletableFuture.completedFuture(Collections.emptyMap());
    }

    @Override
    public CompletableFuture<String> getMovieImdbId(int id) {
        return CompletableFuture.completedFuture(idToImdbId.get(id));
    }

    @Override
    public CompletableFuture<String> getSeriesImdbId(int id) {
        return CompletableFuture.completedFuture(idToImdbId.get(id));
    }

    /**
     * IMDb's suggestion endpoint itself has no {@code language} parameter: it
     * always returns the original title, but internally still matches against
     * foreign AKAs (e.g. searching "Der Pate" correctly finds "The Godfather").
     * Titles are separately localized on a best-effort basis via Wikidata (see
     * {@link #localizeMovies}/{@link #localizeSeries}), but that lookup can
     * still miss less notable titles, so the returned title isn't guaranteed
     * to be in the parsed filename's language. Callers should therefore trust
     * the top result instead of penalizing it with a text-similarity score.
     */
    @Override
    public boolean supportsTitleLocalization() {
        return false;
    }

    // IMDb's own "qid" taxonomy for single-file, standalone productions (as opposed to
    // "tvSeries"/"tvMiniSeries", which have season/episode structure). Includes feature
    // films, TV movies/specials and shorts - e.g. the Oscar-winning short "The Boy, the
    // Mole, the Fox and the Horse" (tt22667880) is filed under "short" on IMDb despite
    // being a standalone ~32 min. production, not part of any series.
    private static final java.util.Set<String> MOVIE_QIDS =
            java.util.Set.of("movie", "tvMovie", "tvSpecial", "video", "short", "tvShort");

    private List<MovieMatch> toMovies(JSONArray items, String year) {
        List<MovieMatch> results = new ArrayList<>();
        if (items == null) return results;
        for (int i = 0; i < items.length(); i++) {
            JSONObject obj = items.optJSONObject(i);
            if (obj == null) continue;
            String qid = obj.optString("qid", "");
            if (!MOVIE_QIDS.contains(qid)) continue;
            String imdbId = obj.optString("id", "");
            if (imdbId.isBlank()) continue;
            String label = obj.optString("l", "Unknown");
            String itemYear = obj.has("y") ? String.valueOf(obj.optInt("y")) : "";
            int syntheticId = toSyntheticId(imdbId);
            idToImdbId.put(syntheticId, imdbId);
            MovieMatch match = new MovieMatch(syntheticId, label, itemYear, "");
            match.setImdbId(imdbId);
            results.add(match);
        }
        return sortByYearMatch(results, year, m -> m.getYear());
    }

    private List<SeriesMatch> toSeries(JSONArray items, String year) {
        List<SeriesMatch> results = new ArrayList<>();
        if (items == null) return results;
        for (int i = 0; i < items.length(); i++) {
            JSONObject obj = items.optJSONObject(i);
            if (obj == null) continue;
            String qid = obj.optString("qid", "");
            if (!"tvSeries".equals(qid) && !"tvMiniSeries".equals(qid)) continue;
            String imdbId = obj.optString("id", "");
            if (imdbId.isBlank()) continue;
            String label = obj.optString("l", "Unknown");
            String itemYear = obj.has("y") ? String.valueOf(obj.optInt("y")) : "";
            int syntheticId = toSyntheticId(imdbId);
            idToImdbId.put(syntheticId, imdbId);
            SeriesMatch match = new SeriesMatch(syntheticId, label, itemYear, "");
            match.setImdbId(imdbId);
            results.add(match);
        }
        return sortByYearMatch(results, year, m -> m.getFirstAirYear());
    }

    /** Moves entries whose year matches the requested year to the front, preserving relative order otherwise. */
    private <T> List<T> sortByYearMatch(List<T> results, String year, java.util.function.Function<T, String> yearOf) {
        if (year == null || year.isBlank() || results.size() < 2) return results;
        List<T> matching = new ArrayList<>();
        List<T> rest = new ArrayList<>();
        for (T t : results) {
            if (year.equals(yearOf.apply(t))) matching.add(t); else rest.add(t);
        }
        matching.addAll(rest);
        return matching;
    }

    /** Derives a stable synthetic int id from an IMDb id's numeric suffix (e.g. "tt0133093" -> 133093). */
    private int toSyntheticId(String imdbId) {
        try {
            String digits = imdbId.replaceAll("[^0-9]", "");
            return Integer.parseInt(digits);
        } catch (Exception e) {
            return Math.abs(imdbId.hashCode());
        }
    }

    private String stripParenthetical(String title) {
        return title.replaceAll("\\(.*?\\)", "").replaceAll("\\s+", " ").trim();
    }

    /** Replaces each match's title with its Wikidata label in the configured language, when available. */
    private CompletableFuture<List<MovieMatch>> localizeMovies(List<MovieMatch> matches) {
        if (wikidataLangCode == null || matches == null || matches.isEmpty()) {
            return CompletableFuture.completedFuture(matches);
        }
        List<CompletableFuture<MovieMatch>> futures = matches.stream().map(m ->
                fetchWikidataLabel(m.getImdbId()).thenApply(label -> {
                    if (label == null || label.isBlank()) return m;
                    MovieMatch localized = new MovieMatch(m.getId(), label, m.getYear(), m.getOverview());
                    localized.setImdbId(m.getImdbId());
                    return localized;
                })
        ).collect(Collectors.toList());
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream().map(CompletableFuture::join).collect(Collectors.toList()));
    }

    /** Replaces each match's name with its Wikidata label in the configured language, when available. */
    private CompletableFuture<List<SeriesMatch>> localizeSeries(List<SeriesMatch> matches) {
        if (wikidataLangCode == null || matches == null || matches.isEmpty()) {
            return CompletableFuture.completedFuture(matches);
        }
        List<CompletableFuture<SeriesMatch>> futures = matches.stream().map(m ->
                fetchWikidataLabel(m.getImdbId()).thenApply(label -> {
                    if (label == null || label.isBlank()) return m;
                    SeriesMatch localized = new SeriesMatch(m.getId(), label, m.getFirstAirYear(), m.getOverview());
                    localized.setImdbId(m.getImdbId());
                    return localized;
                })
        ).collect(Collectors.toList());
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream().map(CompletableFuture::join).collect(Collectors.toList()));
    }

    /**
     * Looks up the title/label of an IMDb id in the configured language via Wikidata's
     * free, key-less SPARQL endpoint (property P345 = "IMDb ID"). Returns {@code null}
     * when Wikidata has no matching item, no label in that language, or the request fails.
     */
    private CompletableFuture<String> fetchWikidataLabel(String imdbId) {
        if (wikidataLangCode == null || imdbId == null || imdbId.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CompletableFuture.completedFuture(null);
        }
        try {
            // Include "mul" (Wikidata's "multiple languages" tag): very well-known titles that
            // are spelled identically across languages (e.g. "Friends") are often stored only
            // under that tag rather than as separate per-language labels. Without it, the label
            // service finds no de/en label and falls back to printing the bare Wikidata id
            // (e.g. "Q79784") as the "label" instead.
            String sparql = "SELECT ?itemLabel WHERE { ?item wdt:P345 \"" + imdbId + "\". "
                    + "SERVICE wikibase:label { bd:serviceParam wikibase:language \"" + wikidataLangCode + ",en,mul\". } } LIMIT 1";
            String url = WIKIDATA_SPARQL_URL + URLEncoder.encode(sparql, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/sparql-results+json")
                    .header("User-Agent", "CleanMediaManager/1.0 (metadata renaming tool; key-less IMDb title localization)")
                    .GET()
                    .build();
            return sendWithRetry(request, 0)
                    .whenComplete((r, ex) -> semaphore.release())
                    .thenApply(response -> {
                        try {
                            JSONObject json = new JSONObject(response.body());
                            JSONArray bindings = json.getJSONObject("results").getJSONArray("bindings");
                            if (bindings.isEmpty()) return null;
                            JSONObject binding = bindings.getJSONObject(0);
                            if (!binding.has("itemLabel")) return null;
                            String label = binding.getJSONObject("itemLabel").optString("value", null);
                            // Defensive fallback: if the label service couldn't resolve any label at all
                            // (in none of the requested languages), it returns the bare Wikidata id
                            // (e.g. "Q79784") as a literal instead of a real title - treat that as "no label".
                            if (label != null && label.matches("Q\\d+")) return null;
                            return label;
                        } catch (Exception e) {
                            if (debug) e.printStackTrace();
                            return null;
                        }
                    })
                    .exceptionally(ex -> {
                        if (debug) ex.printStackTrace();
                        return null;
                    });
        } catch (Exception e) {
            semaphore.release();
            return CompletableFuture.completedFuture(null);
        }
    }

    private CompletableFuture<JSONArray> fetchSuggestions(String query) {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CompletableFuture.failedFuture(e);
        }
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = SUGGEST_URL + encoded + ".json";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            return sendWithRetry(request, 0)
                    .whenComplete((r, ex) -> semaphore.release())
                    .thenApply(response -> {
                        try {
                            if (debug) System.err.println("[ImdbClient] Response (" + response.statusCode() + ") for '" + query + "' from: " + url);
                            JSONObject json = new JSONObject(response.body());
                            JSONArray arr = json.optJSONArray("d");
                            return arr != null ? arr : new JSONArray();
                        } catch (Exception e) {
                            if (debug) e.printStackTrace();
                            return new JSONArray();
                        }
                    })
                    .exceptionally(ex -> {
                        if (debug) ex.printStackTrace();
                        return new JSONArray();
                    });
        } catch (Exception e) {
            semaphore.release();
            return CompletableFuture.completedFuture(new JSONArray());
        }
    }

    /**
     * Sends the request, transparently retrying (with a short backoff) up to twice
     * when IMDb/Wikidata respond with HTTP 429 (rate limited) - both public endpoints
     * enforce a per-IP request budget that a burst of matches can exceed.
     */
    private CompletableFuture<HttpResponse<String>> sendWithRetry(HttpRequest request, int attempt) {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenCompose(response -> {
                    if (response.statusCode() == 429 && attempt < 2) {
                        long delayMs = 1500L * (attempt + 1);
                        if (debug) System.err.println("[ImdbClient] HTTP 429 (rate limited), retrying in " + delayMs + "ms...");
                        return CompletableFuture.supplyAsync(() -> null,
                                        CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS))
                                .thenCompose(v -> sendWithRetry(request, attempt + 1));
                    }
                    return CompletableFuture.completedFuture(response);
                });
    }
}

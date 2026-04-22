package com.cleanmediamanager.core;

import com.cleanmediamanager.AppInfo;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

public class UpdateChecker {

    private static final String RELEASES_API =
            "https://api.github.com/repos/" + AppInfo.GITHUB_REPO + "/releases/latest";

    public record UpdateInfo(String version, String downloadUrl, String assetName) {}

    /**
     * Queries the GitHub Releases API and returns update info if a newer version is available.
     * This method blocks – call it from a background thread.
     */
    public Optional<UpdateInfo> checkForUpdate() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RELEASES_API))
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return Optional.empty();
            }

            JSONObject json = new JSONObject(response.body());
            String tagName = json.optString("tag_name", "");
            if (tagName.isBlank()) return Optional.empty();

            String latestVersion = tagName.startsWith("v") ? tagName.substring(1) : tagName;

            if (!isNewer(latestVersion, AppInfo.VERSION)) {
                return Optional.empty();
            }

            String os = System.getProperty("os.name", "").toLowerCase();
            String assetSuffix = os.contains("win") ? ".exe" : ".deb";

            JSONArray assets = json.optJSONArray("assets");
            if (assets == null) return Optional.empty();

            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                String name = asset.optString("name", "");
                String url  = asset.optString("browser_download_url", "");
                if (name.endsWith(assetSuffix) && !url.isBlank()) {
                    return Optional.of(new UpdateInfo(latestVersion, url, name));
                }
            }

        } catch (Exception e) {
            // Network unavailable or API error – silently ignore
        }
        return Optional.empty();
    }

    /** Returns true when {@code candidate} is strictly newer than {@code current} (semver). */
    private boolean isNewer(String candidate, String current) {
        int[] c = parseSemver(candidate);
        int[] v = parseSemver(current);
        for (int i = 0; i < 3; i++) {
            if (c[i] != v[i]) return c[i] > v[i];
        }
        return false;
    }

    private int[] parseSemver(String version) {
        String[] parts = version.split("\\.");
        int[] result = new int[3];
        for (int i = 0; i < 3 && i < parts.length; i++) {
            try { result[i] = Integer.parseInt(parts[i]); } catch (NumberFormatException ignored) {}
        }
        return result;
    }
}

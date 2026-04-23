package com.cleanmediamanager.core;

import java.awt.Desktop;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.function.Consumer;

public class AutoUpdater {

    /**
     * Downloads the installer to the system temp directory, opens it with the OS default handler,
     * and exits the application.
     *
     * @param downloadUrl URL of the release asset
     * @param assetName   filename (used as temp file name)
     * @param onProgress  callback receiving a progress message (runs on background thread)
     * @param onError     callback receiving an error message (runs on background thread)
     */
    public void downloadAndInstall(String downloadUrl, String assetName,
                                   Consumer<String> onProgress, Consumer<String> onError) {
        try {
            onProgress.accept("Downloading " + assetName + " …");

            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .timeout(Duration.ofMinutes(5))
                    .GET()
                    .build();

            HttpResponse<InputStream> response =
                    client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                onError.accept("Download failed – HTTP " + response.statusCode());
                return;
            }

            Path tempFile = Path.of(System.getProperty("java.io.tmpdir"), assetName);
            try (InputStream in = response.body()) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            onProgress.accept("Download complete. Opening installer …");

            // Make the file executable on Linux
            File file = tempFile.toFile();
            if (!file.setExecutable(true)) {
                // Non-fatal; the Desktop API or package manager will handle it
            }

            boolean opened = false;
            if (Desktop.isDesktopSupported()) {
                try {
                    Desktop.getDesktop().open(file);
                    opened = true;
                } catch (Exception ignored) {
                    // Fall through to xdg-open
                }
            }
            if (!opened) {
                // Use setsid so the child process is detached from the JVM and
                // survives System.exit(). This also allows GUI package managers
                // (e.g. GDebi, GNOME Software) to prompt for sudo independently.
                ProcessBuilder pb = new ProcessBuilder("setsid", "xdg-open", tempFile.toString());
                pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                pb.redirectError(ProcessBuilder.Redirect.DISCARD);
                pb.start();
            }

            // Give the OS a moment to pick up the file, then exit
            Thread.sleep(2500);
            System.exit(0);

        } catch (Exception e) {
            onError.accept("Update failed: " + e.getMessage());
        }
    }
}

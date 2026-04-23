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

            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("linux")) {
                String deb = tempFile.toString();
                boolean launched = false;

                // Check if gdebi-gtk is installed; if so, use pkexec so the
                // polkit password dialog appears and the install runs as root.
                try {
                    boolean hasGdebi = new ProcessBuilder("which", "gdebi-gtk")
                            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                            .redirectError(ProcessBuilder.Redirect.DISCARD)
                            .start().waitFor() == 0;
                    if (hasGdebi) {
                        new ProcessBuilder("setsid", "pkexec", "gdebi-gtk", deb)
                                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                                .redirectError(ProcessBuilder.Redirect.DISCARD)
                                .start();
                        launched = true;
                    }
                } catch (Exception ignored) {}

                // Fallback: xdg-open (lets the OS pick the default .deb handler)
                if (!launched) {
                    try {
                        new ProcessBuilder("setsid", "xdg-open", deb)
                                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                                .redirectError(ProcessBuilder.Redirect.DISCARD)
                                .start();
                        launched = true;
                    } catch (Exception ignored) {}
                }

                if (launched) {
                    onProgress.accept("Installer opened. You can now close this application.");
                } else {
                    onProgress.accept("Installer saved to: " + deb
                            + "\nPlease install manually: sudo apt install \"" + deb + "\"");
                }
            } else {
                // Windows / macOS: installer replaces the running JAR, so exit after opening.
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(file);
                }
                Thread.sleep(2500);
                System.exit(0);
            }

        } catch (Exception e) {
            onError.accept("Update failed: " + e.getMessage());
        }
    }
}

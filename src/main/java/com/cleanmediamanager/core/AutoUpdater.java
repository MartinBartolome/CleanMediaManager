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
                // On Linux the running JAR does not block .deb installation,
                // so we do NOT call System.exit(). We just launch the installer
                // detached and let the user close the app manually.
                // Try common GUI .deb installers in order; fall back to xdg-open.
                String[] candidates = {"gdebi-gtk", "gnome-software", "xdg-open"};
                boolean launched = false;
                for (String cmd : candidates) {
                    try {
                        new ProcessBuilder("setsid", cmd, tempFile.toString())
                                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                                .redirectError(ProcessBuilder.Redirect.DISCARD)
                                .start();
                        launched = true;
                        break;
                    } catch (Exception ignored) {
                        // command not available – try next
                    }
                }
                if (launched) {
                    onProgress.accept("Installer opened. You can now close this application.");
                } else {
                    onProgress.accept("Installer saved to: " + tempFile
                            + "\nPlease install it manually with: sudo dpkg -i " + tempFile);
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

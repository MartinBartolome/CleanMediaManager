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

                // Write an install script; the .deb path is passed as $1 to avoid shell injection
                Path scriptPath = Path.of(System.getProperty("java.io.tmpdir"), "cmm_update_install.sh");
                Files.writeString(scriptPath,
                        "#!/bin/bash\n" +
                        "echo '=== CleanMediaManager Update Installer ==='\n" +
                        "echo ''\n" +
                        "sudo apt install -y \"$1\"\n" +
                        "STATUS=$?\n" +
                        "echo ''\n" +
                        "if [ $STATUS -eq 0 ]; then\n" +
                        "  echo 'Done! Please restart CleanMediaManager.'\n" +
                        "else\n" +
                        "  echo 'Installation failed. Try manually: sudo apt install \"$1\"'\n" +
                        "fi\n" +
                        "read -p 'Press Enter to close...'\n");
                scriptPath.toFile().setExecutable(true);

                String script = scriptPath.toString();
                boolean launched = false;

                // Try common terminal emulators; pass deb as argument to avoid injection
                String[][][] candidates = {
                    {{"which", "gnome-terminal"}, {"setsid", "gnome-terminal", "--", "bash", script, deb}},
                    {{"which", "konsole"},        {"setsid", "konsole",        "-e",  "bash", script, deb}},
                    {{"which", "xterm"},          {"setsid", "xterm",          "-e",  "bash", script, deb}}
                };

                for (String[][] pair : candidates) {
                    try {
                        boolean available = new ProcessBuilder(pair[0])
                                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                                .redirectError(ProcessBuilder.Redirect.DISCARD)
                                .start().waitFor() == 0;
                        if (available) {
                            new ProcessBuilder(pair[1])
                                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                                    .start();
                            launched = true;
                            break;
                        }
                    } catch (Exception ignored) {}
                }

                if (launched) {
                    onProgress.accept("A terminal window has opened. Please enter your sudo password to complete the installation, then restart the application.");
                } else {
                    onProgress.accept("Installer saved to: " + deb + "\nRun: sudo apt install \"" + deb + "\"");
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

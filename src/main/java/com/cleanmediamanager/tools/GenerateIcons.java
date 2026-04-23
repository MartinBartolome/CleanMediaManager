package com.cleanmediamanager.tools;

import com.cleanmediamanager.ui.AppIcon;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility that generates all required icon PNGs from the programmatic renderer
 * and places them in src/main/resources/icons/.
 *
 * Run via Maven:
 *   mvn -q compile exec:java -Dexec.mainClass="com.cleanmediamanager.tools.GenerateIcons"
 */
public class GenerateIcons {

    public static void main(String[] args) throws IOException {
        // Resolve output directory relative to project root (works when run from project root)
        Path outDir = Path.of("src", "main", "resources", "icons");
        Files.createDirectories(outDir);

        for (int size : AppIcon.ICON_SIZES) {
            BufferedImage img = AppIcon.renderIcon(size);
            File outFile = outDir.resolve("app-icon-" + size + ".png").toFile();
            ImageIO.write(img, "PNG", outFile);
            System.out.println("Generated: " + outFile.getPath() + "  (" + size + "x" + size + ")");
        }

        System.out.println("All icon sizes generated in: " + outDir.toAbsolutePath());
    }
}

package com.cleanmediamanager.core;

import com.cleanmediamanager.model.MediaFile;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class FileScanner {

    private static final Set<String> MEDIA_EXTENSIONS = Set.of(
            ".mkv", ".mp4", ".avi", ".mov", ".wmv",
            ".m4v", ".mpg", ".mpeg", ".flv", ".webm"
    );

    public List<MediaFile> scan(Path directory) throws IOException {
        List<MediaFile> files = new ArrayList<>();
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String name = file.getFileName().toString().toLowerCase();
                int dotIdx = name.lastIndexOf('.');
                if (dotIdx >= 0) {
                    String ext = name.substring(dotIdx);
                    if (MEDIA_EXTENSIONS.contains(ext)) {
                        files.add(new MediaFile(file));
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    public List<MediaFile> scanFile(Path file) {
        List<MediaFile> files = new ArrayList<>();
        String name = file.getFileName().toString().toLowerCase();
        int dotIdx = name.lastIndexOf('.');
        if (dotIdx >= 0) {
            String ext = name.substring(dotIdx);
            if (MEDIA_EXTENSIONS.contains(ext)) {
                files.add(new MediaFile(file));
            }
        }
        return files;
    }
}

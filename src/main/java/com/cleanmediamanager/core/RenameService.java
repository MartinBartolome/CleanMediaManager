package com.cleanmediamanager.core;

import com.cleanmediamanager.model.MediaFile;
import com.cleanmediamanager.model.MatchStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class RenameService {

    public List<String> previewRename(List<MediaFile> files) {
        List<String> preview = new ArrayList<>();
        for (MediaFile file : files) {
            if (file.getStatus() == MatchStatus.MATCHED && file.getNewName() != null) {
                preview.add(file.getOriginalName() + "  →  " + file.getNewName());
            }
        }
        return preview;
    }

    public List<String> executeRename(List<MediaFile> files, boolean dryRun) {
        List<String> log = new ArrayList<>();
        for (MediaFile file : files) {
            if (file.getStatus() != MatchStatus.MATCHED || file.getNewName() == null) {
                continue;
            }
            Path source = file.getPath();
            Path parent = source.getParent();
            String newName = file.getNewName();

            // A template may contain one or more '/': all but the last segment are folder
            // names (nested, e.g. "{series}/Season {season:02d}/{file}"), the last segment
            // is the file name. With no '/' at all, it's just a plain rename in-place.
            String[] parts = newName.split("/", -1);
            boolean folderMode = parts.length > 1;
            Path target;
            Path innerMostDir = null;

            if (folderMode) {
                String[] folderParts = new String[parts.length - 1];
                System.arraycopy(parts, 0, folderParts, 0, folderParts.length);
                String fileName = parts[parts.length - 1].trim();

                boolean invalid = fileName.isEmpty() || fileName.contains("\\");
                for (int i = 0; i < folderParts.length && !invalid; i++) {
                    folderParts[i] = folderParts[i].trim();
                    if (folderParts[i].isEmpty() || folderParts[i].contains("\\")) invalid = true;
                }
                if (invalid) {
                    log.add("[SKIP] Invalid folder/file template: " + newName);
                    continue;
                }

                Path grandParent = parent.getParent();
                if (grandParent == null) {
                    log.add("[SKIP] Cannot create parent folder: no grandparent exists for " + source.toString());
                    continue;
                }

                innerMostDir = grandParent.resolve(folderParts[0]);
                for (int i = 1; i < folderParts.length; i++) {
                    innerMostDir = innerMostDir.resolve(folderParts[i]);
                }
                target = innerMostDir.resolve(fileName);
            } else {
                target = parent.resolve(newName);
            }

            if (source.equals(target)) {
                log.add("[SKIP] Already named correctly: " + file.getOriginalName());
                continue;
            }

            if (dryRun) {
                log.add("[DRY RUN] Would rename: " + file.getOriginalName() + "  →  " + target.toString());
                continue;
            }

            try {
                // 1) Ensure the destination folder (all nested levels, if any) exists.
                if (folderMode) {
                    Files.createDirectories(innerMostDir);
                }
                // 2) Never silently overwrite an existing file at the destination.
                if (Files.exists(target)) {
                    log.add("[SKIP] Zieldatei existiert bereits, überspringe: " + target);
                    continue;
                }
                // 3) Move (and thereby rename) the file into place.
                Files.move(source, target);
                log.add("[OK] Renamed: " + file.getOriginalName() + "  →  " + target);
                file.setOriginalName(target.getFileName().toString());

                // 4) Clean up: if the original source folder is now empty, remove it.
                if (folderMode && !parent.equals(target.getParent())) {
                    deleteIfEmpty(parent, log);
                }
            } catch (IOException e) {
                log.add("[ERROR] Failed to rename " + file.getOriginalName() + ": " + e.getMessage());
                file.setStatus(MatchStatus.ERROR);
            }
        }
        return log;
    }

    /** Deletes {@code dir} if it exists and no longer contains any entries, logging failures. */
    private void deleteIfEmpty(Path dir, List<String> log) {
        try {
            if (!Files.exists(dir) || !Files.isDirectory(dir)) return;
            try (var entries = Files.newDirectoryStream(dir)) {
                if (entries.iterator().hasNext()) return; // not empty, leave it alone
            }
            Files.delete(dir);
            log.add("[OK] Leeren Quellordner gelöscht: " + dir);
        } catch (IOException e) {
            log.add("[WARN] Leerer Quellordner konnte nicht gelöscht werden: " + dir + " – " + e.getMessage());
        }
    }
}

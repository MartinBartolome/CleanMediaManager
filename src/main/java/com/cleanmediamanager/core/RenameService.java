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

            // If template contains a single '/', interpret as "newParentName/newFileName"
            Path target;
            int slashIdx = newName.indexOf('/');
            boolean folderMode = slashIdx >= 0;
            if (folderMode) {
                // only allow exactly one leading folder separator (single level)
                if (newName.indexOf('/', slashIdx + 1) != -1) {
                    log.add("[SKIP] Template contains more than one '/' — only single parent folder supported: " + newName);
                    continue;
                }
                String folderName = newName.substring(0, slashIdx).trim();
                String fileName = newName.substring(slashIdx + 1).trim();
                if (folderName.isEmpty() || fileName.isEmpty()) {
                    log.add("[SKIP] Invalid folder/file template: " + newName);
                    continue;
                }
                // prevent nested separators in parts
                if (folderName.contains("\\") || folderName.contains("/") || fileName.contains("\\")) {
                    log.add("[SKIP] Invalid characters in folder/file template: " + newName);
                    continue;
                }

                Path grandParent = parent.getParent();
                if (grandParent == null) {
                    log.add("[SKIP] Cannot rename parent folder: no grandparent exists for " + source.toString());
                    continue;
                }

                Path desiredParent = grandParent.resolve(folderName);
                target = desiredParent.resolve(fileName);
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
                if (folderMode) {
                    Path parentDir = parent;
                    Path grandParent = parentDir.getParent();
                    Path desiredParent = grandParent.resolve(newName.substring(0, slashIdx).trim());

                    if (Files.exists(desiredParent)) {
                        // If desired parent exists, move file into it with new filename
                        if (!Files.isDirectory(desiredParent)) {
                            log.add("[SKIP] Desired parent exists but is not a directory: " + desiredParent);
                            continue;
                        }
                        Path finalTarget = desiredParent.resolve(newName.substring(slashIdx + 1).trim());
                        if (Files.exists(finalTarget)) {
                            log.add("[SKIP] Target already exists, skipping: " + finalTarget);
                            continue;
                        }
                        Files.move(source, finalTarget);
                        log.add("[OK] Moved file into existing folder and renamed: " + file.getOriginalName() + "  →  " + finalTarget);
                        file.setOriginalName(finalTarget.getFileName().toString());
                    } else {
                        // Rename the parent folder itself, then move file path accordingly
                        Path movedParent = Files.move(parentDir, desiredParent);
                        Path finalTarget = movedParent.resolve(newName.substring(slashIdx + 1).trim());
                        if (Files.exists(finalTarget)) {
                            log.add("[SKIP] Target already exists after parent rename, skipping: " + finalTarget);
                            continue;
                        }
                        Files.move(desiredParent.resolve(source.getFileName()), finalTarget);
                        log.add("[OK] Renamed parent folder and file: " + file.getOriginalName() + "  →  " + finalTarget);
                        file.setOriginalName(finalTarget.getFileName().toString());
                    }
                } else {
                    if (Files.exists(target)) {
                        log.add("[SKIP] Target already exists, skipping: " + target.getFileName());
                        continue;
                    }
                    Files.move(source, target);
                    log.add("[OK] Renamed: " + file.getOriginalName() + "  →  " + file.getNewName());
                    file.setOriginalName(file.getNewName());
                }
            } catch (IOException e) {
                log.add("[ERROR] Failed to rename " + file.getOriginalName() + ": " + e.getMessage());
                file.setStatus(MatchStatus.ERROR);
            }
        }
        return log;
    }
}

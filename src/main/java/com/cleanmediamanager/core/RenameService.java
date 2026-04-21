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
            Path target = source.getParent().resolve(file.getNewName());

            if (source.equals(target)) {
                log.add("[SKIP] Already named correctly: " + file.getOriginalName());
                continue;
            }

            if (dryRun) {
                log.add("[DRY RUN] Would rename: " + file.getOriginalName() + "  →  " + file.getNewName());
            } else {
                try {
                    if (Files.exists(target)) {
                        log.add("[SKIP] Target already exists, skipping: " + file.getNewName());
                        continue;
                    }
                    Files.move(source, target);
                    log.add("[OK] Renamed: " + file.getOriginalName() + "  →  " + file.getNewName());
                    file.setOriginalName(file.getNewName());
                } catch (IOException e) {
                    log.add("[ERROR] Failed to rename " + file.getOriginalName() + ": " + e.getMessage());
                    file.setStatus(MatchStatus.ERROR);
                }
            }
        }
        return log;
    }
}

package com.cleanmediamanager.model;

import java.nio.file.Path;

public class MediaFile {
    private final Path path;
    private String originalName;
    private String newName;
    private MatchStatus status;
    private MovieMatch match;

    public MediaFile(Path path) {
        this.path = path;
        this.originalName = path.getFileName().toString();
        this.newName = null;
        this.status = MatchStatus.PENDING;
        this.match = null;
    }

    public Path getPath() { return path; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getNewName() { return newName; }
    public void setNewName(String newName) { this.newName = newName; }
    public MatchStatus getStatus() { return status; }
    public void setStatus(MatchStatus status) { this.status = status; }
    public MovieMatch getMatch() { return match; }
    public void setMatch(MovieMatch match) { this.match = match; }
}

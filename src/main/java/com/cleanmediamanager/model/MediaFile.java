package com.cleanmediamanager.model;

import java.nio.file.Path;

public class MediaFile {
    private final Path path;
    private String originalName;
    private String newName;
    private MatchStatus status;
    private MediaType mediaType;
    private MovieMatch match;
    private SeriesMatch seriesMatch;
    private EpisodeMatch episodeMatch;
    private double matchScore;

    public MediaFile(Path path) {
        this.path = path;
        this.originalName = path.getFileName().toString();
        this.newName = null;
        this.status = MatchStatus.PENDING;
        this.mediaType = MediaType.MOVIE;
        this.match = null;
        this.seriesMatch = null;
        this.episodeMatch = null;
        this.matchScore = 0.0;
    }

    public Path getPath() { return path; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getNewName() { return newName; }
    public void setNewName(String newName) { this.newName = newName; }
    public MatchStatus getStatus() { return status; }
    public void setStatus(MatchStatus status) { this.status = status; }
    public MediaType getMediaType() { return mediaType; }
    public void setMediaType(MediaType mediaType) { this.mediaType = mediaType; }
    public MovieMatch getMatch() { return match; }
    public void setMatch(MovieMatch match) { this.match = match; }
    public SeriesMatch getSeriesMatch() { return seriesMatch; }
    public void setSeriesMatch(SeriesMatch seriesMatch) { this.seriesMatch = seriesMatch; }
    public EpisodeMatch getEpisodeMatch() { return episodeMatch; }
    public void setEpisodeMatch(EpisodeMatch episodeMatch) { this.episodeMatch = episodeMatch; }
    public double getMatchScore() { return matchScore; }
    public void setMatchScore(double matchScore) { this.matchScore = matchScore; }
}

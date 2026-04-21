package com.cleanmediamanager.model;

public class EpisodeMatch {
    private final int season;
    private final int episodeNumber;
    private final String name;
    private final String overview;

    public EpisodeMatch(int season, int episodeNumber, String name, String overview) {
        this.season = season;
        this.episodeNumber = episodeNumber;
        this.name = name;
        this.overview = overview;
    }

    public int getSeason() { return season; }
    public int getEpisodeNumber() { return episodeNumber; }
    public String getName() { return name; }
    public String getOverview() { return overview; }
}

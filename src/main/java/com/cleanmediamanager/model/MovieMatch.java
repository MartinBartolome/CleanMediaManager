package com.cleanmediamanager.model;

public class MovieMatch {
    private final int id;
    private final String title;
    private final String year;
    private final String overview;
    private String imdbId;

    public MovieMatch(int id, String title, String year, String overview) {
        this.id = id;
        this.title = title;
        this.year = year;
        this.overview = overview;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getYear() { return year; }
    public String getOverview() { return overview; }
    public String getImdbId() { return imdbId; }
    public void setImdbId(String imdbId) { this.imdbId = imdbId; }

    @Override
    public String toString() {
        return title + " (" + year + ")";
    }
}

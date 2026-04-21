package com.cleanmediamanager.model;

public class SeriesMatch {
    private final int id;
    private final String name;
    private final String firstAirYear;
    private final String overview;

    public SeriesMatch(int id, String name, String firstAirYear, String overview) {
        this.id = id;
        this.name = name;
        this.firstAirYear = firstAirYear;
        this.overview = overview;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getFirstAirYear() { return firstAirYear; }
    public String getOverview() { return overview; }

    @Override
    public String toString() {
        return firstAirYear != null && !firstAirYear.isEmpty()
                ? name + " (" + firstAirYear + ")"
                : name;
    }
}

package com.cleanmediamanager.core;

import com.cleanmediamanager.model.MovieMatch;
import com.cleanmediamanager.model.SeriesMatch;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Simple scoring utilities to compute a confidence score [0..1].
 * Combines normalized Levenshtein-based title similarity with year and token checks.
 */
public class MatchScorer {

    public static final double DEFAULT_THRESHOLD = 0.75;

    public double scoreMovie(String parsedTitle, String parsedYear, MovieMatch candidate) {
        if (candidate == null) return 0.0;
        String candTitle = candidate.getTitle();
        String candYear = candidate.getYear();

        double titleScore = normalizedSimilarity(normalize(parsedTitle), normalize(candTitle));
        double yearBonus = (parsedYear != null && !parsedYear.isBlank() && parsedYear.equals(candYear)) ? 0.2 : 0.0;
        double tokenBonus = tokenSubsetBonus(parsedTitle, candTitle) ? 0.1 : 0.0;

        double score = Math.min(1.0, 0.7 * titleScore + yearBonus + tokenBonus);
        return score;
    }

    public double scoreSeries(String parsedTitle, String parsedYear, SeriesMatch candidate) {
        if (candidate == null) return 0.0;
        String candTitle = candidate.getName();
        String candYear = candidate.getFirstAirYear();

        double titleScore = normalizedSimilarity(normalize(parsedTitle), normalize(candTitle));
        double yearBonus = (parsedYear != null && !parsedYear.isBlank() && parsedYear.equals(candYear)) ? 0.2 : 0.0;
        double tokenBonus = tokenSubsetBonus(parsedTitle, candTitle) ? 0.1 : 0.0;

        double score = Math.min(1.0, 0.7 * titleScore + yearBonus + tokenBonus);
        return score;
    }

    // Normalize strings: lowercase, strip diacritics, keep letters/digits and spaces
    private String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return n;
    }

    // Return true when most tokens of parsed are contained in candidate title
    private boolean tokenSubsetBonus(String parsed, String candidate) {
        if (parsed == null || parsed.isBlank() || candidate == null || candidate.isBlank()) return false;
        String[] pt = normalize(parsed).split(" ");
        String cand = normalize(candidate);
        int matched = 0;
        for (String t : pt) {
            if (t.length() < 2) continue;
            if (cand.contains(t)) matched++;
        }
        return matched >= Math.max(1, pt.length / 2);
    }

    // normalized similarity based on Levenshtein distance (returns 0..1)
    private double normalizedSimilarity(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        int dist = levenshtein(a, b);
        int max = Math.max(a.length(), b.length());
        if (max == 0) return 1.0;
        return 1.0 - ((double) dist / (double) max);
    }

    private int levenshtein(String s0, String s1) {
        int len0 = s0.length() + 1;
        int len1 = s1.length() + 1;

        int[] cost = new int[len0];
        int[] newcost = new int[len0];

        for (int i = 0; i < len0; i++) cost[i] = i;

        for (int j = 1; j < len1; j++) {
            newcost[0] = j;
            for (int i = 1; i < len0; i++) {
                int match = (s0.charAt(i - 1) == s1.charAt(j - 1)) ? 0 : 1;
                int costReplace = cost[i - 1] + match;
                int costInsert = cost[i] + 1;
                int costDelete = newcost[i - 1] + 1;
                newcost[i] = Math.min(Math.min(costInsert, costDelete), costReplace);
            }
            int[] swap = cost; cost = newcost; newcost = swap;
        }
        return cost[len0 - 1];
    }
}

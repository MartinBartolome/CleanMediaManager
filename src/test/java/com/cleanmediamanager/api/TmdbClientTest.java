package com.cleanmediamanager.api;

import com.cleanmediamanager.model.MovieMatch;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;

public class TmdbClientTest {

    @Test
    public void testEmptyApiKeyReturnsEmpty() throws Exception {
        TmdbClient client = new TmdbClient("", "en-US");
        CompletableFuture<List<MovieMatch>> future = client.searchMovie("Inception", "2010");
        List<MovieMatch> result = future.get();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testNullApiKeyReturnsEmpty() throws Exception {
        TmdbClient client = new TmdbClient(null, "en-US");
        CompletableFuture<List<MovieMatch>> future = client.searchMovie("The Matrix", "1999");
        List<MovieMatch> result = future.get();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testClientCreationWithLanguage() {
        TmdbClient client = new TmdbClient("some-key", "de-DE");
        assertNotNull(client);
    }
}

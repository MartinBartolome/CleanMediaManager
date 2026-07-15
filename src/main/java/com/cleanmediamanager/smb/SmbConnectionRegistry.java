package com.cleanmediamanager.smb;

import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Keeps live SMB connections (one {@link DiskShare} per distinct host/share/login)
 * around for reuse, and transparently reconnects if a session has dropped.
 * This lets the application browse and rename files directly on the network
 * share without requiring the OS to mount it first (e.g. via gvfs/Nautilus).
 */
public final class SmbConnectionRegistry {

    private static final SMBClient CLIENT = new SMBClient(SmbConfig.builder()
            .withTimeout(30, TimeUnit.SECONDS)
            .withSoTimeout(30, TimeUnit.SECONDS)
            .build());

    private static final Map<String, DiskShare> SHARES = new ConcurrentHashMap<>();
    private static final Map<String, Connection> CONNECTIONS = new ConcurrentHashMap<>();

    private SmbConnectionRegistry() {}

    public static synchronized DiskShare getShare(SmbCredentials creds) throws IOException {
        String key = creds.connectionKey();
        DiskShare existing = SHARES.get(key);
        if (existing != null && existing.isConnected()) {
            return existing;
        }
        SHARES.remove(key);

        try {
            Connection connection = CONNECTIONS.get(key);
            if (connection == null || !connection.isConnected()) {
                connection = CLIENT.connect(creds.host(), creds.port());
                CONNECTIONS.put(key, connection);
            }
            AuthenticationContext auth = creds.isAnonymous()
                    ? AuthenticationContext.anonymous()
                    : new AuthenticationContext(creds.username(), creds.password(),
                            creds.domain().isBlank() ? null : creds.domain());
            Session session = connection.authenticate(auth);
            DiskShare share = (DiskShare) session.connectShare(creds.share());
            SHARES.put(key, share);
            return share;
        } catch (Exception e) {
            CONNECTIONS.remove(key);
            throw new IOException("SMB-Verbindung zu \\\\" + creds.host() + "\\" + creds.share()
                    + " fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    /** Closes all pooled connections. Safe to call on application shutdown. */
    public static synchronized void closeAll() {
        SHARES.values().forEach(s -> { try { s.close(); } catch (Exception ignored) {} });
        SHARES.clear();
        CONNECTIONS.values().forEach(c -> { try { c.close(); } catch (Exception ignored) {} });
        CONNECTIONS.clear();
    }
}

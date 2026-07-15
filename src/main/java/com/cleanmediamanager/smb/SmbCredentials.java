package com.cleanmediamanager.smb;

import java.util.Objects;

/**
 * Connection credentials identifying one SMB share session (host + share + login).
 * The password is kept only in memory for the lifetime of the connection and is
 * never persisted to disk.
 */
public record SmbCredentials(String host, int port, String share, String domain, String username, char[] password) {

    public SmbCredentials {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(share, "share");
        if (domain == null) domain = "";
        if (username == null) username = "";
        if (password == null) password = new char[0];
    }

    /** Key identifying a reusable connection (deliberately excludes the password). */
    public String connectionKey() {
        return host.toLowerCase() + ":" + port + ":" + share.toLowerCase() + ":"
                + domain.toLowerCase() + ":" + username.toLowerCase();
    }

    public boolean isAnonymous() {
        return username.isBlank();
    }
}

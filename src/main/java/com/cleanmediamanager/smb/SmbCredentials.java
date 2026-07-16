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
        // Defensively copy the password array instead of keeping the caller's
        // reference: callers (e.g. the "Open Network Path" dialog) zero out
        // their own char[] right after the initial connect() call for good
        // hygiene, but these credentials are retained for the lifetime of the
        // SmbFileSystem/connection and are re-used to transparently
        // reconnect (e.g. after an idle SMB session drops) for every later
        // file operation, including renames. Without this copy, that zeroing
        // would silently turn later reconnect attempts into an empty-password
        // login, causing STATUS_LOGON_FAILURE.
        password = password != null ? password.clone() : new char[0];
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

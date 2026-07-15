package com.cleanmediamanager.smb;

import com.hierynomus.smbj.share.DiskShare;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * A {@link FileSystem} representing one connected SMB share, addressed
 * directly over the network (no OS-level mount required). Use
 * {@link #connect(SmbCredentials)} to open one.
 */
public final class SmbFileSystem extends FileSystem {

    private final String host;
    private final int port;
    private final String shareName;
    private final SmbCredentials credentials;
    private final Map<String, SmbBasicFileAttributes> attributeCache = new ConcurrentHashMap<>();
    private volatile boolean open = true;

    private SmbFileSystem(SmbCredentials credentials) {
        this.host = credentials.host();
        this.port = credentials.port();
        this.shareName = credentials.share();
        this.credentials = credentials;
    }

    /** Connects to the share, validating credentials immediately so callers get a clear error. */
    public static SmbFileSystem connect(SmbCredentials credentials) throws IOException {
        SmbConnectionRegistry.getShare(credentials);
        return new SmbFileSystem(credentials);
    }

    DiskShare share() throws IOException {
        return SmbConnectionRegistry.getShare(credentials);
    }

    String getHost() { return host; }
    int getPort() { return port; }
    String getShareName() { return shareName; }

    SmbPath rootPath() { return new SmbPath(this, "", true); }

    void cacheAttributes(String rawPath, SmbBasicFileAttributes attrs) {
        attributeCache.put(rawPath, attrs);
    }

    SmbBasicFileAttributes takeCachedAttributes(String rawPath) {
        return attributeCache.remove(rawPath);
    }

    void invalidateCache(String rawPath) {
        attributeCache.remove(rawPath);
    }

    @Override
    public FileSystemProvider provider() { return SmbFileSystemProvider.INSTANCE; }

    @Override
    public void close() { open = false; }

    @Override
    public boolean isOpen() { return open; }

    @Override
    public boolean isReadOnly() { return false; }

    @Override
    public String getSeparator() { return "/"; }

    @Override
    public Iterable<Path> getRootDirectories() { return List.of(rootPath()); }

    @Override
    public Iterable<FileStore> getFileStores() { return List.of(); }

    @Override
    public Set<String> supportedFileAttributeViews() { return Set.of("basic"); }

    @Override
    public Path getPath(String first, String... more) {
        StringBuilder sb = new StringBuilder(first == null ? "" : first);
        if (more != null) {
            for (String s : more) {
                if (sb.length() > 0 && !s.isEmpty()) sb.append('/');
                sb.append(s);
            }
        }
        return new SmbPath(this, sb.toString(), true);
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        int colon = syntaxAndPattern.indexOf(':');
        if (colon <= 0) throw new IllegalArgumentException("Invalid syntax: " + syntaxAndPattern);
        String syntax = syntaxAndPattern.substring(0, colon);
        String pattern = syntaxAndPattern.substring(colon + 1);
        String regex;
        if ("regex".equalsIgnoreCase(syntax)) {
            regex = pattern;
        } else if ("glob".equalsIgnoreCase(syntax)) {
            regex = globToRegex(pattern);
        } else {
            throw new UnsupportedOperationException("Syntax not supported: " + syntax);
        }
        Pattern compiled = Pattern.compile(regex);
        return path -> compiled.matcher(path.toString()).matches();
    }

    private static String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder();
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append('.');
                case '.' -> sb.append("\\.");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public WatchService newWatchService() {
        throw new UnsupportedOperationException("WatchService wird für SMB-Netzwerkpfade nicht unterstützt");
    }
}

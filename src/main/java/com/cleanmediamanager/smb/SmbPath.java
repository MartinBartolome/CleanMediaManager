package com.cleanmediamanager.smb;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A {@link Path} implementation representing a location on an SMB share,
 * addressed directly over the network via {@link SmbConnectionRegistry}
 * (no local mount / gvfs required).
 *
 * <p>Instances are either "absolute" (rooted at the share, e.g. produced by
 * {@link SmbFileSystem#getPath(String, String...)} or directory listings) or
 * "relative" name fragments (e.g. returned by {@link #getFileName()}), mirroring
 * the semantics of the default filesystem's {@code Path} implementation.
 */
public final class SmbPath implements Path {

    private final SmbFileSystem fs;
    private final String path; // '/'-separated, no leading/trailing slash, "" == share root
    private final boolean absolute;

    SmbPath(SmbFileSystem fs, String rawPath, boolean absolute) {
        this.fs = fs;
        this.path = normalize(rawPath);
        this.absolute = absolute;
    }

    private static String normalize(String p) {
        if (p == null || p.isEmpty()) return "";
        String s = p.replace('\\', '/');
        while (s.startsWith("/")) s = s.substring(1);
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s.replaceAll("/+", "/");
    }

    /** The path relative to the share root, using '/' separators, no leading slash. */
    String rawPath() { return path; }

    /** UNC-style backslash path as expected by the smbj {@code DiskShare} API. */
    String smbPath() { return path.replace('/', '\\'); }

    private List<String> segments() {
        if (path.isEmpty()) return List.of();
        return List.of(path.split("/"));
    }

    @Override
    public SmbFileSystem getFileSystem() { return fs; }

    @Override
    public boolean isAbsolute() { return absolute; }

    @Override
    public Path getRoot() { return absolute ? fs.rootPath() : null; }

    @Override
    public Path getFileName() {
        List<String> segs = segments();
        if (segs.isEmpty()) return null;
        return new SmbPath(fs, segs.get(segs.size() - 1), false);
    }

    @Override
    public Path getParent() {
        List<String> segs = segments();
        if (segs.isEmpty()) return null;
        if (segs.size() == 1) return absolute ? fs.rootPath() : null;
        return new SmbPath(fs, String.join("/", segs.subList(0, segs.size() - 1)), absolute);
    }

    @Override
    public int getNameCount() { return segments().size(); }

    @Override
    public Path getName(int index) {
        List<String> segs = segments();
        if (index < 0 || index >= segs.size()) {
            throw new IllegalArgumentException("Invalid index: " + index);
        }
        return new SmbPath(fs, segs.get(index), false);
    }

    @Override
    public Path subpath(int beginIndex, int endIndex) {
        List<String> segs = segments();
        if (beginIndex < 0 || endIndex > segs.size() || beginIndex >= endIndex) {
            throw new IllegalArgumentException("Invalid subpath range");
        }
        return new SmbPath(fs, String.join("/", segs.subList(beginIndex, endIndex)), false);
    }

    @Override
    public boolean startsWith(Path other) {
        if (!(other instanceof SmbPath sp) || sp.fs != this.fs) return false;
        return sp.path.isEmpty() || this.path.equals(sp.path) || this.path.startsWith(sp.path + "/");
    }

    @Override
    public boolean startsWith(String other) { return startsWith(new SmbPath(fs, other, this.absolute)); }

    @Override
    public boolean endsWith(Path other) {
        if (!(other instanceof SmbPath sp) || sp.fs != this.fs) return false;
        return sp.path.isEmpty() || this.path.equals(sp.path) || this.path.endsWith("/" + sp.path);
    }

    @Override
    public boolean endsWith(String other) { return endsWith(new SmbPath(fs, other, false)); }

    @Override
    public Path normalize() { return this; }

    @Override
    public Path resolve(Path other) {
        if (!(other instanceof SmbPath sp) || sp.fs != this.fs) throw new ProviderMismatchException();
        if (sp.isAbsolute()) return sp;
        if (sp.path.isEmpty()) return this;
        String combined = this.path.isEmpty() ? sp.path : this.path + "/" + sp.path;
        return new SmbPath(fs, combined, this.absolute);
    }

    @Override
    public Path resolve(String other) { return resolve(new SmbPath(fs, other, false)); }

    @Override
    public Path resolveSibling(Path other) {
        Path parent = getParent();
        return parent == null ? other : parent.resolve(other);
    }

    @Override
    public Path resolveSibling(String other) { return resolveSibling(new SmbPath(fs, other, false)); }

    @Override
    public Path relativize(Path other) {
        if (!(other instanceof SmbPath sp) || sp.fs != this.fs) {
            throw new IllegalArgumentException("Cannot relativize path from a different SMB share");
        }
        if (!sp.path.startsWith(this.path)) {
            throw new IllegalArgumentException("Cannot relativize " + other + " against " + this);
        }
        String rest = sp.path.substring(this.path.length());
        while (rest.startsWith("/")) rest = rest.substring(1);
        return new SmbPath(fs, rest, false);
    }

    @Override
    public URI toUri() {
        try {
            String uriPath = "/" + fs.getShareName() + (path.isEmpty() ? "" : "/" + path);
            return new URI("smb", null, fs.getHost(), fs.getPort(), uriPath, null, null);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Path toAbsolutePath() { return absolute ? this : new SmbPath(fs, path, true); }

    @Override
    public Path toRealPath(LinkOption... options) throws IOException { return toAbsolutePath(); }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) {
        throw new UnsupportedOperationException("WatchService wird für SMB-Netzwerkpfade nicht unterstützt");
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) {
        throw new UnsupportedOperationException("WatchService wird für SMB-Netzwerkpfade nicht unterstützt");
    }

    @Override
    public Iterator<Path> iterator() {
        List<Path> names = new ArrayList<>();
        for (String s : segments()) names.add(new SmbPath(fs, s, false));
        return names.iterator();
    }

    @Override
    public int compareTo(Path other) {
        if (!(other instanceof SmbPath sp)) throw new ClassCastException("Not an SmbPath: " + other);
        return this.toString().compareTo(sp.toString());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof SmbPath sp)) return false;
        return fs == sp.fs && absolute == sp.absolute && path.equals(sp.path);
    }

    @Override
    public int hashCode() { return (fs.hashCode() * 31 + path.hashCode()) * 31 + Boolean.hashCode(absolute); }

    @Override
    public String toString() {
        if (!absolute) return path;
        return "smb://" + fs.getHost() + "/" + fs.getShareName() + (path.isEmpty() ? "" : "/" + path);
    }
}

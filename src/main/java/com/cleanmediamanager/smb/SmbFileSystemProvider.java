package com.cleanmediamanager.smb;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileAllInformation;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.protocol.commons.EnumWithValue;
import com.hierynomus.smbj.share.DiskEntry;
import com.hierynomus.smbj.share.DiskShare;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A minimal {@link FileSystemProvider} that browses and renames files directly
 * on an SMB share over the network, backed by smbj's {@link DiskShare} API.
 * Only the operations actually needed by this application are implemented
 * (directory listing, basic attributes, and move/rename) — reading or writing
 * file contents over SMB is intentionally not supported.
 */
public final class SmbFileSystemProvider extends FileSystemProvider {

    public static final SmbFileSystemProvider INSTANCE = new SmbFileSystemProvider();

    private SmbFileSystemProvider() {}

    @Override
    public String getScheme() { return "smb"; }

    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env) {
        throw new UnsupportedOperationException("Use SmbFileSystem.connect(...) instead");
    }

    @Override
    public FileSystem getFileSystem(URI uri) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Path getPath(URI uri) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) {
        throw new UnsupportedOperationException("Datei-Inhalte werden über SMB in dieser App nicht direkt gelesen/geschrieben");
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        SmbPath smbDir = asSmbPath(dir);
        DiskShare share = smbDir.getFileSystem().share();
        List<FileIdBothDirectoryInformation> entries;
        try {
            entries = share.list(smbDir.smbPath());
        } catch (Exception e) {
            throw new IOException("Verzeichnis konnte nicht gelesen werden: " + smbDir + " – " + e.getMessage(), e);
        }

        List<Path> children = new ArrayList<>();
        for (FileIdBothDirectoryInformation info : entries) {
            String name = info.getFileName();
            if (".".equals(name) || "..".equals(name)) continue;
            SmbPath child = (SmbPath) smbDir.resolve(name);
            boolean isDir = EnumWithValue.EnumUtils.isSet(info.getFileAttributes(), FileAttributes.FILE_ATTRIBUTE_DIRECTORY);
            smbDir.getFileSystem().cacheAttributes(child.rawPath(), new SmbBasicFileAttributes(
                    isDir, info.getEndOfFile(), info.getLastWriteTime().toEpochMillis(), info.getCreationTime().toEpochMillis()));
            children.add(child);
        }

        return new DirectoryStream<>() {
            @Override
            public Iterator<Path> iterator() {
                if (filter == null) return children.iterator();
                List<Path> filtered = new ArrayList<>();
                for (Path p : children) {
                    try {
                        if (filter.accept(p)) filtered.add(p);
                    } catch (IOException ignored) {
                        // skip entries that fail the filter check
                    }
                }
                return filtered.iterator();
            }

            @Override
            public void close() { /* no resources held */ }
        };
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        SmbPath smbDir = asSmbPath(dir);
        try {
            smbDir.getFileSystem().share().mkdir(smbDir.smbPath());
        } catch (Exception e) {
            throw new IOException("Verzeichnis konnte nicht erstellt werden: " + smbDir + " – " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(Path path) throws IOException {
        SmbPath smbPath = asSmbPath(path);
        DiskShare share = smbPath.getFileSystem().share();
        try {
            if (share.folderExists(smbPath.smbPath())) {
                share.rmdir(smbPath.smbPath(), false);
            } else {
                share.rm(smbPath.smbPath());
            }
        } catch (Exception e) {
            throw new IOException("Löschen fehlgeschlagen: " + smbPath + " – " + e.getMessage(), e);
        }
        smbPath.getFileSystem().invalidateCache(smbPath.rawPath());
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) {
        throw new UnsupportedOperationException("copy wird für SMB-Netzwerkpfade nicht unterstützt");
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        SmbPath src = asSmbPath(source);
        SmbPath dst = asSmbPath(target);
        if (src.getFileSystem() != dst.getFileSystem()) {
            throw new IOException("Verschieben zwischen unterschiedlichen SMB-Freigaben wird nicht unterstützt");
        }
        boolean replaceExisting = false;
        for (CopyOption option : options) {
            if (option == StandardCopyOption.REPLACE_EXISTING) replaceExisting = true;
        }

        DiskShare share = src.getFileSystem().share();
        try (DiskEntry entry = share.open(
                src.smbPath(),
                Set.of(AccessMask.DELETE, AccessMask.FILE_READ_ATTRIBUTES),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null)) {
            entry.rename(dst.smbPath(), replaceExisting);
        } catch (Exception e) {
            throw new IOException("Umbenennen/Verschieben fehlgeschlagen: " + src + " -> " + dst + " – " + e.getMessage(), e);
        }
        src.getFileSystem().invalidateCache(src.rawPath());
        dst.getFileSystem().invalidateCache(dst.rawPath());
    }

    @Override
    public boolean isSameFile(Path path, Path path2) {
        return asSmbPath(path).equals(asSmbPath(path2));
    }

    @Override
    public boolean isHidden(Path path) { return false; }

    @Override
    public FileStore getFileStore(Path path) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        readAttributes(path, BasicFileAttributes.class);
    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
        if (type != BasicFileAttributes.class) {
            throw new UnsupportedOperationException("Nur BasicFileAttributes werden für SMB-Pfade unterstützt");
        }
        SmbPath smbPath = asSmbPath(path);
        SmbFileSystem fs = smbPath.getFileSystem();

        SmbBasicFileAttributes cached = fs.takeCachedAttributes(smbPath.rawPath());
        if (cached != null) return (A) cached;

        if (smbPath.rawPath().isEmpty()) {
            return (A) new SmbBasicFileAttributes(true, 0, System.currentTimeMillis(), System.currentTimeMillis());
        }

        try {
            FileAllInformation info = fs.share().getFileInformation(smbPath.smbPath());
            boolean isDir = info.getStandardInformation().isDirectory();
            long size = info.getStandardInformation().getEndOfFile();
            long modified = info.getBasicInformation().getLastWriteTime().toEpochMillis();
            long created = info.getBasicInformation().getCreationTime().toEpochMillis();
            return (A) new SmbBasicFileAttributes(isDir, size, modified, created);
        } catch (Exception e) {
            throw new NoSuchFileException(smbPath.toString(), null, e.getMessage());
        }
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) {
        throw new UnsupportedOperationException();
    }

    private SmbPath asSmbPath(Path path) {
        if (!(path instanceof SmbPath smbPath)) throw new ProviderMismatchException();
        return smbPath;
    }
}

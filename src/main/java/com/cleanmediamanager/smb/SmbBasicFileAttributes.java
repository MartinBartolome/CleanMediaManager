package com.cleanmediamanager.smb;

import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

/** Minimal {@link BasicFileAttributes} view backed by data queried from an SMB share. */
final class SmbBasicFileAttributes implements BasicFileAttributes {

    private final boolean directory;
    private final long size;
    private final FileTime lastModified;
    private final FileTime creationTime;

    SmbBasicFileAttributes(boolean directory, long size, long lastModifiedMillis, long creationMillis) {
        this.directory = directory;
        this.size = size;
        this.lastModified = FileTime.fromMillis(lastModifiedMillis);
        this.creationTime = FileTime.fromMillis(creationMillis);
    }

    @Override public FileTime lastModifiedTime() { return lastModified; }
    @Override public FileTime lastAccessTime() { return lastModified; }
    @Override public FileTime creationTime() { return creationTime; }
    @Override public boolean isRegularFile() { return !directory; }
    @Override public boolean isDirectory() { return directory; }
    @Override public boolean isSymbolicLink() { return false; }
    @Override public boolean isOther() { return false; }
    @Override public long size() { return size; }
    @Override public Object fileKey() { return null; }
}

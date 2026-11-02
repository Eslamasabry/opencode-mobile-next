package io.github.eslamasabry.oc.engine.composer;

import java.util.Collection;
import kotlinx.io.RawSink;
import kotlinx.io.RawSource;
import kotlinx.io.files.FileMetadata;
import kotlinx.io.files.FileSystem;
import kotlinx.io.files.FileSystemJvmKt;
import kotlinx.io.files.Path;

/** JVM fault fixture: Kotlin seals FileSystem implementations to kotlinx-io itself. */
final class ThrowingFileSystem implements FileSystem {
    private final FileSystem delegate = FileSystemJvmKt.SystemFileSystem;
    private final String operation;
    private final Throwable failure;

    ThrowingFileSystem(String operation, Throwable failure) {
        this.operation = operation;
        this.failure = failure;
    }

    private void check(String current) {
        if (operation.equals(current)) ThrowingFileSystem.<RuntimeException>raise(failure);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void raise(Throwable failure) throws E {
        throw (E) failure;
    }

    @Override public boolean exists(Path path) {
        check("exists");
        return delegate.exists(path);
    }

    @Override public void delete(Path path, boolean mustExist) {
        delegate.delete(path, mustExist);
    }

    @Override public void createDirectories(Path path, boolean mustCreate) {
        delegate.createDirectories(path, mustCreate);
    }

    @Override public void atomicMove(Path source, Path destination) {
        delegate.atomicMove(source, destination);
    }

    @Override public RawSource source(Path path) {
        check("read");
        return delegate.source(path);
    }

    @Override public RawSink sink(Path path, boolean append) {
        return delegate.sink(path, append);
    }

    @Override public FileMetadata metadataOrNull(Path path) {
        check("length");
        return delegate.metadataOrNull(path);
    }

    @Override public Path resolve(Path path) {
        return delegate.resolve(path);
    }

    @Override public Collection<Path> list(Path directory) {
        check("list");
        return delegate.list(directory);
    }
}

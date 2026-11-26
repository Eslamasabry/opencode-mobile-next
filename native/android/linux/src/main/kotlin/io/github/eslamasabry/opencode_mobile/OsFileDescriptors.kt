package io.github.eslamasabry.opencode_mobile

import android.system.ErrnoException
import android.system.Os
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Deterministic close helpers for [FileDescriptor] handles returned by [Os.open].
 *
 * Android libcore's `FileInputStream(FileDescriptor)` and `FileOutputStream(FileDescriptor)` set
 * `isFdOwner = false`, so closing the stream wrapper alone leaks the underlying kernel descriptor.
 */
object OsFileDescriptors {
    /** Runs [block] with [fd] and closes [fd] in `finally` without masking caller exceptions. */
    fun <T> useFd(
        fd: FileDescriptor,
        close: (FileDescriptor) -> Unit = Os::close,
        block: (FileDescriptor) -> T,
    ): T {
        try {
            return block(fd)
        } finally {
            closeQuietly(fd, close)
        }
    }

    /** Wraps [fd] in a [FileInputStream] and closes both the stream and [fd] on exit. */
    fun <T> useInputStream(
        fd: FileDescriptor,
        close: (FileDescriptor) -> Unit = Os::close,
        block: (FileInputStream) -> T,
    ): T = useFd(fd, close) { descriptor -> FileInputStream(descriptor).use(block) }

    /** Wraps [fd] in a [FileOutputStream] and closes both the stream and [fd] on exit. */
    fun <T> useOutputStream(
        fd: FileDescriptor,
        close: (FileDescriptor) -> Unit = Os::close,
        block: (FileOutputStream) -> T,
    ): T = useFd(fd, close) { descriptor -> FileOutputStream(descriptor).use(block) }

    /** Closes [fd] via [close], swallowing [ErrnoException] so cleanup never masks caller errors. */
    fun closeQuietly(
        fd: FileDescriptor,
        close: (FileDescriptor) -> Unit = Os::close,
    ) {
        try {
            close(fd)
        } catch (_: ErrnoException) {
            // Kernel already released the FD on EBADF/EINTR; never retry Linux close(2) or mask errors.
        }
    }
}

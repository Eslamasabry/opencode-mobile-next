package io.github.eslamasabry.oc.engine.voice.models

import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.io.writeString

/**
 * The file operations the pack installer needs, with "/" paths (plan 10 §7.2). Blocking: callers run them on
 * `AppDispatchers.io`. A seam so B-MEDIA-027's "publishing fails" and "cleanup fails" paths can be tested.
 */
public interface ModelFiles {
    /** Length in bytes, or null when the file does not exist. */
    public fun size(path: String): Long?

    public fun isDirectory(path: String): Boolean

    public fun createDirectories(path: String)

    /** Deletes a file or an empty directory; no error when it is missing. */
    public fun delete(path: String)

    /** Names directly inside [path]; empty when it is missing. */
    public fun list(path: String): List<String>

    /** Atomic rename that replaces [to]. */
    public fun move(from: String, to: String)

    public fun readText(path: String): String?

    public fun writeText(path: String, text: String)

    public fun openAppend(path: String): ByteSink

    /** A reader positioned at [offset] (a platform may seek; the portable one skips). */
    public fun openRead(path: String, offset: Long = 0): ByteSource
}

public interface ByteSink {
    public fun write(bytes: ByteArray, offset: Int, length: Int)

    public fun flush()

    public fun close()
}

public interface ByteSource {
    /** -1 at the end. */
    public fun read(buffer: ByteArray): Int

    public fun skip(bytes: Long)

    public fun close()
}

/** Deletes [path] and everything under it; no error when it is missing. */
public fun ModelFiles.deleteTree(path: String) {
    if (isDirectory(path)) for (name in list(path)) deleteTree("$path/$name")
    delete(path)
}

/** kotlinx-io over the platform file system (works on Android and the desktop JVM). */
public class SystemModelFiles(private val fs: FileSystem = SystemFileSystem) : ModelFiles {
    override fun size(path: String): Long? = fs.metadataOrNull(Path(path))?.takeIf { it.isRegularFile }?.size

    override fun isDirectory(path: String): Boolean = fs.metadataOrNull(Path(path))?.isDirectory == true

    override fun createDirectories(path: String): Unit = fs.createDirectories(Path(path), mustCreate = false)

    override fun delete(path: String): Unit = fs.delete(Path(path), mustExist = false)

    override fun list(path: String): List<String> =
        if (isDirectory(path)) fs.list(Path(path)).map { it.name } else emptyList()

    override fun move(from: String, to: String): Unit = fs.atomicMove(Path(from), Path(to))

    override fun readText(path: String): String? {
        if (size(path) == null) return null
        return fs.source(Path(path)).buffered().use { it.readString() }
    }

    override fun writeText(path: String, text: String) {
        fs.sink(Path(path)).buffered().use { it.writeString(text) }
    }

    override fun openAppend(path: String): ByteSink = RawByteSink(fs.sink(Path(path), append = true))

    override fun openRead(path: String, offset: Long): ByteSource =
        RawByteSource(fs.source(Path(path))).also { if (offset > 0) it.skip(offset) }

    private class RawByteSink(private val raw: RawSink) : ByteSink {
        private val buffer = Buffer()

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            buffer.write(bytes, offset, offset + length)
            raw.write(buffer, buffer.size)
        }

        override fun flush(): Unit = raw.flush()

        override fun close(): Unit = raw.close()
    }

    private class RawByteSource(raw: RawSource) : ByteSource {
        private val source = raw.buffered()

        override fun read(buffer: ByteArray): Int = source.readAtMostTo(buffer, 0, buffer.size)

        override fun skip(bytes: Long) {
            val scratch = ByteArray(64 * 1024)
            var left = bytes
            while (left > 0) {
                val n = source.readAtMostTo(scratch, 0, minOf(left, scratch.size.toLong()).toInt())
                if (n < 0) break
                left -= n
            }
        }

        override fun close(): Unit = source.close()
    }
}

/** Reads all bytes; tests and the small marker files only. */
internal fun ByteSource.readAllBytes(): ByteArray {
    val out = Buffer()
    val chunk = ByteArray(64 * 1024)
    while (true) {
        val n = read(chunk)
        if (n < 0) break
        out.write(chunk, 0, n)
    }
    return out.readByteArray()
}

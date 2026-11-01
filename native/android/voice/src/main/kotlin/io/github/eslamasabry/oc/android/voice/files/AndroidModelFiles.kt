package io.github.eslamasabry.oc.android.voice.files

import android.system.ErrnoException
import android.system.OsConstants
import io.github.eslamasabry.oc.engine.voice.models.ByteSink
import io.github.eslamasabry.oc.engine.voice.models.ByteSource
import io.github.eslamasabry.oc.engine.voice.models.DigestFactory
import io.github.eslamasabry.oc.engine.voice.models.ModelFiles
import io.github.eslamasabry.oc.engine.voice.models.Sha256Digest
import io.github.eslamasabry.oc.engine.voice.models.StorageFullException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * [ModelFiles] on `java.io`: reads SEEK (so the verified stamp reads about 2 MiB per file, M4) and a full disk
 * (`ENOSPC`) becomes [StorageFullException] → `DownloadNoSpace`, decided by errno, never by message text.
 */
public class AndroidModelFiles : ModelFiles {
    override fun size(path: String): Long? = File(path).takeIf { it.isFile }?.length()

    override fun isDirectory(path: String): Boolean = File(path).isDirectory

    override fun createDirectories(path: String) {
        val dir = File(path)
        if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) throw IOException("mkdirs refused")
    }

    override fun delete(path: String) {
        val file = File(path)
        if (file.exists() && !file.delete()) throw IOException("delete refused")
    }

    override fun list(path: String): List<String> = File(path).list()?.toList() ?: emptyList()

    override fun move(from: String, to: String) {
        java.nio.file.Files.move(
            File(from).toPath(),
            File(to).toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
        )
    }

    override fun readText(path: String): String? = File(path).takeIf { it.isFile }?.readText()

    override fun writeText(path: String, text: String) = mapFull { File(path).writeText(text) }

    override fun openAppend(path: String): ByteSink {
        val out = FileOutputStream(path, true)
        return object : ByteSink {
            override fun write(bytes: ByteArray, offset: Int, length: Int) = mapFull { out.write(bytes, offset, length) }

            override fun flush() = mapFull { out.fd.sync() }

            override fun close() = out.close()
        }
    }

    override fun openRead(path: String, offset: Long): ByteSource {
        val file = RandomAccessFile(path, "r")
        if (offset > 0) file.seek(offset)
        return object : ByteSource {
            override fun read(buffer: ByteArray): Int = file.read(buffer)

            override fun skip(bytes: Long) = file.seek(file.filePointer + bytes)

            override fun close() = file.close()
        }
    }

    private inline fun mapFull(block: () -> Unit) {
        try {
            block()
        } catch (e: IOException) {
            if (isNoSpace(e)) throw StorageFullException()
            throw e
        }
    }

    internal companion object {
        fun isNoSpace(error: Throwable): Boolean =
            generateSequence(error) { it.cause }.any { it is ErrnoException && it.errno == OsConstants.ENOSPC }
    }
}

/** The platform digest: much faster than the portable one on a 375 MB model. */
public object PlatformDigests : DigestFactory {
    override fun sha256(): Sha256Digest {
        val digest = MessageDigest.getInstance("SHA-256")
        return object : Sha256Digest {
            override fun update(bytes: ByteArray, offset: Int, length: Int) = digest.update(bytes, offset, length)

            override fun digestHex(): String = digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

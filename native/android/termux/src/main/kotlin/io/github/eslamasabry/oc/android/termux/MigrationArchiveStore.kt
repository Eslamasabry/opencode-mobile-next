package io.github.eslamasabry.oc.android.termux

import io.github.eslamasabry.oc.core.common.AppDispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest

/**
 * Where a copied item lands and how it is proven (Flutter migration_archive_store.dart). Implementations signal a refusal
 * with the module's fixed codes; any other exception counts as `storage`.
 */
public interface TermuxMigrationArchives {
    public fun jobDirectory(jobId: String): Path

    public suspend fun builtinInstalled(): Boolean

    public suspend fun verify(archive: Path, expected: TermuxMigrationArchive)

    public suspend fun importItem(
        jobId: String,
        item: TermuxMigrationItem,
        archive: Path,
        expected: TermuxMigrationArchive,
        cancelled: () -> Boolean,
    )

    public suspend fun imported(jobId: String, item: TermuxMigrationItem, expected: TermuxMigrationArchive): Boolean

    /** Only verified inactive config exports can supply advisory provider labels. */
    public suspend fun providerNames(jobId: String, expected: TermuxMigrationArchive): List<String>

    public suspend fun cleanupPartial(jobId: String)
}

/**
 * Private, bounded USTAR importer. Exports are never activated as configuration. A manifest travels inside the atomic
 * directory rename, so a lost journal write cannot cause a second import or overwrite a person's imported project.
 *
 * Layout under [support] (the app's private files dir): `migrations/<job>/` transfer cache and staging,
 * `projects/termux-<job>/` imported projects, `linux/ubuntu/root/.oc-migration-exports/<job>/<item>/` private copies.
 */
public class TermuxMigrationArchiveStore(
    private val support: Path,
    private val dispatchers: AppDispatchers,
) : TermuxMigrationArchives {
    public companion object {
        public const val MAX_BYTES: Long = 512L * 1024 * 1024
        public const val MAX_ENTRIES: Int = 20_000
        public const val MANIFEST: String = ".oc-migration-manifest.json"
        private val JOB = Regex("^[a-zA-Z0-9_-]{1,80}$")
        private val NO_FOLLOW = arrayOf(LinkOption.NOFOLLOW_LINKS)
        private const val MODE_DIR = 448 // 0700
        private const val MODE_FILE = 384 // 0600
        private const val MODE_EXEC = 448 // 0700
    }

    override fun jobDirectory(jobId: String): Path {
        job(jobId)
        return support.resolve("migrations").resolve(jobId)
    }

    override suspend fun builtinInstalled(): Boolean = withContext(dispatchers.io) {
        try {
            existingParents(support.resolve("linux/ubuntu"))
            Files.isRegularFile(support.resolve("linux/ubuntu.ready"), *NO_FOLLOW)
        } catch (_: TermuxMigrationException) {
            false
        } catch (_: IOException) {
            false
        }
    }

    override suspend fun verify(archive: Path, expected: TermuxMigrationArchive): Unit = withContext(dispatchers.io) { verifyNow(archive, expected) }

    private fun verifyNow(archive: Path, expected: TermuxMigrationArchive) {
        expected(expected)
        try {
            if (!Files.isRegularFile(archive, *NO_FOLLOW) || Files.size(archive) != expected.bytes) {
                fail(TermuxMigrationFailure.invalidArchive)
            }
            if (sha256Of(archive) != expected.sha256) fail(TermuxMigrationFailure.checksumMismatch)
        } catch (_: IOException) {
            fail(TermuxMigrationFailure.storage)
        }
    }

    override suspend fun importItem(
        jobId: String,
        item: TermuxMigrationItem,
        archive: Path,
        expected: TermuxMigrationArchive,
        cancelled: () -> Boolean,
    ): Unit = withContext(dispatchers.io) {
        job(jobId)
        cancel(cancelled)
        if (importedNow(jobId, item, expected)) return@withContext
        verifyNow(archive, expected)
        cancel(cancelled)
        val stage = jobDirectory(jobId).resolve("staging").resolve(item.name)
        try {
            directory("migrations/$jobId/staging")
            deleteStage(stage)
            Files.createDirectory(stage)
            mode(stage, MODE_DIR)
            val entries = extract(archive, stage, item, cancelled)
            verifyNow(archive, expected)
            cancel(cancelled)
            val manifest = buildJsonObject {
                put("version", 1)
                put("archiveBytes", expected.bytes)
                put("archiveSha256", expected.sha256)
                put("item", item.name)
                put("entries", buildJsonArray { entries.forEach { add(it.toJson()) } })
            }
            writeFlushed(stage.resolve(MANIFEST), manifest.toString().toByteArray(Charsets.UTF_8))
            secureStage(stage, entries)
            val destination = destination(jobId, item)
            if (item != TermuxMigrationItem.projects && !builtinInstalledNow()) fail(TermuxMigrationFailure.unavailable)
            directory(if (item == TermuxMigrationItem.projects) "projects" else "linux/ubuntu/root/.oc-migration-exports/$jobId")
            if (Files.exists(destination, *NO_FOLLOW) || Files.isSymbolicLink(destination)) {
                fail(TermuxMigrationFailure.destinationConflict)
            }
            cancel(cancelled)
            Files.move(stage, destination, StandardCopyOption.ATOMIC_MOVE)
            // Re-read destination hashes before reporting a verified import.
            importedNow(jobId, item, expected)
        } catch (e: TermuxMigrationException) {
            throw e
        } catch (_: Exception) {
            fail(TermuxMigrationFailure.storage)
        } finally {
            // Only uncommitted staging belongs to this attempt. Imported files remain.
            try {
                deleteStage(stage)
            } catch (_: IOException) {
                fail(TermuxMigrationFailure.storage)
            }
        }
    }

    private fun builtinInstalledNow(): Boolean =
        try {
            existingParents(support.resolve("linux/ubuntu"))
            Files.isRegularFile(support.resolve("linux/ubuntu.ready"), *NO_FOLLOW)
        } catch (_: Exception) {
            false
        }

    override suspend fun imported(jobId: String, item: TermuxMigrationItem, expected: TermuxMigrationArchive): Boolean =
        withContext(dispatchers.io) { importedNow(jobId, item, expected) }

    private fun importedNow(jobId: String, item: TermuxMigrationItem, expected: TermuxMigrationArchive): Boolean {
        job(jobId)
        expected(expected)
        val destination = destination(jobId, item)
        if (!Files.exists(destination, *NO_FOLLOW) && !Files.isSymbolicLink(destination)) return false
        try {
            existingParents(destination)
            val marker = destination.resolve(MANIFEST)
            if (!Files.isRegularFile(marker, *NO_FOLLOW) || Files.size(marker) > 16L * 1024 * 1024) {
                fail(TermuxMigrationFailure.destinationConflict)
            }
            val data = Json.parseToJsonElement(String(Files.readAllBytes(marker), Charsets.UTF_8)) as JsonObject
            if ((data["version"] as? JsonPrimitive)?.intOrNull != 1 ||
                (data["archiveBytes"] as? JsonPrimitive)?.longOrNull != expected.bytes ||
                (data["archiveSha256"] as? JsonPrimitive)?.takeIf { it.isString }?.content != expected.sha256 ||
                (data["item"] as? JsonPrimitive)?.takeIf { it.isString }?.content != item.name
            ) {
                fail(TermuxMigrationFailure.destinationConflict)
            }
            val entries = data["entries"] as JsonArray
            if (entries.size > MAX_ENTRIES) fail(TermuxMigrationFailure.destinationConflict)
            val paths = HashSet<String>()
            var total = 0L
            for (value in entries) {
                val entry = value as JsonObject
                val path = path((entry["path"] as JsonPrimitive).content)
                if (!paths.add(path) || path == MANIFEST) fail(TermuxMigrationFailure.destinationConflict)
                val target = destination.resolve(path)
                existingParents(target.parent)
                when ((entry["type"] as? JsonPrimitive)?.content) {
                    "directory" -> if (!Files.isDirectory(target, *NO_FOLLOW)) fail(TermuxMigrationFailure.destinationConflict)
                    "file" -> {
                        val bytes = (entry["bytes"] as JsonPrimitive).longOrNull ?: fail(TermuxMigrationFailure.destinationConflict)
                        total += bytes
                        if (bytes < 0 ||
                            total > MAX_BYTES ||
                            !Files.isRegularFile(target, *NO_FOLLOW) ||
                            Files.size(target) != bytes ||
                            sha256Of(target) != (entry["sha256"] as JsonPrimitive).content
                        ) {
                            fail(TermuxMigrationFailure.destinationConflict)
                        }
                    }
                    else -> fail(TermuxMigrationFailure.destinationConflict)
                }
                if (modeOf(target) != (entry["mode"] as JsonPrimitive).intOrNull) fail(TermuxMigrationFailure.destinationConflict)
            }
            var count = 0
            Files.walk(destination).use { stream ->
                for (entity in stream) {
                    if (entity == destination) continue
                    val relative = destination.relativize(entity).toString()
                    if (relative == MANIFEST) continue
                    if (++count > MAX_ENTRIES || relative !in paths) fail(TermuxMigrationFailure.destinationConflict)
                }
            }
            if (count != paths.size) fail(TermuxMigrationFailure.destinationConflict)
            return true
        } catch (_: Exception) {
            fail(TermuxMigrationFailure.destinationConflict)
        }
    }

    override suspend fun providerNames(jobId: String, expected: TermuxMigrationArchive): List<String> =
        withContext(dispatchers.io) {
            if (!importedNow(jobId, TermuxMigrationItem.config, expected)) {
                emptyList()
            } else {
                readMigrationProviderNames(destination(jobId, TermuxMigrationItem.config))
            }
        }

    override suspend fun cleanupPartial(jobId: String): Unit = withContext(dispatchers.io) {
        job(jobId)
        val job = jobDirectory(jobId)
        try {
            // Transfer archives and offsets can hold private source data. Once completed, keep only the verified
            // import and its in-directory receipt.
            existingParents(job, allowMissing = true)
            deleteStage(job)
        } catch (_: IOException) {
            fail(TermuxMigrationFailure.storage)
        }
    }

    private fun destination(jobId: String, item: TermuxMigrationItem): Path =
        if (item == TermuxMigrationItem.projects) {
            support.resolve("projects").resolve("termux-$jobId")
        } else {
            support.resolve("linux/ubuntu/root/.oc-migration-exports").resolve(jobId).resolve(item.name)
        }

    private class Entry(val path: String, val directory: Boolean, val bytes: Long = 0, val sha256: String = "", val mode: Int) {
        fun toJson(): JsonObject = buildJsonObject {
            put("path", path)
            put("type", if (directory) "directory" else "file")
            if (!directory) {
                put("bytes", bytes)
                put("sha256", sha256)
            }
            put("mode", mode)
        }
    }

    private fun extract(archive: Path, stage: Path, item: TermuxMigrationItem, cancelled: () -> Boolean): List<Entry> {
        val entries = LinkedHashMap<String, Entry>()
        val explicit = HashSet<String>()
        var payload = 0L
        var headers = 0
        var zeros = 0
        FileChannel.open(archive, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { input ->
            val archiveBytes = input.size()
            while (input.position() < archiveBytes) {
                cancel(cancelled)
                val header = read(input, 512)
                if (header.all { it == 0.toByte() }) {
                    zeros++
                    continue
                }
                if (zeros != 0 || ++headers > MAX_ENTRIES) fail(TermuxMigrationFailure.invalidArchive)
                var checksum = 0L
                for (i in 0 until 512) checksum += if (i in 148 until 156) 32 else (header[i].toInt() and 0xff)
                if (octal(header, 148, 8) != checksum ||
                    text(header, 257, 6) != "ustar" ||
                    String(header, 263, 2, Charsets.US_ASCII) != "00"
                ) {
                    fail(TermuxMigrationFailure.invalidArchive)
                }
                val type = header[156].toInt() and 0xff
                if (type != 0 && type != 48 && type != 53) fail(TermuxMigrationFailure.unsupportedEntry)
                if (text(header, 157, 100).isNotEmpty()) fail(TermuxMigrationFailure.unsupportedEntry)
                val prefix = text(header, 345, 155)
                var raw = (if (prefix.isEmpty()) "" else "$prefix/") + text(header, 0, 100)
                val size = octal(header, 124, 12)
                val sourceMode = octal(header, 100, 8)
                if (type == 53 && size != 0L) fail(TermuxMigrationFailure.invalidArchive)
                if ((raw == "./" || raw == ".") && type == 53) {
                    if (!explicit.add(".")) fail(TermuxMigrationFailure.invalidArchive)
                    continue
                }
                if (raw.startsWith("./")) raw = raw.substring(2)
                if (type == 53 && raw.endsWith("/")) raw = raw.dropLast(1)
                val path = path(raw)
                if (path == MANIFEST || !explicit.add(path)) fail(TermuxMigrationFailure.invalidArchive)
                payload += size
                if (payload > MAX_BYTES || size > archiveBytes - input.position()) fail(TermuxMigrationFailure.tooLarge)
                val parts = path.split('/')
                for (i in 1 until parts.size) stageDirectory(stage, parts.take(i).joinToString("/"), entries)
                if (type == 53) {
                    stageDirectory(stage, path, entries)
                } else {
                    if (entries.containsKey(path)) fail(TermuxMigrationFailure.invalidArchive)
                    val target = stage.resolve(path)
                    val digest = MessageDigest.getInstance("SHA-256")
                    val mode = if (item == TermuxMigrationItem.projects && (sourceMode and 73L) != 0L) MODE_EXEC else MODE_FILE
                    FileChannel.open(
                        target,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE_NEW,
                        LinkOption.NOFOLLOW_LINKS,
                    ).use { output ->
                        var remaining = size
                        while (remaining > 0) {
                            cancel(cancelled)
                            val chunk = read(input, minOf(remaining, 65536L).toInt())
                            digest.update(chunk)
                            val buffer = ByteBuffer.wrap(chunk)
                            while (buffer.hasRemaining()) output.write(buffer)
                            remaining -= chunk.size
                        }
                        output.force(true)
                    }
                    entries[path] = Entry(path, directory = false, bytes = size, sha256 = hex(digest.digest()), mode = mode)
                    if (entries.size > MAX_ENTRIES) fail(TermuxMigrationFailure.tooLarge)
                    val padding = ((512 - size % 512) % 512).toInt()
                    if (padding > 0 && !read(input, padding).all { it == 0.toByte() }) fail(TermuxMigrationFailure.invalidArchive)
                }
            }
        }
        if (zeros < 2) fail(TermuxMigrationFailure.invalidArchive)
        return entries.values.toList()
    }

    private fun stageDirectory(stage: Path, path: String, entries: MutableMap<String, Entry>) {
        val existing = entries[path]
        if (existing != null) {
            if (!existing.directory) fail(TermuxMigrationFailure.invalidArchive)
            return
        }
        if (entries.size >= MAX_ENTRIES) fail(TermuxMigrationFailure.tooLarge)
        Files.createDirectory(stage.resolve(path))
        entries[path] = Entry(path, directory = true, mode = MODE_DIR)
    }

    /** Creates [relative] under support one 0700 directory at a time; an existing non-directory (a link too) conflicts. */
    private fun directory(relative: String) {
        existingParents(support)
        var current = support
        for (part in relative.split('/')) {
            current = current.resolve(part)
            if (!Files.exists(current, *NO_FOLLOW) && !Files.isSymbolicLink(current)) {
                Files.createDirectory(current)
                mode(current, MODE_DIR)
            } else if (!Files.isDirectory(current, *NO_FOLLOW)) {
                fail(TermuxMigrationFailure.destinationConflict)
            }
        }
    }

    /** Every directory from support down to [directory] is a real directory, never a link. */
    private fun existingParents(directory: Path, allowMissing: Boolean = false) {
        val relative = if (directory == support) null else support.relativize(directory)
        if (relative != null && relative.toString().split('/').any { it == ".." }) fail(TermuxMigrationFailure.destinationConflict)
        var current = support
        val parts = listOf("") + (relative?.toString()?.split('/')?.filter { it.isNotEmpty() } ?: emptyList())
        for (part in parts) {
            if (part.isNotEmpty()) current = current.resolve(part)
            val missing = !Files.exists(current, *NO_FOLLOW) && !Files.isSymbolicLink(current)
            if (allowMissing && missing) return
            if (!Files.isDirectory(current, *NO_FOLLOW)) fail(TermuxMigrationFailure.destinationConflict)
        }
    }

    private fun deleteStage(stage: Path) {
        if (Files.isDirectory(stage, *NO_FOLLOW)) {
            deleteTree(stage)
        } else if (Files.exists(stage, *NO_FOLLOW) || Files.isSymbolicLink(stage)) {
            fail(TermuxMigrationFailure.destinationConflict)
        }
    }

    private fun secureStage(stage: Path, entries: List<Entry>) {
        // The enclosing staging directory is 0700 from creation: u=rwX,go= for everything, then 0700 for executables.
        Files.walkFileTree(
            stage,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    mode(dir, MODE_DIR)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (!attrs.isRegularFile) fail(TermuxMigrationFailure.storage)
                    mode(file, MODE_FILE)
                    return FileVisitResult.CONTINUE
                }
            },
        )
        for (entry in entries) if (!entry.directory && entry.mode == MODE_EXEC) mode(stage.resolve(entry.path), MODE_EXEC)
    }

    private fun read(input: FileChannel, bytes: Int): ByteArray {
        val buffer = ByteBuffer.allocate(bytes)
        while (buffer.hasRemaining()) {
            if (input.read(buffer) <= 0) fail(TermuxMigrationFailure.invalidArchive)
        }
        return buffer.array()
    }

    private fun text(bytes: ByteArray, start: Int, length: Int): String {
        var end = start
        while (end < start + length && bytes[end] != 0.toByte()) end++
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, start, end - start))
                .toString()
        } catch (_: Exception) {
            fail(TermuxMigrationFailure.invalidArchive)
        }
    }

    private fun octal(bytes: ByteArray, start: Int, length: Int): Long {
        val value = text(bytes, start, length).trim()
        if (!Regex("^[0-7]+$").matches(value) || value.length > 21) fail(TermuxMigrationFailure.invalidArchive)
        return value.toLong(8)
    }

    private fun path(path: String): String {
        if (path.isEmpty() || path.length > 255 || path.startsWith("/") || path.contains('\\') ||
            path.any { it.code < 32 || it.code == 127 }
        ) {
            fail(TermuxMigrationFailure.invalidArchive)
        }
        val parts = path.split('/')
        if (parts.size > 64 || parts.any { it.isEmpty() || it == "." || it == ".." }) fail(TermuxMigrationFailure.invalidArchive)
        return path
    }

    private fun job(job: String) {
        if (!JOB.matches(job)) fail(TermuxMigrationFailure.invalidSelection)
    }

    private fun expected(expected: TermuxMigrationArchive) {
        if (expected.bytes < 1024 || expected.bytes > MAX_BYTES || expected.bytes % 512 != 0L || !SHA256.matches(expected.sha256)) {
            fail(TermuxMigrationFailure.invalidArchive)
        }
    }

    private fun cancel(cancelled: () -> Boolean) {
        if (cancelled()) fail(TermuxMigrationFailure.cancelled)
    }
}

internal fun sha256Of(path: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { input ->
        val buffer = ByteArray(65536)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return hex(digest.digest())
}

internal fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }

/** The permission bits (`mode & 0777`) of [path], not following a link. */
internal fun modeOf(path: Path): Int {
    var mode = 0
    for (permission in Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS)) {
        mode = mode or when (permission) {
            PosixFilePermission.OWNER_READ -> 256
            PosixFilePermission.OWNER_WRITE -> 128
            PosixFilePermission.OWNER_EXECUTE -> 64
            PosixFilePermission.GROUP_READ -> 32
            PosixFilePermission.GROUP_WRITE -> 16
            PosixFilePermission.GROUP_EXECUTE -> 8
            PosixFilePermission.OTHERS_READ -> 4
            PosixFilePermission.OTHERS_WRITE -> 2
            PosixFilePermission.OTHERS_EXECUTE -> 1
        }
    }
    return mode
}

/** Sets the permission bits of [path]. Callers prove [path] is no link first. */
internal fun mode(path: Path, mode: Int) {
    val permissions = HashSet<PosixFilePermission>()
    val order = listOf(
        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
        PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_EXECUTE,
        PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_EXECUTE,
    )
    for ((index, permission) in order.withIndex()) if ((mode and (256 shr index)) != 0) permissions.add(permission)
    try {
        Files.setPosixFilePermissions(path, permissions)
    } catch (_: Exception) {
        fail(TermuxMigrationFailure.storage)
    }
}

/** Deletes a directory tree without following any link inside it (a link is removed, never its target). */
internal fun deleteTree(root: Path) {
    Files.walkFileTree(
        root,
        object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                if (exc != null) throw exc
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        },
    )
}

internal fun writeFlushed(path: Path, bytes: ByteArray) {
    FileOutputStream(path.toFile()).use { output ->
        output.write(bytes)
        output.flush()
        output.fd.sync()
    }
}

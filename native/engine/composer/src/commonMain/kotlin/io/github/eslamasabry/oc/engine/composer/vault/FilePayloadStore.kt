package io.github.eslamasabry.oc.engine.composer.vault

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

/**
 * Payloads as `<root>/<ownerKey>/<id>.blob`, published through a sibling `.tmp` and an atomic rename: the exact
 * layout Flutter's `FileDraftAttachmentStorage` writes under the app-support directory, so a vault written by the
 * Flutter build reads here unchanged. [io] is the injected I/O dispatcher (AppDispatchers.io; rule 6).
 */
public class FilePayloadStore(
    private val root: Path,
    private val io: CoroutineDispatcher,
    private val fs: FileSystem = SystemFileSystem,
) : PayloadStore {
    private fun file(ownerKey: String, id: String): Path {
        checkedIdentity(ownerKey, id)
        return Path(Path(root, ownerKey), "$id.blob")
    }

    override suspend fun list(ownerKey: String?): List<PayloadEntry> =
        withContext(io) {
            if (!fs.exists(root)) return@withContext emptyList()
            val out = mutableListOf<PayloadEntry>()
            for (dir in fs.list(root)) {
                val owner = dir.name
                val meta = fs.metadataOrNull(dir) ?: continue
                if (!meta.isDirectory || !HASH_ID.matches(owner) || (ownerKey != null && owner != ownerKey)) continue
                for (file in fs.list(dir)) {
                    val name = file.name
                    if (!PAYLOAD.matches(name)) continue
                    val fileMeta = fs.metadataOrNull(file) ?: continue
                    if (!fileMeta.isRegularFile) continue
                    out += PayloadEntry(owner, name.take(64), fileMeta.size, committed = name.endsWith(".blob"))
                }
            }
            out
        }

    override suspend fun length(ownerKey: String, id: String): Long? =
        withContext(io) {
            val path = file(ownerKey, id)
            fs.metadataOrNull(path)?.takeIf { it.isRegularFile }?.size
        }

    override suspend fun read(ownerKey: String, id: String, expectedBytes: Long): ByteArray =
        withContext(io) {
            val path = file(ownerKey, id)
            if (!fs.exists(path)) throw PayloadFormatException("Attachment missing")
            // A length check followed by a whole read is not a bound: read at most expected + 1 bytes.
            val buffer = Buffer()
            fs.source(path).buffered().use { source ->
                while (buffer.size <= expectedBytes) {
                    val read = source.readAtMostTo(buffer, expectedBytes + 1 - buffer.size)
                    if (read < 0) break
                }
            }
            if (buffer.size != expectedBytes) throw PayloadFormatException("Attachment size changed")
            buffer.readByteArray()
        }

    override suspend fun write(ownerKey: String, id: String, bytes: ByteArray): Unit =
        withContext(io) {
            val path = file(ownerKey, id)
            try {
                fs.createDirectories(Path(root, ownerKey))
                val temporary = Path(Path(root, ownerKey), "$id.tmp")
                fs.sink(temporary).buffered().use { it.write(bytes) }
                fs.atomicMove(temporary, path)
            } catch (e: IOException) {
                throw PayloadStoreException(classify(e))
            }
        }

    override suspend fun delete(ownerKey: String, id: String): Unit =
        withContext(io) {
            val path = file(ownerKey, id)
            try {
                fs.delete(path, mustExist = false)
                // Also a partial publication (Flutter only ever deleted the .blob, so dead .tmp files counted
                // against the 256 MiB budget forever).
                fs.delete(Path(Path(root, ownerKey), "$id.tmp"), mustExist = false)
            } catch (e: IOException) {
                throw PayloadStoreException(classify(e))
            }
        }

    private fun classify(e: IOException): PayloadFailure {
        // ENOSPC has no portable code in kotlinx-io; the JVM and Android messages say "No space left on device".
        val text = e.message.orEmpty()
        return if (text.contains("No space", ignoreCase = true) || text.contains("ENOSPC")) {
            PayloadFailure.Quota
        } else {
            PayloadFailure.Unavailable
        }
    }

    private companion object {
        val PAYLOAD = Regex("^[0-9a-f]{64}\\.(blob|tmp)$")
    }
}

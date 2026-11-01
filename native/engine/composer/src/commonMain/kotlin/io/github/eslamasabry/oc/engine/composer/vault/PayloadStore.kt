package io.github.eslamasabry.oc.engine.composer.vault

/** Why a payload store refused to keep bytes (Flutter `DraftAttachmentStorageFailure`). */
public enum class PayloadFailure {
    /** The app's own budget for a vault root or a draft is exhausted. */
    Limit,

    /** The platform refused for lack of space. */
    Quota,

    /** The store could not be opened or answered with an error not about space. */
    Unavailable,
}

/** Thrown by a [PayloadStore] (an I/O boundary); the vault turns it into a Failure value. */
public class PayloadStoreException(public val failure: PayloadFailure) : Exception(failure.name)

/** Thrown when a payload is missing, has the wrong size or a bad identity (Flutter `FormatException`). */
public class PayloadFormatException(reason: String) : Exception(reason)

/** One payload a store holds, described without loading its bytes (Flutter `DraftPayloadEntry`). */
public data class PayloadEntry(val ownerKey: String, val id: String, val bytes: Long, val committed: Boolean = true) {
    val key: String get() = "$ownerKey/$id"
}

/**
 * Where one vault root keeps its bytes (Flutter `DraftAttachmentStorage`): `<ownerKey>/<id>` keys, durable and
 * atomic. Native builds use [FilePayloadStore] with the Flutter file layout.
 */
public interface PayloadStore {
    public suspend fun list(ownerKey: String? = null): List<PayloadEntry>

    public suspend fun length(ownerKey: String, id: String): Long?

    /** Exactly [expectedBytes] long or [PayloadFormatException]. */
    public suspend fun read(ownerKey: String, id: String, expectedBytes: Long): ByteArray

    /** Atomic publication: a reader sees the old payload or the new one, never a prefix. */
    public suspend fun write(ownerKey: String, id: String, bytes: ByteArray)

    /** A missing payload is not an error. */
    public suspend fun delete(ownerKey: String, id: String)
}

internal val HASH_ID: Regex = Regex("^[0-9a-f]{64}$")

internal fun checkedIdentity(ownerKey: String, id: String) {
    if (!HASH_ID.matches(ownerKey) || !HASH_ID.matches(id)) throw PayloadFormatException("Invalid attachment identity")
}

/** In-memory store for previews and tests. [failWrites] makes writes throw; [partial] adds an uncommitted entry. */
public class MemoryPayloadStore : PayloadStore {
    private val blobs = LinkedHashMap<String, ByteArray>()
    private val partials = LinkedHashMap<String, Long>()
    public var failWrites: PayloadFailure? = null
    public var failDeletes: Boolean = false
    public var failLists: Boolean = false

    public val size: Int get() = blobs.size

    public fun keys(): Set<String> = blobs.keys.toSet()

    /** Test seam: what a process killed between the temporary write and the rename leaves behind. */
    public fun addPartial(ownerKey: String, id: String, bytes: Long) {
        partials["$ownerKey/$id"] = bytes
    }

    /** Test seam: change bytes behind the vault's back. */
    public fun tamper(ownerKey: String, id: String, bytes: ByteArray) {
        blobs["$ownerKey/$id"] = bytes
    }

    override suspend fun list(ownerKey: String?): List<PayloadEntry> {
        if (failLists) throw PayloadStoreException(PayloadFailure.Unavailable)
        val committed = blobs.map { (key, value) -> entry(key, value.size.toLong(), true) }
        val partial = partials.map { (key, bytes) -> entry(key, bytes, false) }
        return (committed + partial).filter { ownerKey == null || it.ownerKey == ownerKey }
    }

    private fun entry(key: String, bytes: Long, committed: Boolean): PayloadEntry =
        PayloadEntry(key.substringBefore('/'), key.substringAfter('/'), bytes, committed)

    override suspend fun length(ownerKey: String, id: String): Long? {
        checkedIdentity(ownerKey, id)
        return blobs["$ownerKey/$id"]?.size?.toLong()
    }

    override suspend fun read(ownerKey: String, id: String, expectedBytes: Long): ByteArray {
        checkedIdentity(ownerKey, id)
        val bytes = blobs["$ownerKey/$id"] ?: throw PayloadFormatException("Attachment missing")
        if (bytes.size.toLong() != expectedBytes) throw PayloadFormatException("Attachment size changed")
        return bytes.copyOf()
    }

    override suspend fun write(ownerKey: String, id: String, bytes: ByteArray) {
        checkedIdentity(ownerKey, id)
        failWrites?.let { throw PayloadStoreException(it) }
        blobs["$ownerKey/$id"] = bytes.copyOf()
    }

    override suspend fun delete(ownerKey: String, id: String) {
        if (failDeletes) throw PayloadStoreException(PayloadFailure.Unavailable)
        val key = "$ownerKey/$id"
        blobs.remove(key)
        partials.remove(key)
    }
}

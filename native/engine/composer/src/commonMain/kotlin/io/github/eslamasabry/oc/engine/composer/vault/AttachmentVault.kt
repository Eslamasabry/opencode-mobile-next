package io.github.eslamasabry.oc.engine.composer.vault

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.PromptAttachment
import kotlin.coroutines.cancellation.CancellationException

/**
 * Small persisted metadata for one attachment (Flutter `DraftAttachmentRef`): data-URL payloads live in a
 * [PayloadStore] under [blob] (the SHA-256 of the whole `data:` URL's UTF-8 bytes); `file:`/`http(s):` stay as [url].
 */
public data class AttachmentRef(
    val filename: String,
    val mime: String,
    val blob: String? = null,
    val url: String? = null,
    val bytes: Long = 0,
) {
    override fun toString(): String = "AttachmentRef(${filename.length} chars, $mime, blob=${blob != null}, $bytes B)"
}

/** What a restore gave back, and the names of what it could not (one composer note lists them). */
public data class AttachmentRecovery(val attachments: List<PromptAttachment>, val unavailable: List<String>) {
    public companion object {
        public val Empty: AttachmentRecovery = AttachmentRecovery(emptyList(), emptyList())
    }
}

/** Why the vault refused (mapped to [Failure.StorageRefused] at the command boundary). */
public enum class VaultProblem { TooMany, CannotSurviveRestart, DraftLimit, RootLimit, Quota, Unavailable }

/**
 * Port of Flutter `DraftAttachmentVault` (`lib/state/draft_attachments.dart`): content identity, bounds, owner
 * isolation and ordering. Payloads are committed before metadata; [collect] runs only after metadata succeeded.
 * One vault per root: drafts, saved prompts and photo recovery never share a root, so one collector never sees
 * another's payloads (B-STATE-136).
 */
public class AttachmentVault(private val storage: PayloadStore) {
    public companion object {
        public const val DRAFT_ROOT: String = "draft-attachments-v1"
        public const val STASH_ROOT: String = "prompt-stash-attachments-v1"
        public const val PHOTO_ROOT: String = "prompt-photo-recovery-v1"
        public const val MAX_DISK_BYTES: Long = 256L * 1024 * 1024
        public const val MAX_DRAFT_BYTES: Long = 32L * 1024 * 1024
        public const val MAX_ATTACHMENTS: Int = 5

        /** The owner directory: SHA-256 of the owner (profile id) text. */
        public fun ownerKey(owner: String): String = Sha256.hex(owner)

        /** The payload identity of a data URL. */
        public fun blobId(dataUrl: String): String = Sha256.hex(dataUrl)

        /** Every vault refusal is the flat list's [Failure.StorageRefused] (plan 03 §10.1; INTEGRATE-W1 D3). */
        @Suppress("UNUSED_PARAMETER")
        public fun problemFailure(problem: VaultProblem): Failure = Failure.StorageRefused()
    }

    /** Stores [attachments] for [owner]; nothing in metadata changes on a refusal. */
    public suspend fun store(owner: String, attachments: List<PromptAttachment>): Outcome<List<AttachmentRef>> =
        try {
            storeOrThrow(owner, attachments).ok()
        } catch (e: CancellationException) {
            throw e
        } catch (e: VaultRefusal) {
            problemFailure(e.problem).failed()
        } catch (e: PayloadStoreException) {
            problemFailure(if (e.failure == PayloadFailure.Quota) VaultProblem.Quota else VaultProblem.Unavailable).failed()
        } catch (e: PayloadFormatException) {
            problemFailure(VaultProblem.Unavailable).failed()
        }

    internal class VaultRefusal(val problem: VaultProblem) : Exception(problem.name)

    internal suspend fun storeOrThrow(owner: String, attachments: List<PromptAttachment>): List<AttachmentRef> {
        if (attachments.isEmpty()) return emptyList()
        if (attachments.size > MAX_ATTACHMENTS) throw VaultRefusal(VaultProblem.TooMany)
        val refs = mutableListOf<AttachmentRef>()
        val ownerId = ownerKey(owner)
        var draftBytes = 0L
        var diskBytes: Long? = null
        for (attachment in attachments) {
            val scheme = schemeOf(attachment.url)
            if (scheme != "data") {
                if (scheme !in REMOTE) throw VaultRefusal(VaultProblem.CannotSurviveRestart)
                refs += AttachmentRef(filename = attachment.filename, mime = attachment.mime, url = attachment.url)
                continue
            }
            val payload = attachment.url.encodeToByteArray()
            val ref = AttachmentRef(attachment.filename, attachment.mime, blob = Sha256.hex(payload), bytes = payload.size.toLong())
            draftBytes += ref.bytes
            if (draftBytes > MAX_DRAFT_BYTES) throw VaultRefusal(VaultProblem.DraftLimit)
            val id = ref.blob!!
            val existing = storage.length(ownerId, id)
            val intact =
                existing == ref.bytes &&
                    try {
                        Sha256.hex(storage.read(ownerId, id, ref.bytes)) == id
                    } catch (e: PayloadFormatException) {
                        false
                    }
            if (!intact) {
                if (existing != null) storage.delete(ownerId, id)
                val disk = diskBytes ?: storage.list().sumOf { it.bytes }
                if (disk + ref.bytes > MAX_DISK_BYTES) throw VaultRefusal(VaultProblem.RootLimit)
                storage.write(ownerId, id, payload)
                diskBytes = disk + ref.bytes
            }
            refs += ref
        }
        return refs
    }

    /**
     * Reads [refs] back. A blob must have its recorded size and hash; a `file:` URL is restored only at the same
     * location ([sameLocation]); anything else is named in [AttachmentRecovery.unavailable]. Never consumes the source.
     */
    public suspend fun restore(owner: String, refs: List<AttachmentRef>, sameLocation: Boolean): AttachmentRecovery {
        val attachments = mutableListOf<PromptAttachment>()
        val missing = mutableListOf<String>()
        val ownerId = ownerKey(owner)
        var restoredBytes = 0L
        for (ref in refs) {
            val restored =
                try {
                    restoreOne(ownerId, ref, sameLocation, attachments.size, restoredBytes)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            if (restored == null) {
                missing += ref.filename
            } else {
                if (ref.blob != null) restoredBytes += ref.bytes
                attachments += restored
            }
        }
        return AttachmentRecovery(attachments.toList(), missing.toList())
    }

    private suspend fun restoreOne(
        ownerId: String,
        ref: AttachmentRef,
        sameLocation: Boolean,
        count: Int,
        restoredBytes: Long,
    ): PromptAttachment? {
        if (count >= MAX_ATTACHMENTS) return null
        val url: String
        val blob = ref.blob
        if (blob != null) {
            // A malformed negative length must not buy more recovery capacity.
            if (ref.bytes < 0 || ref.bytes > MAX_DRAFT_BYTES - restoredBytes) return null
            if (!HASH_ID.matches(blob)) return null
            if (storage.length(ownerId, blob) != ref.bytes) return null
            val bytes = storage.read(ownerId, blob, ref.bytes)
            url = bytes.decodeToString()
            if (Sha256.hex(url) != blob || schemeOf(url) != "data") return null
        } else {
            url = ref.url.orEmpty()
            val scheme = schemeOf(url)
            if (scheme !in REMOTE || (scheme == "file" && !sameLocation)) return null
        }
        return PromptAttachment(mime = ref.mime, filename = ref.filename, url = url)
    }

    /**
     * Deletes this root's unreferenced payloads (and every uncommitted `.tmp`), never user files. [owner] limits the
     * sweep to one profile. False when anything failed; nothing referenced is ever deleted.
     */
    public suspend fun collect(retained: Map<String, List<AttachmentRef>>, owner: String? = null): Boolean =
        try {
            val keep = HashSet<String>()
            for ((profile, refs) in retained) {
                for (ref in refs) {
                    val blob = ref.blob ?: continue
                    if (HASH_ID.matches(blob)) keep += "${ownerKey(profile)}/$blob"
                }
            }
            for (entry in storage.list(owner?.let(::ownerKey))) {
                if (!entry.committed || entry.key !in keep) storage.delete(entry.ownerKey, entry.id)
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
}

private val REMOTE = setOf("file", "http", "https")
private val SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.\\-]*):")

/** Dart `Uri.tryParse(url)?.scheme`, lower-cased; empty when there is none. */
public fun schemeOf(url: String): String = SCHEME.find(url)?.groupValues?.get(1)?.lowercase().orEmpty()

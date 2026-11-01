package io.github.eslamasabry.oc.engine.media.attach

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.platform.ActivityResults
import io.github.eslamasabry.oc.core.domain.platform.AppPermission
import io.github.eslamasabry.oc.core.domain.platform.DocumentTarget
import io.github.eslamasabry.oc.core.domain.platform.PermissionGate
import io.github.eslamasabry.oc.core.domain.platform.PermissionStatus
import io.github.eslamasabry.oc.core.domain.platform.PhotoTarget
import io.github.eslamasabry.oc.core.domain.platform.PickedFile
import io.github.eslamasabry.oc.core.domain.platform.TreeTarget
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.time.Clock
import kotlin.time.Instant

val PNG: ByteArray = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)

fun png(tag: Int): ByteArray = PNG + byteArrayOf(tag.toByte())

class FixedClock(var millis: Long = 1_760_000_000_000) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(millis)
}

/**
 * The composer's photo record and draft join, in memory, with the composer's rules (PhotoRecoveryStore and
 * DraftPhotoRecovery, `:engine:composer`): one record at a time, a refused commit refuses, bytes sniffed and capped,
 * the join dedupes on the blob and profile removal wins.
 */
class FakePhotoStore(vararg profiles: String) : PhotoRecord, DraftPhotoJoin {
    val profiles: MutableSet<String> = profiles.toMutableSet()
    var refuse: Boolean = false
    var vaultFails: Boolean = false
    private var record: PendingPhotoInfo? = null
    private var blob: ByteArray? = null
    private var counter = 0

    /** Draft blobs per "profile/session". */
    val drafts: MutableMap<String, MutableList<String>> = mutableMapOf()
    val directories: MutableMap<String, String?> = mutableMapOf()

    override val pending: PendingPhotoInfo? get() = record

    override suspend fun begin(profileId: String, sessionId: String, directory: String?, workspace: String?): Outcome<String> {
        if (record != null) return Outcome.Failed(Failure.Media(MediaProblem.PhotoPending))
        if (refuse) return Outcome.Failed(Failure.Media(MediaProblem.PhotoStorage))
        val id = "${1_760_000_000_000_000L + counter++}"
        record = PendingPhotoInfo(id, profileId, sessionId, hasBytes = false)
        directories["$profileId/$sessionId"] = directory
        return Outcome.Ok(id)
    }

    override suspend fun complete(id: String, name: String, bytes: ByteArray): Outcome<Boolean> {
        val r = record
        if (r?.id != id) return Outcome.Ok(false)
        if (bytes.size > 10 * 1024 * 1024) return Outcome.Failed(Failure.Media(MediaProblem.PhotoTooLarge))
        if (MagicBytes.imageMime(bytes) == null) return Outcome.Failed(Failure.Media(MediaProblem.PhotoUnsupported))
        if (vaultFails || refuse) return Outcome.Failed(Failure.Media(MediaProblem.PhotoStorage))
        blob = bytes
        record = r.copy(hasBytes = true, name = name)
        return Outcome.Ok(true)
    }

    override suspend fun markFailed(id: String): Outcome<Unit> {
        if (refuse) return Outcome.Failed(Failure.Media(MediaProblem.PhotoStorage))
        record = record?.takeIf { it.id == id }?.copy(failed = true) ?: record
        return Outcome.Ok(Unit)
    }

    override suspend fun discard(id: String): Outcome<Unit> {
        if (refuse) return Outcome.Failed(Failure.Media(MediaProblem.PhotoStorage))
        if (record?.id == id) {
            record = null
            blob = null
        }
        return Outcome.Ok(Unit)
    }

    override suspend fun join(): RecoveryResult {
        val r = record ?: return RecoveryResult.NothingPending
        if (r.profileId !in profiles) {
            drafts.keys.removeAll { it.startsWith("${r.profileId}/") }
            discard(r.id)
            return RecoveryResult.ProfileRemoved
        }
        val bytes = blob ?: return RecoveryResult.RetryRequired
        val key = bytes.contentHashCode().toString()
        val draft = drafts.getOrPut("${r.profileId}/${r.sessionId}") { mutableListOf() }
        if (key !in draft) draft += key
        discard(r.id)
        return RecoveryResult.Attached
    }
}

/** Files by uri: the bytes a platform read would return. */
class FakeFiles {
    val bytes: MutableMap<String, ByteArray> = mutableMapOf()

    fun file(uri: String, data: ByteArray, name: String = uri, mime: String? = null, size: Long? = data.size.toLong()): PickedFile {
        bytes[uri] = data
        return PickedFile(uri, name, mime, size)
    }
}

class FakeActivity(private val onLaunch: () -> Unit = {}) : ActivityResults {
    var images: suspend (Int) -> Outcome<ImmutableList<PickedFile>> = { Outcome.Ok(persistentListOf()) }
    var photo: suspend (PhotoTarget) -> Outcome<PickedFile?> = { Outcome.Ok(null) }
    var document: Outcome<PickedFile?> = Outcome.Ok(null)
    val launches: MutableList<String> = mutableListOf()
    var lastLimit: Int? = null

    override suspend fun pickImages(limit: Int): Outcome<ImmutableList<PickedFile>> {
        launches += "images"
        lastLimit = limit
        onLaunch()
        return images(limit)
    }

    override suspend fun takePhoto(target: PhotoTarget): Outcome<PickedFile?> {
        launches += "camera"
        onLaunch()
        return photo(target)
    }

    override suspend fun openDocument(mimeTypes: List<String>): Outcome<PickedFile?> {
        launches += "document"
        return document
    }

    override suspend fun createDocument(suggestedName: String, mime: String): Outcome<DocumentTarget?> = Outcome.Ok(null)

    override suspend fun openDocumentTree(): Outcome<TreeTarget?> = Outcome.Ok(null)
}

class FakePermissions(var status: PermissionStatus = PermissionStatus.Granted) : PermissionGate {
    override suspend fun status(permission: AppPermission): PermissionStatus = status

    override suspend fun request(permission: AppPermission): PermissionStatus = status

    override fun openSettings(permission: AppPermission) = Unit
}

/** Reads like ContentAccess.readBounded: limit + 1 bytes, then AttachmentTooLarge. Records the limit it was given. */
class FakeReader(private val files: FakeFiles) : PickedFileReader {
    var lastLimit: Long? = null

    override suspend fun readBounded(file: PickedFile, limit: Long): Outcome<ByteArray> {
        lastLimit = limit
        val data = files.bytes[file.uri] ?: return Outcome.Failed(Failure.Media(MediaProblem.AttachmentContentMissing))
        return if (data.size > limit) Outcome.Failed(Failure.Media(MediaProblem.AttachmentTooLarge)) else Outcome.Ok(data)
    }
}

/** A codec that keeps the bytes (the Android one scales and re-encodes); [rewrite] replaces the output. */
class FakeCodec(private val files: FakeFiles) : ImageCodec {
    var rewrite: ((ByteArray) -> ByteArray)? = null
    var fail: Failure? = null
    val prepared: MutableList<String> = mutableListOf()

    private fun out(bytes: ByteArray): Outcome<PreparedImage> {
        fail?.let { return Outcome.Failed(it) }
        val result = rewrite?.invoke(bytes) ?: bytes
        return Outcome.Ok(PreparedImage(result, "image/png", 10, 10))
    }

    override suspend fun preparePhoto(source: PickedFile, rules: PhotoRules): Outcome<PreparedImage> {
        prepared += source.uri
        val bytes = files.bytes[source.uri] ?: return Outcome.Failed(Failure.Media(MediaProblem.PhotoUnavailable))
        return out(bytes)
    }

    override suspend fun prepareBytes(bytes: ByteArray, declaredMime: String?, rules: PhotoRules): Outcome<PreparedImage> = out(bytes)
}

class FakeClipboard : ClipboardImages {
    var image: Outcome<ClipboardImage?> = Outcome.Ok(null)
    var lastMax: Int? = null

    override suspend fun hasImage(): Boolean = (image as? Outcome.Ok)?.value != null

    override suspend fun read(maxBytes: Int): Outcome<ClipboardImage?> {
        lastMax = maxBytes
        return image
    }
}

class FakeCaptures(private val files: FakeFiles) : CaptureFiles {
    val deleted: MutableList<String> = mutableListOf()

    fun uri(id: String) = "capture/$id.jpg"

    override fun target(captureId: String): PhotoTarget = PhotoTarget(uri(captureId))

    override suspend fun existing(captureId: String): PickedFile? {
        val data = files.bytes[uri(captureId)] ?: return null
        if (data.isEmpty()) return null
        return PickedFile(uri(captureId), "$captureId.jpg", "image/jpeg", data.size.toLong())
    }

    override suspend fun delete(captureId: String) {
        deleted += captureId
        files.bytes.remove(uri(captureId))
    }
}

object TestWords : AttachmentWords {
    override fun officeHeader(name: String): String = "Contents of $name"

    override fun officeTruncated(): String = "Cut short"
}

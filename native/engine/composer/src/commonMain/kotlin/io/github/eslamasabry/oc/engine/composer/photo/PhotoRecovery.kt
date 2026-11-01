package io.github.eslamasabry.oc.engine.composer.photo

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.composer.drafts.Drafts
import io.github.eslamasabry.oc.engine.composer.drafts.SessionDraft
import io.github.eslamasabry.oc.engine.composer.store.ComposerPrefs
import io.github.eslamasabry.oc.engine.composer.store.ComposerProfiles
import io.github.eslamasabry.oc.engine.composer.store.StoredFormatException
import io.github.eslamasabry.oc.engine.composer.store.asObject
import io.github.eslamasabry.oc.engine.composer.store.boolOrNull
import io.github.eslamasabry.oc.engine.composer.store.commit
import io.github.eslamasabry.oc.engine.composer.store.jsonString
import io.github.eslamasabry.oc.engine.composer.store.parseJson
import io.github.eslamasabry.oc.engine.composer.store.str
import io.github.eslamasabry.oc.engine.composer.store.strOrNull
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentRef
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentVault
import io.github.eslamasabry.oc.engine.composer.vault.attachmentRefFromJson
import io.github.eslamasabry.oc.engine.composer.vault.toJson
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * The one camera/picker request in flight (Flutter `PendingPromptPhoto`,
 * `lib/state/prompt_photos.dart`), stored as `oc.pendingPromptPhoto`. Committed before the picker
 * launches, so a photo handed back after Android killed the app joins its own conversation's draft.
 * [ref] is set once the bytes are in the photo vault; [path] once the platform named a file;
 * [failed] when the platform reported an error.
 */
public data class PendingPhoto(
    val id: String,
    val profileId: String,
    val sessionId: String,
    val directory: String? = null,
    val workspace: String? = null,
    val ref: AttachmentRef? = null,
    val path: String? = null,
    val name: String? = null,
    val failed: Boolean = false,
) {
    override fun toString(): String =
        "PendingPhoto($id, ref=${ref != null}, path=${path != null}, failed=$failed)"
}

internal fun PendingPhoto.toJson(): JsonObject = buildJsonObject {
    put("id", JsonPrimitive(id))
    put("profileID", JsonPrimitive(profileId))
    put("sessionID", JsonPrimitive(sessionId))
    put("directory", jsonString(directory))
    put("workspace", jsonString(workspace))
    put("ref", ref?.toJson() ?: JsonNull)
    put("path", jsonString(path))
    put("name", jsonString(name))
    put("failed", JsonPrimitive(failed))
}

internal fun pendingPhotoFromJson(value: JsonObject): PendingPhoto =
    PendingPhoto(
        id = value.str("id"),
        profileId = value.str("profileID"),
        sessionId = value.str("sessionID"),
        directory = value.strOrNull("directory"),
        workspace = value.strOrNull("workspace"),
        ref = (value["ref"] as? JsonObject)?.let(::attachmentRefFromJson),
        path = value.strOrNull("path"),
        name = value.strOrNull("name"),
        failed = value.boolOrNull("failed") == true,
    )

/** Why a photo was refused (Flutter `PromptPhotoFailure`). */
public enum class PhotoProblem {
    Storage,
    Pending,
    TooLarge,
    Unsupported,
    Unavailable,
    SensitiveIdentity,
}

public sealed interface PhotoResult<out T> {
    public data class Ok<out T>(val value: T) : PhotoResult<T>

    public data class Refused(val problem: PhotoProblem) : PhotoResult<Nothing>
}

/**
 * Image checks (Flutter `PromptPhotoStore.readPhoto`): ≤ 10 MiB and a real PNG, JPEG, GIF or WebP
 * by its bytes.
 */
public object PhotoSniff {
    public const val MAX_BYTES: Int = 10 * 1024 * 1024
    public const val MAX_EDGE: Int = 2048
    public const val QUALITY: Int = 85

    public fun mimeOf(bytes: ByteArray): String? {
        fun prefix(vararg v: Int) =
            bytes.size >= v.size && v.indices.all { (bytes[it].toInt() and 0xff) == v[it] }
        fun ascii(at: Int, s: String) =
            bytes.size >= at + s.length && s.indices.all { bytes[at + it].toInt() == s[it].code }
        return when {
            prefix(137, 80, 78, 71, 13, 10, 26, 10) -> "image/png"
            prefix(255, 216, 255) -> "image/jpeg"
            ascii(0, "GIF87a") || ascii(0, "GIF89a") -> "image/gif"
            ascii(0, "RIFF") && ascii(8, "WEBP") -> "image/webp"
            else -> null
        }
    }

    /**
     * The attachment for picked bytes, named [name]; fake extensions and oversized images are
     * refused.
     */
    public fun attachment(name: String, bytes: ByteArray): PhotoResult<PromptAttachment> {
        if (bytes.size > MAX_BYTES) return PhotoResult.Refused(PhotoProblem.TooLarge)
        val mime = mimeOf(bytes) ?: return PhotoResult.Refused(PhotoProblem.Unsupported)
        if (bytes.isEmpty()) return PhotoResult.Refused(PhotoProblem.Unsupported)
        return PhotoResult.Ok(
            PromptAttachment(
                mime = mime,
                filename = name,
                url = "data:$mime;base64,${Base64.encode(bytes)}",
            )
        )
    }
}

/**
 * The photo recovery record (plan 09 §10.6 camera recovery; B-STATE-088, B-MEDIA-008). The platform
 * picker (`:android:platform` ActivityResults) calls [begin] and launches only on Ok; it hands
 * bytes to [complete]; on cancel it calls [discard]. One serial lane.
 */
public class PhotoRecoveryStore(
    private val prefs: ComposerPrefs,
    private val vault: AttachmentVault,
    private val redactor: Redactor,
    private val clock: Clock,
) {
    public companion object {
        public const val KEY: String = "oc.pendingPromptPhoto"
    }

    private val lane = Mutex()
    private var counter = 0

    /** The record as stored; a corrupt one reads as nothing pending. */
    public val pending: PendingPhoto?
        get() {
            val raw = prefs.string(KEY) ?: return null
            return try {
                pendingPhotoFromJson(parseJson(raw).asObject())
            } catch (e: StoredFormatException) {
                null
            }
        }

    /**
     * A request that would route by secret-looking text is refused, never rerouted (Flutter
     * `savedPromptIdentity`).
     */
    private fun safe(photo: PendingPhoto): Boolean =
        listOfNotNull(
                photo.id,
                photo.profileId,
                photo.sessionId,
                photo.directory,
                photo.workspace,
                photo.path,
                photo.ref?.blob,
                photo.ref?.url,
            )
            .none(redactor::containsSecret)

    private suspend fun save(photo: PendingPhoto): PhotoResult<PendingPhoto> {
        if (!safe(photo)) return PhotoResult.Refused(PhotoProblem.SensitiveIdentity)
        val clean =
            photo.copy(
                name = photo.name?.let(redactor::redact),
                ref =
                    photo.ref?.let {
                        it.copy(
                            filename = redactor.redact(it.filename),
                            mime = redactor.redact(it.mime),
                        )
                    },
            )
        return if (prefs.commit { putString(KEY, clean.toJson().toString()) }) PhotoResult.Ok(clean)
        else PhotoResult.Refused(PhotoProblem.Storage)
    }

    /**
     * Commits the origin before the picker opens. A failed commit means the camera must not launch.
     */
    public suspend fun begin(
        profileId: String,
        sessionId: String,
        directory: String?,
        workspace: String?,
    ): PhotoResult<PendingPhoto> = lane.withLock {
        if (prefs.contains(KEY)) return@withLock PhotoResult.Refused(PhotoProblem.Pending)
        val id = "${clock.now().toEpochMilliseconds() * 1000 + (counter++ % 1000)}"
        save(PendingPhoto(id, profileId, sessionId, directory, workspace))
    }

    /**
     * The platform returned [bytes] for request [id]. Null result: the request was discarded
     * meanwhile.
     */
    public suspend fun complete(
        id: String,
        name: String,
        bytes: ByteArray,
        path: String? = null,
    ): PhotoResult<PendingPhoto?> = lane.withLock {
        val request = pending
        if (request?.id != id) return@withLock PhotoResult.Ok(null)
        if (path != null) {
            val withPath = save(request.copy(path = path, name = name))
            if (withPath is PhotoResult.Refused) return@withLock withPath
        }
        val attachment =
            when (val a = PhotoSniff.attachment(name, bytes)) {
                is PhotoResult.Ok -> a.value
                is PhotoResult.Refused -> return@withLock a
            }
        val refs =
            when (val stored = vault.store(request.profileId, listOf(attachment))) {
                is Outcome.Ok -> stored.value
                is Outcome.Failed -> return@withLock PhotoResult.Refused(PhotoProblem.Storage)
            }
        if (pending?.id != id) return@withLock PhotoResult.Ok(null)
        save((pending ?: request).copy(ref = refs.single(), name = name, failed = false))
    }

    /**
     * The platform reported an error: keep the origin (so a later recovery still lands in the right
     * draft).
     */
    public suspend fun markFailed(id: String): PhotoResult<Unit> = lane.withLock {
        val request = pending
        if (request?.id != id) return@withLock PhotoResult.Ok(Unit)
        when (val r = save(request.copy(failed = true))) {
            is PhotoResult.Ok -> PhotoResult.Ok(Unit)
            is PhotoResult.Refused -> r
        }
    }

    /** The photo's bytes back from the photo vault. */
    public suspend fun readPending(id: String): PhotoResult<PromptAttachment> = lane.withLock {
        val request = pending
        val ref = request?.ref
        if (request?.id != id || ref == null)
            return@withLock PhotoResult.Refused(PhotoProblem.Unavailable)
        val restored = vault.restore(request.profileId, listOf(ref), sameLocation = true)
        restored.attachments.singleOrNull()?.let { PhotoResult.Ok(it) }
            ?: PhotoResult.Refused(PhotoProblem.Unavailable)
    }

    /**
     * Cancel, denial or done: payloads first, then the record. A failed removal keeps the record
     * for a retry.
     */
    public suspend fun discard(id: String): PhotoResult<Unit> = lane.withLock { discardLocked(id) }

    private suspend fun discardLocked(id: String): PhotoResult<Unit> {
        val request = pending
        if (request == null) {
            // A corrupt record still blocks the picker: remove it.
            if (prefs.contains(KEY) && !prefs.commit { remove(KEY) })
                return PhotoResult.Refused(PhotoProblem.Storage)
            return PhotoResult.Ok(Unit)
        }
        if (request.id != id) return PhotoResult.Ok(Unit)
        // Publication may have succeeded before the reference commit (or a process death).
        if (!vault.collect(emptyMap(), owner = request.profileId)) {
            return PhotoResult.Refused(PhotoProblem.Storage)
        }
        return if (prefs.commit { remove(KEY) }) PhotoResult.Ok(Unit)
        else PhotoResult.Refused(PhotoProblem.Storage)
    }

    /** Profile deletion: a pending photo of that profile (or an unattributed one) goes with it. */
    public suspend fun clearForProfile(profileId: String): Boolean = lane.withLock {
        // A missing record, or another profile's record, does not prove this owner's root is empty.
        if (!vault.collect(emptyMap(), owner = profileId)) return@withLock false
        val request =
            pending ?: return@withLock !prefs.contains(KEY) || prefs.commit { remove(KEY) }
        if (request.profileId != profileId && request.profileId.isNotEmpty()) return@withLock true
        discardLocked(request.id) is PhotoResult.Ok
    }
}

public enum class PhotoRecoveryResult {
    NothingPending,
    Attached,
    ProfileRemoved,
    RetryRequired,
}

/**
 * Bootstrap step before any conversation reads its draft (port of
 * `lib/state/draft_photo_recovery.dart`): a photo the camera handed back after the app was killed
 * joins the draft of the conversation that asked for it. Runs in the drafts lane. Profile removal
 * always wins over recovery.
 */
public class DraftPhotoRecovery(
    private val photos: PhotoRecoveryStore,
    private val drafts: Drafts,
    private val draftVault: AttachmentVault,
    private val prefs: ComposerPrefs,
    private val profiles: ComposerProfiles,
    private val redactor: Redactor,
    private val clock: Clock,
) {
    private fun owned(photo: PendingPhoto) =
        photo.profileId.isNotEmpty() &&
            profiles.exists(photo.profileId) &&
            !drafts.isClosing(photo.profileId)

    public suspend fun recover(): PhotoRecoveryResult = drafts.inLane { recoverLocked() }

    private suspend fun removed(photo: PendingPhoto): PhotoRecoveryResult {
        if (!drafts.readable) return PhotoRecoveryResult.RetryRequired
        val kept =
            drafts.all().filterValues {
                it.profileId.isNotEmpty() && it.profileId != photo.profileId
            }
        if (kept.size != drafts.all().size && !drafts.writeIndexLocked(kept))
            return PhotoRecoveryResult.RetryRequired
        if (!draftVault.collect(emptyMap(), owner = photo.profileId))
            return PhotoRecoveryResult.RetryRequired
        if (photos.discard(photo.id) !is PhotoResult.Ok) return PhotoRecoveryResult.RetryRequired
        return PhotoRecoveryResult.ProfileRemoved
    }

    private suspend fun recoverLocked(): PhotoRecoveryResult {
        val photo = photos.pending ?: return PhotoRecoveryResult.NothingPending
        if (!owned(photo)) return removed(photo)
        if (photo.sessionId.isEmpty()) return PhotoRecoveryResult.RetryRequired
        val ref =
            photo.ref
                ?: return PhotoRecoveryResult
                    .RetryRequired // the platform still has to hand the bytes back
        if (!drafts.readable) return PhotoRecoveryResult.RetryRequired
        val key = SessionDraft.keyFor(photo.profileId, photo.sessionId)
        // A prior run committed the draft but failed to clear the record: finish, without reading
        // the vault again.
        if (drafts.all()[key]?.attachments?.any { it.blob == ref.blob } == true) {
            return if (photos.discard(photo.id) is PhotoResult.Ok) PhotoRecoveryResult.Attached
            else PhotoRecoveryResult.RetryRequired
        }
        val attachment =
            when (val read = photos.readPending(photo.id)) {
                is PhotoResult.Ok -> read.value
                is PhotoResult.Refused -> return PhotoRecoveryResult.RetryRequired
            }
        if (!owned(photo)) return removed(photo)
        if (redactor.containsSecret(attachment.url)) return PhotoRecoveryResult.RetryRequired
        if (
            prefs.bool(Drafts.VAULT_FLAG) != true &&
                !prefs.commit { putBool(Drafts.VAULT_FLAG, true) }
        ) {
            return PhotoRecoveryResult.RetryRequired
        }
        val copied =
            when (
                val stored =
                    draftVault.store(
                        photo.profileId,
                        listOf(attachment.copy(filename = redactor.redact(attachment.filename))),
                    )
            ) {
                is Outcome.Ok -> stored.value.single()
                is Outcome.Failed -> return PhotoRecoveryResult.RetryRequired
            }
        if (!owned(photo)) return removed(photo)
        val all = LinkedHashMap(drafts.all())
        val current = all[key]
        val refs =
            current?.attachments.orEmpty().let {
                if (it.any { r -> r.blob == copied.blob }) it else it + copied
            }
        if (
            refs.size > AttachmentVault.MAX_ATTACHMENTS ||
                refs.sumOf { it.bytes } > AttachmentVault.MAX_DRAFT_BYTES
        ) {
            return PhotoRecoveryResult.RetryRequired
        }
        all[key] =
            SessionDraft(
                sessionId = photo.sessionId,
                profileId = photo.profileId,
                text = current?.text.orEmpty(),
                updatedAt = clock.now().toEpochMilliseconds(),
                attachments = refs,
                directory = if (current == null) photo.directory else current.directory,
                workspace = if (current == null) photo.workspace else current.workspace,
            )
        if (all.size > Drafts.MAX_DRAFTS || !drafts.writeIndexLocked(all))
            return PhotoRecoveryResult.RetryRequired
        if (!owned(photo)) return removed(photo)
        return if (photos.discard(photo.id) is PhotoResult.Ok) PhotoRecoveryResult.Attached
        else PhotoRecoveryResult.RetryRequired
    }
}

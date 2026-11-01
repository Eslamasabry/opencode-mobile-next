package io.github.eslamasabry.oc.engine.media.attach

import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.domain.platform.ActivityResults
import io.github.eslamasabry.oc.core.domain.platform.AppPermission
import io.github.eslamasabry.oc.core.domain.platform.PermissionGate
import io.github.eslamasabry.oc.core.domain.platform.PermissionStatus
import io.github.eslamasabry.oc.core.domain.platform.PhotoTarget
import io.github.eslamasabry.oc.core.domain.platform.PickedFile
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.engine.media.office.OfficeText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/** The conversation a photo or file is for, with the draft location a recovered photo must keep (B-STATE-088). */
public data class AttachTarget(val conversation: ConversationRef, val directory: String? = null, val workspace: String? = null)

/** What an intake added. [skipped] is the first problem that stopped one item while others were added. */
public data class IntakeResult(
    val added: ImmutableList<PromptAttachment>,
    val note: IntakeNote? = null,
    val skipped: Failure? = null,
) {
    public companion object {
        public val Nothing: IntakeResult = IntakeResult(persistentListOf())
    }
}

/** A composer note the feature turns into words (`:ui:words`). */
public sealed interface IntakeNote {
    /** Some picked photos did not fit: the draft holds up to 5 files and 20 MB. */
    public data object DraftFilled : IntakeNote

    public data class SheetAttached(val name: String, val sheets: Int, val rows: Int) : IntakeNote

    public data class DocumentAttached(val name: String) : IntakeNote

    /** "Paste image" found no picture on the clipboard. */
    public data object ClipboardEmpty : IntakeNote
}

/** Which system picker the gallery uses (plan 10 §10.3 step 1): the multi contract rejects a limit of one. */
public sealed interface PhotoPickerChoice {
    public data object Single : PhotoPickerChoice

    public data class Multiple(val limit: Int) : PhotoPickerChoice

    public companion object {
        public fun forRoom(room: Int): PhotoPickerChoice? =
            when {
                room <= 0 -> null
                room == 1 -> Single
                else -> Multiple(room)
            }
    }
}

/** Capture files under `files/camera-capture-v1/` (plan 10 §10.4); the Android side builds the FileProvider URI. */
public interface CaptureFiles {
    public fun target(captureId: String): PhotoTarget

    /** The capture as a picked file, or null when it is missing or zero bytes. */
    public suspend fun existing(captureId: String): PickedFile?

    public suspend fun delete(captureId: String)
}

/**
 * The attachment pipeline (plan 10 §4.6 `AttachmentIntake`, §10.3–10.8): photos, camera, files, clipboard and keyboard
 * pictures, and bytes from viewers. Every path applies the same limits ([AttachmentLimits]), the MIME gate
 * ([PromptAttachmentMime]) or the picture rules ([ImageCodec] + [MagicBytes]). Returns attachments for the composer to
 * hand to `Drafts` (which writes the vault blob before the chip appears); it never stores drafts itself.
 *
 * Deviation from plan 10 §4.6: `fromKeyboard(InsertedContent)` is [fromInserted] taking the bytes the feature read
 * through `ContentAccess.readInserted` — `InsertedContent`/`ContentAccess` are not in core yet.
 */
public class AttachmentIntake(
    private val activity: ActivityResults,
    private val permissions: PermissionGate,
    private val reader: PickedFileReader,
    private val codec: ImageCodec,
    private val clipboard: ClipboardImages,
    private val captures: CaptureFiles,
    private val photos: PhotoRecord,
    private val words: AttachmentWords,
    private val dispatchers: AppDispatchers,
    private val clock: Clock,
) {
    /** Gallery: up to the room left; the pending record exists while the picker is open, and is gone afterwards. */
    public suspend fun pickPhotos(target: AttachTarget, current: List<PromptAttachment>): Outcome<IntakeResult> {
        AttachmentLimits.roomFor(current)?.let { return Outcome.Failed(it) }
        val room = AttachmentLimits.MAX_COUNT - current.size
        val record =
            when (val begun = begin(target)) {
                is Outcome.Ok -> begun.value
                is Outcome.Failed -> return begun
            }
        try {
            val files =
                when (val picked = activity.pickImages(room)) {
                    is Outcome.Ok -> picked.value
                    is Outcome.Failed -> return picked
                }
            // A picker that ignores the limit cannot overfill the draft.
            return Outcome.Ok(addPictures(current, files.take(room).map { PictureInput.File(it) }))
        } finally {
            photos.discard(record)
        }
    }

    /** Camera: permission, then the record (a failed write never launches the camera), then the capture. */
    public suspend fun takePhoto(target: AttachTarget, current: List<PromptAttachment>): Outcome<IntakeResult> {
        AttachmentLimits.roomFor(current)?.let { return Outcome.Failed(it) }
        when (permissions.request(AppPermission.Camera)) {
            PermissionStatus.Denied -> return Outcome.Failed(Failure.Media(MediaProblem.CameraDenied))
            PermissionStatus.PermanentlyDenied -> return Outcome.Failed(Failure.Media(MediaProblem.CameraDeniedForever))
            PermissionStatus.Granted, PermissionStatus.NotApplicable -> Unit
        }
        val record =
            when (val begun = begin(target)) {
                is Outcome.Ok -> begun.value
                is Outcome.Failed -> return begun
            }
        try {
            val shot =
                when (val taken = activity.takePhoto(captures.target(record))) {
                    is Outcome.Ok -> taken.value
                    is Outcome.Failed -> return taken
                }
            if (shot == null) return Outcome.Ok(IntakeResult.Nothing) // cancelled: nothing pending, no file kept
            val file = captures.existing(record) ?: shot
            return Outcome.Ok(addPictures(current, listOf(PictureInput.File(file))))
        } finally {
            captures.delete(record)
            photos.discard(record)
        }
    }

    /** "Attach file": count, aggregate, reported size, then a bounded read (plan 10 §10.6). */
    public suspend fun pickFile(current: List<PromptAttachment>): Outcome<IntakeResult> {
        AttachmentLimits.roomFor(current)?.let { return Outcome.Failed(it) }
        val file =
            when (val picked = activity.openDocument(listOf("*/*"))) {
                is Outcome.Ok -> picked.value ?: return Outcome.Ok(IntakeResult.Nothing)
                is Outcome.Failed -> return picked
            }
        AttachmentLimits.checkReportedSize(current, file.sizeBytes)?.let { return Outcome.Failed(it) }
        val limit = AttachmentLimits.readLimit(current)
        val bytes =
            when (val read = reader.readBounded(file, limit)) {
                is Outcome.Ok -> read.value
                is Outcome.Failed -> {
                    val f = read.failure
                    return if (f is Failure.Media && f.problem == MediaProblem.AttachmentTooLarge) {
                        Outcome.Failed(AttachmentLimits.overflowFailure(limit))
                    } else {
                        read
                    }
                }
            }
        if (bytes.size > limit) return Outcome.Failed(AttachmentLimits.overflowFailure(limit))
        return fromBytes(current, file.displayName, file.mime, bytes)
    }

    /** Bytes the person chose to attach (a viewer's "Attach to prompt", a picked file after its read). */
    public suspend fun fromBytes(current: List<PromptAttachment>, name: String?, mime: String?, bytes: ByteArray): Outcome<IntakeResult> {
        AttachmentLimits.roomFor(current)?.let { return Outcome.Failed(it) }
        val clean = AttachmentName.clean(name)
        if (bytes.size > AttachmentLimits.MAX_BYTES) return Outcome.Failed(Failure.Media(MediaProblem.AttachmentTooLarge))
        val (attachment, note) =
            if (OfficeText.isOfficeDocument(clean)) {
                when (val office = office(clean, bytes)) {
                    is Outcome.Ok -> office.value
                    is Outcome.Failed -> return office
                }
            } else {
                val type =
                    PromptAttachmentMime.of(mime, clean, bytes)
                        ?: return Outcome.Failed(Failure.Media(MediaProblem.AttachmentUnsupported))
                PromptAttachment(mime = type, filename = clean, url = encode(type, bytes)) to null
            }
        AttachmentLimits.fits(current, attachment)?.let { return Outcome.Failed(it) }
        return Outcome.Ok(IntakeResult(persistentListOf(attachment), note))
    }

    /** "Paste image from clipboard" (plan 10 §10.8). */
    public suspend fun fromClipboard(current: List<PromptAttachment>): Outcome<IntakeResult> {
        AttachmentLimits.roomFor(current)?.let { return Outcome.Failed(it) }
        val image =
            when (val read = clipboard.read(AttachmentLimits.CLIPBOARD_MAX_BYTES)) {
                is Outcome.Ok -> read.value ?: return Outcome.Ok(IntakeResult(persistentListOf(), IntakeNote.ClipboardEmpty))
                is Outcome.Failed -> return read
            }
        return fromInserted(current, image.mime, image.bytes)
    }

    /** A keyboard-inserted or pasted picture, already read (≤ 10 MiB) by the platform. */
    public suspend fun fromInserted(current: List<PromptAttachment>, mime: String?, bytes: ByteArray): Outcome<IntakeResult> {
        AttachmentLimits.roomFor(current)?.let { return Outcome.Failed(it) }
        if (bytes.size > AttachmentLimits.CLIPBOARD_MAX_BYTES) return Outcome.Failed(Failure.Media(MediaProblem.ClipboardImageTooLarge))
        val type = mime?.substringBefore(';')?.trim()?.lowercase()?.ifEmpty { null } ?: "image/png"
        val name = "pasted-image-${clock.now().toEpochMilliseconds()}.${MagicBytes.extensionFor(type)}"
        val result = addPictures(current, listOf(PictureInput.Bytes(name, type, bytes)))
        if (result.added.isEmpty() && result.skipped != null) return Outcome.Failed(result.skipped)
        return Outcome.Ok(result)
    }

    // ---- pictures

    private sealed interface PictureInput {
        data class File(val file: PickedFile) : PictureInput

        class Bytes(val name: String, val mime: String, val bytes: ByteArray) : PictureInput
    }

    private suspend fun addPictures(current: List<PromptAttachment>, inputs: List<PictureInput>): IntakeResult {
        val added = ArrayList<PromptAttachment>()
        var note: IntakeNote? = null
        var skipped: Failure? = null
        for (input in inputs) {
            val prepared =
                when (input) {
                    is PictureInput.File -> codec.preparePhoto(input.file, PhotoRules.Prompt)
                    is PictureInput.Bytes -> codec.prepareBytes(input.bytes, input.mime, PhotoRules.Prompt)
                }
            val image =
                when (prepared) {
                    is Outcome.Ok -> prepared.value
                    is Outcome.Failed -> {
                        skipped = skipped ?: prepared.failure
                        continue
                    }
                }
            if (image.bytes.size > PhotoRules.Prompt.maxBytes) {
                skipped = skipped ?: Failure.Media(MediaProblem.PhotoTooLarge)
                continue
            }
            // Sniff the output: a fake extension or a codec that wrote something else is refused.
            val mime = MagicBytes.imageMime(image.bytes)
            if (mime == null) {
                skipped = skipped ?: Failure.Media(MediaProblem.PhotoUnsupported)
                continue
            }
            val name =
                when (input) {
                    is PictureInput.File -> AttachmentName.clean(input.file.displayName)
                    is PictureInput.Bytes -> input.name
                }
            val attachment = PromptAttachment(mime = mime, filename = name, url = encode(mime, image.bytes))
            val all = current + added
            if (all.any { it.url == attachment.url }) continue // duplicates are skipped
            val problem = AttachmentLimits.fits(all, attachment)
            if (problem != null) {
                if (problem.problem == MediaProblem.DraftFull) {
                    note = IntakeNote.DraftFilled
                    break
                }
                skipped = skipped ?: problem
                continue
            }
            added += attachment
        }
        return IntakeResult(added.toImmutableList(), note, skipped)
    }

    // ---- office and encoding

    private suspend fun office(name: String, bytes: ByteArray): Outcome<Pair<PromptAttachment, IntakeNote>> {
        val read = withContext(dispatchers.default) { OfficeText.read(name, bytes) }
        val office =
            when (read) {
                is Outcome.Ok -> read.value
                is Outcome.Failed -> return read
            }
        if (office.text.isEmpty()) return Outcome.Failed(Failure.Media(MediaProblem.OfficeEmpty))
        val spreadsheet = !name.lowercase().endsWith(".docx")
        val text =
            buildList {
                add(words.officeHeader(name))
                if (office.truncated) add(words.officeTruncated())
                add("")
                add(office.text)
            }.joinToString("\n")
        val attachment =
            PromptAttachment(
                mime = PromptAttachmentMime.TEXT_PLAIN,
                filename = "$name.${if (spreadsheet) "csv" else "txt"}",
                url = encode(PromptAttachmentMime.TEXT_PLAIN, text.encodeToByteArray()),
            )
        val note = if (spreadsheet) IntakeNote.SheetAttached(name, office.sections, office.lines) else IntakeNote.DocumentAttached(name)
        return Outcome.Ok(attachment to note)
    }

    /** Base64 off the main thread (plan 10 §10.1). */
    private suspend fun encode(mime: String, bytes: ByteArray): String = withContext(dispatchers.default) { DataUrl.encode(mime, bytes) }

    // ---- the pending-photo record

    private suspend fun begin(target: AttachTarget): Outcome<String> =
        photos.begin(target.conversation.profileId.raw, target.conversation.sessionId.raw, target.directory, target.workspace)
}

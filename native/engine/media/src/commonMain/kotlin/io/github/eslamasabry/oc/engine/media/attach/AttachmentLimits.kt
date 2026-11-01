package io.github.eslamasabry.oc.engine.media.attach

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.model.PromptAttachment

/**
 * Every attachment limit in one place (plan 10 §10.1). Plan 03 §10.4 homes this in `:core:domain` so `:engine:composer`
 * can share it; until the core owner adds it there, `:engine:media` is the single copy (core request in the report).
 */
public object AttachmentLimits {
    public const val MAX_COUNT: Int = 5
    public const val MAX_BYTES: Long = 10L * 1024 * 1024
    public const val MAX_TOTAL_BYTES: Long = 20L * 1024 * 1024
    public const val CLIPBOARD_MAX_BYTES: Int = 10 * 1024 * 1024
    public const val MARKUP_MAX_BYTES: Int = 9 * 1024 * 1024

    /** Decoded bytes of the draft's attachments (base64 length and padding; Flutter `_attachmentByteLength`). */
    public fun totalBytes(current: List<PromptAttachment>): Long = current.sumOf { DataUrl.decodedLength(it.url) }

    /** Room for another attachment before anything is read: count first, then the aggregate (plan 10 §10.6 step 2). */
    public fun roomFor(current: List<PromptAttachment>): Failure.Media? =
        when {
            current.size >= MAX_COUNT -> Failure.Media(MediaProblem.DraftFull)
            totalBytes(current) >= MAX_TOTAL_BYTES -> Failure.Media(MediaProblem.AttachmentsTotalTooLarge)
            else -> null
        }

    /** The picker's reported size, a hint only: refuse early when it already says too much. */
    public fun checkReportedSize(current: List<PromptAttachment>, size: Long?): Failure.Media? {
        if (size == null) return null
        if (size > MAX_BYTES) return Failure.Media(MediaProblem.AttachmentTooLarge)
        if (size > 0 && totalBytes(current) + size > MAX_TOTAL_BYTES) return Failure.Media(MediaProblem.AttachmentsTotalTooLarge)
        return null
    }

    /** How many bytes the bounded read may keep: min(10 MiB, 20 MiB − aggregate). */
    public fun readLimit(current: List<PromptAttachment>): Long = minOf(MAX_BYTES, MAX_TOTAL_BYTES - totalBytes(current)).coerceAtLeast(0)

    /** Which limit a read that overflowed [limit] hit. */
    public fun overflowFailure(limit: Long): Failure.Media =
        Failure.Media(if (limit < MAX_BYTES) MediaProblem.AttachmentsTotalTooLarge else MediaProblem.AttachmentTooLarge)

    /** Whether [next] fits after [current] (count and aggregate). */
    public fun fits(current: List<PromptAttachment>, next: PromptAttachment): Failure.Media? =
        when {
            current.size >= MAX_COUNT -> Failure.Media(MediaProblem.DraftFull)
            DataUrl.decodedLength(next.url) > MAX_BYTES -> Failure.Media(MediaProblem.AttachmentTooLarge)
            totalBytes(current) + DataUrl.decodedLength(next.url) > MAX_TOTAL_BYTES ->
                Failure.Media(MediaProblem.AttachmentsTotalTooLarge)
            else -> null
        }
}

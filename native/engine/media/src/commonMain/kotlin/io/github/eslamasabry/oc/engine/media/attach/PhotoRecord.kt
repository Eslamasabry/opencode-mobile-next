package io.github.eslamasabry.oc.engine.media.attach

import io.github.eslamasabry.oc.core.common.Outcome

/** The pending-photo record as the media pipeline sees it (`oc.pendingPromptPhoto`, plan 10 §10.5). */
public data class PendingPhotoInfo(
    val id: String,
    val profileId: String,
    val sessionId: String,
    /** The bytes are already in the recovery vault (the record has a `ref`). */
    val hasBytes: Boolean,
    val path: String? = null,
    val name: String? = null,
    val failed: Boolean = false,
)

/**
 * Port onto the composer's photo record (`:engine:composer` `PhotoRecoveryStore`, which owns the key and its format).
 *
 * `:engine:media` may use `:engine:composer` (ADR §6.2), but on 2026-11-01 the composer module does not compile against
 * the flat `Failure` (INTEGRATE-W1), so the pipeline talks to this port. The adapter is a dozen lines mapping
 * `PhotoResult` to `Outcome` (sketched in docs/native/units/S3-MEDIAATT.md) and lands with INTEGRATE-W2.
 *
 * Refusals are `Failure.Media`: `PhotoPending`, `PhotoStorage` (also for a secret-looking identity), `PhotoTooLarge`,
 * `PhotoUnsupported`, `PhotoUnavailable`.
 */
public interface PhotoRecord {
    public val pending: PendingPhotoInfo?

    /** Commits the origin before any picker or camera opens; returns the record id. A refusal means: do not launch. */
    public suspend fun begin(profileId: String, sessionId: String, directory: String?, workspace: String?): Outcome<String>

    /** Stores [bytes] (sniffed, ≤ 10 MiB) in the recovery vault as the record's `ref`. false = the record is gone. */
    public suspend fun complete(id: String, name: String, bytes: ByteArray): Outcome<Boolean>

    public suspend fun markFailed(id: String): Outcome<Unit>

    /** Payloads first, then the record; a failed removal keeps the record for a retry. */
    public suspend fun discard(id: String): Outcome<Unit>
}

/**
 * The composer's join of a stored photo into its own conversation's draft (`DraftPhotoRecovery.recover()`): dedupes on
 * the vault blob, profile removal wins, any storage trouble is [RecoveryResult.RetryRequired].
 */
public fun interface DraftPhotoJoin {
    public suspend fun join(): RecoveryResult
}

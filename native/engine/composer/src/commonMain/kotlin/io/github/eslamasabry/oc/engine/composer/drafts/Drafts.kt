package io.github.eslamasabry.oc.engine.composer.drafts

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.engine.composer.store.ComposerPrefs
import io.github.eslamasabry.oc.engine.composer.store.ComposerProfiles
import io.github.eslamasabry.oc.engine.composer.store.StoredFormatException
import io.github.eslamasabry.oc.engine.composer.store.commit
import io.github.eslamasabry.oc.engine.composer.store.parseJson
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentRecovery
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentRef
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentVault
import io.github.eslamasabry.oc.engine.composer.vault.schemeOf
import kotlin.concurrent.atomics.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray

/**
 * Where the files of a draft were picked: a `file:` attachment is restored only at the same place.
 */
public data class DraftLocation(val directory: String?, val workspace: String?)

/**
 * How a chat opens (plan 09 §10.8 restore rules): share-intent words and files, and whether the
 * chat is watched or the demo (no drafts at all).
 */
public data class DraftOpening(
    val initialText: String = "",
    val initialAttachments: List<PromptAttachment> = emptyList(),
    val isolated: Boolean = false,
)

/**
 * What the composer starts with. [text] null leaves the field empty. [recoverSavedAttachments] asks
 * the composer to call [Drafts.restoreAttachments] (asynchronously; missing files become one note).
 * [tracksDraft] false: no saves.
 */
public data class DraftRestore(
    val text: String?,
    val attachments: List<PromptAttachment>,
    val recoverSavedAttachments: Boolean,
    val tracksDraft: Boolean,
) {
    override fun toString(): String =
        "DraftRestore(text=${text?.length}, attachments=${attachments.size}, recover=$recoverSavedAttachments, tracks=$tracksDraft)"
}

/**
 * The drafts engine (plan 09 §10.8; port of `lib/state/session_drafts.dart` and
 * `lib/state/connection/drafts.dart`).
 *
 * One app-wide preference `oc.sessionDrafts` holds a JSON array of drafts, keyed per server and
 * conversation; payloads of data-URL attachments live in the `draft-attachments-v1` vault (flag
 * `oc.draftAttachmentVault`). Every change runs in one serial lane: payloads first, then metadata,
 * then collection of unreferenced payloads. A full store (50) refuses new drafts rather than
 * evicting unsent work. A closed (deleted) profile refuses late writes.
 */
public class Drafts(
    private val prefs: ComposerPrefs,
    private val vault: AttachmentVault,
    private val profiles: ComposerProfiles,
    private val clock: Clock,
) {
    public companion object {
        public const val KEY: String = "oc.sessionDrafts"
        public const val VAULT_FLAG: String = "oc.draftAttachmentVault"
        public const val MAX_DRAFTS: Int = 50
        public val AUTOSAVE_DELAY: Duration = 600.milliseconds
    }

    private val lane = Mutex()
    private val closing = AtomicReference<Set<String>>(emptySet())

    private class Snapshot(
        val raw: String?,
        val drafts: Map<String, SessionDraft>,
        val readable: Boolean,
    )

    private val snapshot = AtomicReference(Snapshot(null, emptyMap(), true))

    /**
     * The index as stored; an unreadable index reads as empty and refuses writes (it is never
     * overwritten).
     */
    private fun load(): Snapshot {
        val raw =
            try {
                val value = prefs.string(KEY)
                if (value == null && prefs.contains(KEY)) return unreadable(null)
                value
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return unreadable(null)
            }
        val cached = snapshot.load()
        if (cached.raw == raw && (raw != null || (cached.readable && cached.drafts.isEmpty())))
            return cached
        val next =
            if (raw.isNullOrEmpty()) {
                Snapshot(raw, emptyMap(), true)
            } else {
                try {
                    val list =
                        parseJson(raw) as? JsonArray
                            ?: throw StoredFormatException("Invalid draft index")
                    val drafts = LinkedHashMap<String, SessionDraft>()
                    for (entry in list) {
                        val draft =
                            sessionDraftFromJson(entry)
                                ?: throw StoredFormatException("Invalid draft entry")
                        if (draft.storageKey in drafts)
                            throw StoredFormatException("Duplicate draft entry")
                        drafts[draft.storageKey] = draft
                    }
                    Snapshot(raw, drafts, true)
                } catch (e: StoredFormatException) {
                    Snapshot(raw, emptyMap(), false)
                }
            }
        snapshot.store(next)
        return next
    }

    private fun unreadable(raw: String?): Snapshot =
        Snapshot(raw, emptyMap(), false).also { snapshot.store(it) }

    /** False when the stored index cannot be read: nothing is written over it. */
    public val readable: Boolean
        get() = load().readable

    public fun all(): Map<String, SessionDraft> = load().drafts

    /**
     * The saved draft of [sessionId] on [profileId]; an older unattributed draft counts only with
     * one server.
     */
    public fun draft(profileId: String, sessionId: String): SessionDraft? {
        val drafts = load().drafts
        return drafts[SessionDraft.keyFor(profileId, sessionId)]
            ?: if (profiles.count() <= 1) drafts[sessionId] else null
    }

    /**
     * Bytes the drafts occupy on disk (index text + distinct payloads), for the storage readout in
     * Settings.
     */
    public fun storedBytes(): Long {
        val current = load()
        if (!current.readable) return 0
        val payloads = HashMap<String, Long>()
        for (draft in current.drafts.values) {
            for (ref in draft.attachments) ref.blob?.let {
                payloads["${draft.profileId}/$it"] = ref.bytes
            }
        }
        return (current.raw?.encodeToByteArray()?.size ?: 0).toLong() + payloads.values.sum()
    }

    /**
     * Saves (or, when [text] is blank and no files remain, forgets) the draft of one conversation.
     * [attachments] null keeps the saved files; a list replaces them. Arguments are snapshotted
     * before the lane, so a caller that keeps editing its list cannot change an in-flight save
     * (B-STATE-101).
     *
     * Failures: [Failure.StorageRefused] (unreadable index, refused commit, 50 drafts or a vault
     * limit, a file that cannot survive a restart); [Failure.Refused] `ProfileDeleted`.
     */
    public suspend fun save(
        profileId: String,
        sessionId: String,
        text: String,
        attachments: List<PromptAttachment>? = null,
        location: DraftLocation? = null,
    ): Outcome<Unit> {
        val files = attachments?.toList()
        return lane.withLock { saveLocked(profileId, sessionId, text, files, location) }
    }

    /**
     * Exact text-only preview writes retain whitespace and refuse files that cannot be restored.
     */
    internal suspend fun saveText(
        profileId: String,
        sessionId: String,
        text: String,
        permit: () -> Boolean = { true },
    ): Outcome<Unit> = lane.withLock {
        if (!permit()) return@withLock Failure.Refused(RefusalReason.ProfileDeleted).failed()
        saveLocked(profileId, sessionId, text, null, null, Exactness.TextOnly)
    }

    /**
     * The draft session's whole-state write (full composer, 2026-11-02): the exact text
     * (whitespace preserved; an empty draft with no files is never written) and the authoritative
     * file snapshot — an empty list removes saved files and lets the collector reclaim their
     * payloads. A refusal changes no metadata.
     */
    internal suspend fun saveRevision(
        profileId: String,
        sessionId: String,
        text: String,
        attachments: List<PromptAttachment>,
        permit: () -> Boolean = { true },
    ): Outcome<Unit> {
        val files = attachments.toList()
        return lane.withLock {
            if (!permit()) return@withLock Failure.Refused(RefusalReason.ProfileDeleted).failed()
            saveLocked(profileId, sessionId, text, files, null, Exactness.WholeState)
        }
    }

    private suspend fun saveLocked(
        owner: String,
        sessionId: String,
        text: String,
        files: List<PromptAttachment>?,
        location: DraftLocation?,
        exactness: Exactness = Exactness.Plain,
    ): Outcome<Unit> {
        val current = load()
        if (!current.readable) return Failure.StorageRefused().failed()
        if (owner in closing.load() || (owner.isNotEmpty() && !profiles.exists(owner))) {
            if (text.isBlank() && files.isNullOrEmpty()) return Unit.ok()
            return Failure.Refused(RefusalReason.ProfileDeleted).failed()
        }
        val drafts = current.drafts
        val key = SessionDraft.keyFor(owner, sessionId)
        val single = profiles.count() <= 1
        val previous = drafts[key] ?: if (single) drafts[sessionId] else null
        if (exactness == Exactness.TextOnly && previous?.attachments?.isNotEmpty() == true)
            return Failure.StorageRefused().failed()
        val emptyText = if (exactness == Exactness.Plain) text.isBlank() else text.isEmpty()
        val nothingStored = emptyText && key !in drafts && (!single || sessionId !in drafts)
        if (exactness == Exactness.WholeState) {
            if (files.isNullOrEmpty() && nothingStored) return Unit.ok()
        } else if (files == null && (drafts[key]?.text == text || nothingStored)) {
            return Unit.ok()
        }
        var refs: List<AttachmentRef> = previous?.attachments.orEmpty()
        var filesToStore = files
        if (files == null && refs.isNotEmpty() && previous != null && previous.profileId != owner) {
            // The legacy owner has its own vault directory. Copy and acknowledge its files under
            // the new owner
            // before removing its metadata; a refusal must leave the original draft recoverable
            // (B-STATE-101).
            val recovered = vault.restore(previous.profileId, refs, sameLocation = true)
            if (recovered.unavailable.isNotEmpty()) return Failure.StorageRefused().failed()
            filesToStore = recovered.attachments
        }
        if (filesToStore != null) {
            if (filesToStore.any { schemeOf(it.url) == "data" } && prefs.bool(VAULT_FLAG) != true) {
                // The deletion sweep collects draft payloads only after this marker exists: set it
                // before any payload.
                if (!prefs.commit { putBool(VAULT_FLAG, true) }) {
                    return Failure.StorageRefused().failed()
                }
            }
            when (val stored = vault.store(owner, filesToStore)) {
                is Outcome.Ok -> refs = stored.value
                is Outcome.Failed -> {
                    collectLocked()
                    return stored
                }
            }
        }
        val next = LinkedHashMap(drafts)
        if (single) next.remove(sessionId)
        if (emptyText && refs.isEmpty()) {
            next.remove(key)
        } else {
            next[key] =
                SessionDraft(
                    sessionId = sessionId,
                    profileId = owner,
                    text = text,
                    updatedAt = clock.now().toEpochMilliseconds(),
                    attachments = refs,
                    directory = if (files == null) previous?.directory else location?.directory,
                    workspace = if (files == null) previous?.workspace else location?.workspace,
                )
        }
        if (next.size > MAX_DRAFTS) {
            collectLocked()
            return Failure.StorageRefused().failed()
        }
        if (!writeIndex(next)) {
            collectLocked()
            return Failure.StorageRefused().failed()
        }
        collectLocked()
        return Unit.ok()
    }

    /** Newest first, as Flutter writes it; an empty map removes the key. */
    private suspend fun writeIndex(drafts: Map<String, SessionDraft>): Boolean {
        if (drafts.size > MAX_DRAFTS) return false
        val sorted = drafts.values.sortedByDescending { it.updatedAt }
        return if (sorted.isEmpty()) {
            prefs.commit { remove(KEY) }
        } else {
            prefs.commit { putString(KEY, JsonArray(sorted.map { it.toJson() }).toString()) }
        }
    }

    /**
     * Collects payloads no acknowledged draft references; only after metadata settled, only when
     * readable.
     */
    private suspend fun collectLocked(owner: String? = null): Boolean {
        val current = load()
        if (!current.readable) return false
        if (prefs.bool(VAULT_FLAG) != true) return true
        val retained = HashMap<String, MutableList<AttachmentRef>>()
        for (draft in current.drafts.values) retained.getOrPut(draft.profileId) {
            mutableListOf()
        } += draft.attachments
        return vault.collect(retained, owner)
    }

    /**
     * Reads the saved files back (async; the composer shows one note naming
     * [AttachmentRecovery.unavailable]).
     */
    public suspend fun restoreAttachments(
        profileId: String,
        sessionId: String,
        location: DraftLocation,
    ): AttachmentRecovery = lane.withLock {
        val draft = draft(profileId, sessionId) ?: return@withLock AttachmentRecovery.Empty
        vault.restore(
            draft.profileId,
            draft.attachments,
            sameLocation =
                draft.directory == location.directory && draft.workspace == location.workspace,
        )
    }

    /**
     * The §10.8 restore rules (port of `chat_screen.dart:476-503`). [unconfirmedTexts] are the
     * words of this conversation's dispatched, not-confirmed sends (`:engine:delivery`): a draft
     * equal to one of them (trimmed) is not put back, because that would leave a duplicate send one
     * tap away.
     */
    public fun restoreFor(
        profileId: String,
        sessionId: String,
        unconfirmedTexts: Collection<String>,
        opening: DraftOpening = DraftOpening(),
    ): DraftRestore {
        if (opening.isolated) {
            return DraftRestore(
                opening.initialText.ifEmpty { null },
                emptyList(),
                recoverSavedAttachments = false,
                tracksDraft = false,
            )
        }
        if (opening.initialText.isNotEmpty() || opening.initialAttachments.isNotEmpty()) {
            return DraftRestore(
                opening.initialText.ifEmpty { null },
                opening.initialAttachments,
                recoverSavedAttachments = false,
                tracksDraft = true,
            )
        }
        val saved = draft(profileId, sessionId)
        val waits = saved != null && unconfirmedTexts.any { it.trim() == saved.text.trim() }
        return DraftRestore(
            text = saved?.text?.takeUnless { waits || it.isEmpty() },
            attachments = opening.initialAttachments,
            recoverSavedAttachments =
                opening.initialAttachments.isEmpty() && saved?.attachments?.isNotEmpty() == true,
            tracksDraft = true,
        )
    }

    /**
     * Profile deletion (B-STATE-100, 101): refuses every later write for [profileId] at once, then
     * (after writes already in the lane) removes its drafts and collects only its payloads.
     * Unattributed older drafts may be removed only with one profile; multiple possible owners
     * refuse cleanup without changing the index. False when anything is left to retry.
     */
    public suspend fun closeProfile(profileId: String): Boolean {
        while (true) {
            val before = closing.load()
            if (closing.compareAndSet(before, before + profileId)) break
        }
        return lane.withLock {
            val current = load()
            if (!current.readable) return@withLock false
            if (profiles.count() > 1 && current.drafts.values.any { it.profileId.isEmpty() })
                return@withLock false
            val kept =
                current.drafts.filterValues {
                    it.profileId.isNotEmpty() && it.profileId != profileId
                }
            if (kept.size != current.drafts.size && !writeIndex(kept)) return@withLock false
            val hadLegacy = current.drafts.values.any { it.profileId.isEmpty() }
            collectLocked(profileId) && (!hadLegacy || collectLocked(""))
        }
    }

    /** A server added again under a removed id may keep drafts again. */
    public fun reopenProfile(profileId: String) {
        while (true) {
            val before = closing.load()
            if (closing.compareAndSet(before, before - profileId)) return
        }
    }

    /**
     * Runs [block] in the drafts lane (photo recovery and the older-drafts move write the index
     * directly).
     */
    internal suspend fun <T> inLane(block: suspend () -> T): T = lane.withLock { block() }

    internal fun isClosing(profileId: String): Boolean = profileId in closing.load()

    internal fun hasProfile(profileId: String): Boolean =
        profileId.isNotEmpty() && profiles.exists(profileId)

    internal fun profileAvailable(profileId: String): Boolean =
        profileId !in closing.load() && (profileId.isEmpty() || profiles.exists(profileId))

    internal suspend fun writeIndexLocked(drafts: Map<String, SessionDraft>): Boolean =
        writeIndex(drafts)

    internal suspend fun collectInLane(owner: String? = null): Boolean = collectLocked(owner)
}

/** How strictly one save treats its text and files. */
private enum class Exactness {
    /** [Drafts.save]: blank text forgets, null files keep the saved ones. */
    Plain,

    /** The text-only preview: exact whitespace, refuses to run over saved files. */
    TextOnly,

    /** The draft session's own write: exact text plus the authoritative file snapshot. */
    WholeState,
}

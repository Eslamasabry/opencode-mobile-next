package io.github.eslamasabry.oc.engine.composer.stash

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.composer.store.ComposerPrefs
import io.github.eslamasabry.oc.engine.composer.store.ComposerProfiles
import io.github.eslamasabry.oc.engine.composer.store.StoredFormatException
import io.github.eslamasabry.oc.engine.composer.store.asObject
import io.github.eslamasabry.oc.engine.composer.store.commit
import io.github.eslamasabry.oc.engine.composer.store.parseJson
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentRecovery
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentRef
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentVault
import io.github.eslamasabry.oc.engine.composer.vault.schemeOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.concurrent.atomics.AtomicReference

/**
 * Saved prompts and prompt history (plan 09 §10.8; port of `lib/state/prompt_shelf.dart` and
 * `saved_prompts_controller.dart`). One key per saved prompt, `oc.promptStash.<profile>.<id>`; history
 * `oc.promptHistory.<profile>`; payloads in the `prompt-stash-attachments-v1` vault, published only after the owner
 * marker `oc.stashAttachmentVault.<profile>` is committed (a crash leaves discoverable orphans). One lane for every
 * profile, because the disk budget spans profiles. An unreadable shelf is refused, never silently dropped.
 */
public class PromptShelf(
    private val prefs: ComposerPrefs,
    private val vault: AttachmentVault,
    private val profiles: ComposerProfiles,
    private val redactor: Redactor,
) {
    public companion object {
        public const val CAPACITY: Int = 50
        public const val MAX_ATTACHMENTS: Int = 5

        public fun stashKey(profile: String, id: String): String = "oc.promptStash.$profile.$id"

        public fun historyKey(profile: String): String = "oc.promptHistory.$profile"

        public fun ownerKey(profile: String): String = "oc.stashAttachmentVault.$profile"

        private val BLOB = Regex("^[0-9a-f]{64}$")
    }

    private val lane = Mutex()
    private val closing = AtomicReference<Set<String>>(emptySet())

    private fun readStashes(profile: String): List<StashedPrompt> {
        val prefix = "oc.promptStash.$profile."
        val entries = mutableListOf<StashedPrompt>()
        for (key in prefs.keys().filter { it.startsWith(prefix) }) {
            val raw = prefs.string(key) ?: throw StoredFormatException("Stash entry is not text")
            val prompt = stashedPromptFromJson(parseJson(raw).asObject())
            if (prompt.id.isEmpty() || key != "$prefix${prompt.id}") throw StoredFormatException("Stash identity changed")
            entries += prompt
        }
        return entries.sortedByDescending { it.createdAt }
    }

    /** Newest first. Failed with `Storage(Unreadable)` for a corrupt shelf (never shown as empty). */
    public fun stashes(profile: String): Outcome<List<StashedPrompt>> =
        try {
            readStashes(profile).ok()
        } catch (e: StoredFormatException) {
            Failure.StorageRefused().failed()
        }

    /** Sent prompts, newest first ("Reuse a prompt"; desktop arrow keys). A corrupt list reads as empty. */
    public fun history(profile: String): List<String> {
        val raw = prefs.string(historyKey(profile)) ?: return emptyList()
        return try {
            (parseJson(raw) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }.orEmpty()
        } catch (e: StoredFormatException) {
            emptyList()
        }
    }

    private fun current(profile: String): Failure? =
        when {
            profile.isEmpty() -> Failure.Refused(RefusalReason.StaleScope)
            profile in closing.load() || !profiles.exists(profile) -> Failure.Refused(RefusalReason.ProfileDeleted)
            else -> null
        }

    private suspend fun ensureOwner(profile: String): Boolean =
        prefs.bool(ownerKey(profile)) == true || prefs.commit { putBool(ownerKey(profile), true) }

    /**
     * Deletes this profile's unreferenced stash payloads. Suppressed (false, nothing deleted) when the shelf cannot be
     * read, a blob reference is corrupt, or a reference is neither blob nor URL (B-STATE-136).
     */
    private suspend fun collect(profile: String): Boolean {
        val refs =
            try {
                readStashes(profile).flatMap { it.attachmentRefs }
            } catch (e: StoredFormatException) {
                return false
            }
        for (ref in refs) {
            val blob = ref.blob
            if (blob != null && (ref.url != null || !BLOB.matches(blob))) return false
            if (blob == null && ref.url == null) return false
        }
        if (refs.any { it.blob != null } && !ensureOwner(profile)) return false
        // Shelves that never published files have nothing to collect.
        if (!prefs.contains(ownerKey(profile))) return true
        return vault.collect(mapOf(profile to refs), owner = profile)
    }

    /** Moves data-URL attachments into the vault; file and web URLs stay as references. */
    private suspend fun externalize(profile: String, prompt: StashedPrompt): Outcome<StashedPrompt> {
        if (prompt.attachments.isEmpty()) return prompt.ok()
        if (prompt.attachments.size > MAX_ATTACHMENTS) return Failure.StorageRefused().failed()
        val data = prompt.attachments.filter { schemeOf(it.url) == "data" }
        var stored: List<AttachmentRef> = emptyList()
        if (data.isNotEmpty()) {
            if (!ensureOwner(profile)) return Failure.StorageRefused().failed()
            when (val r = vault.store(profile, data)) {
                is Outcome.Ok -> stored = r.value
                is Outcome.Failed -> return r
            }
        }
        var index = 0
        val refs =
            prompt.attachments.map {
                if (schemeOf(it.url) == "data") stored[index++] else AttachmentRef(filename = it.filename, mime = it.mime, url = it.url)
            }
        return prompt.copy(attachments = emptyList(), attachmentRefs = refs).ok()
    }

    /**
     * Saves [prompt] under its id. Refused when the profile is gone, the id repeats, the shelf holds 50 (a saved
     * prompt is never evicted), or the identity or location holds something secret-like.
     */
    public suspend fun stash(profile: String, prompt: StashedPrompt): Outcome<Unit> {
        val snapshot = prompt.copy(attachments = prompt.attachments.toList(), references = prompt.references.toList())
        return lane.withLock {
            current(profile)?.let { return@withLock it.failed() }
            if (snapshot.id.isEmpty() || listOfNotNull(snapshot.id, snapshot.directory, snapshot.workspace).any(redactor::containsSecret)) {
                return@withLock Failure.StorageRefused().failed()
            }
            if (snapshot.attachmentRefs.isNotEmpty()) return@withLock Failure.StorageRefused().failed()
            val existing =
                try {
                    readStashes(profile)
                } catch (e: StoredFormatException) {
                    return@withLock Failure.StorageRefused().failed()
                }
            if (existing.any { it.id == snapshot.id }) return@withLock Failure.StorageRefused().failed()
            if (existing.size >= CAPACITY) return@withLock Failure.StorageRefused().failed()
            if (!collect(profile)) return@withLock Failure.StorageRefused().failed()
            try {
                val saved =
                    when (val r = externalize(profile, snapshot)) {
                        is Outcome.Ok -> r.value
                        is Outcome.Failed -> return@withLock r
                    }
                if (current(profile) != null) return@withLock Failure.Refused(RefusalReason.ProfileDeleted).failed()
                if (!prefs.commit { putString(stashKey(profile, saved.id), saved.toJson().toString()) }) {
                    return@withLock Failure.StorageRefused().failed()
                }
                Unit.ok()
            } finally {
                collect(profile)
            }
        }
    }

    /** Removes one saved prompt and then its unreferenced payloads. */
    public suspend fun remove(profile: String, id: String): Outcome<Unit> =
        lane.withLock {
            current(profile)?.let { return@withLock it.failed() }
            if (!adoptExisting(profile)) return@withLock Failure.StorageRefused().failed()
            if (!prefs.commit { remove(stashKey(profile, id)) }) return@withLock Failure.StorageRefused().failed()
            if (!collect(profile)) Failure.StorageRefused().failed() else Unit.ok()
        }

    /** Records an owner marker for older file-backed records before their metadata goes (or when unreadable). */
    private suspend fun adoptExisting(profile: String): Boolean {
        if (prefs.contains(ownerKey(profile))) return ensureOwner(profile)
        val mayHaveFiles =
            try {
                readStashes(profile).any { p -> p.attachmentRefs.any { it.blob != null } }
            } catch (e: StoredFormatException) {
                true
            }
        return !mayHaveFiles || ensureOwner(profile)
    }

    /** Reads the files back without consuming the saved prompt; the caller rechecks place and conversation. */
    public suspend fun restoreAttachments(profile: String, id: String, sameLocation: Boolean): Outcome<AttachmentRecovery> =
        lane.withLock {
            current(profile)?.let { return@withLock it.failed() }
            val prompt =
                when (val all = stashes(profile)) {
                    is Outcome.Ok -> all.value.firstOrNull { it.id == id } ?: return@withLock Failure.StorageRefused().failed()
                    is Outcome.Failed -> return@withLock all
                }
            if (prompt.attachmentRefs.isNotEmpty()) return@withLock vault.restore(profile, prompt.attachmentRefs, sameLocation).ok()
            val (available, unavailable) =
                prompt.attachments.partition {
                    schemeOf(it.url) in setOf("data", "file", "http", "https") && (sameLocation || schemeOf(it.url) != "file")
                }
            AttachmentRecovery(available, unavailable.map { it.filename }).ok()
        }

    /**
     * Deletes a saved prompt at once and returns its Undo, which puts back the exact text, metadata and bytes
     * (B-STATE-085). Undo is single use and refused once the profile is gone (B-STATE-086).
     */
    public suspend fun deleteWithUndo(profile: String, id: String): Outcome<SavedPromptUndo> {
        val prompt =
            when (val all = stashes(profile)) {
                is Outcome.Ok -> all.value.firstOrNull { it.id == id } ?: return Failure.StorageRefused().failed()
                is Outcome.Failed -> return all
            }
        val files =
            when (val r = restoreAttachments(profile, id, sameLocation = true)) {
                is Outcome.Ok -> r.value
                is Outcome.Failed -> return r
            }
        val original = prompt.copy(attachments = prompt.attachments.ifEmpty { files.attachments }, attachmentRefs = emptyList())
        return when (val removed = remove(profile, id)) {
            is Outcome.Failed -> removed
            is Outcome.Ok -> SavedPromptUndo { stash(profile, original) }.ok()
        }
    }

    /** Remembers a sent prompt (newest first, deduplicated, 50). A late send after deletion writes nothing. */
    public suspend fun recordSent(profile: String, text: String): Outcome<Unit> =
        lane.withLock {
            current(profile)?.let { return@withLock it.failed() }
            if (text.isBlank()) return@withLock Unit.ok()
            val next = (listOf(text) + history(profile).filter { it != text }).take(CAPACITY)
            if (prefs.commit { putString(historyKey(profile), JsonArray(next.map(::JsonPrimitive)).toString()) }) {
                Unit.ok()
            } else {
                Failure.StorageRefused().failed()
            }
        }

    /**
     * Profile deletion: refuses later writes at once, then removes every saved prompt, the history, the payloads and
     * finally the owner marker. False leaves something to retry (a retry also removes files orphaned by a failed run).
     */
    public suspend fun clearForProfile(profile: String): Boolean {
        while (true) {
            val before = closing.load()
            if (closing.compareAndSet(before, before + profile)) break
        }
        return lane.withLock {
            if (!adoptExisting(profile)) return@withLock false
            val prefix = "oc.promptStash.$profile."
            val keys = prefs.keys().filter { it.startsWith(prefix) || it == historyKey(profile) }
            if (keys.isNotEmpty() && !prefs.commit { keys.forEach { remove(it) } }) return@withLock false
            if (!collect(profile)) return@withLock false
            !prefs.contains(ownerKey(profile)) || prefs.commit { remove(ownerKey(profile)) }
        }
    }

    /** A server added again under a removed id may save prompts again. */
    public fun reopenProfile(profile: String) {
        while (true) {
            val before = closing.load()
            if (closing.compareAndSet(before, before - profile)) return
        }
    }
}

/** A one-use Undo (Flutter `SavedPromptUndo`): a second use, or one after the profile is gone, is refused. */
public class SavedPromptUndo internal constructor(private val action: suspend () -> Outcome<Unit>) {
    private val lock = Mutex()
    private var used = false

    public suspend fun undo(): Outcome<Unit> =
        lock.withLock {
            if (used) return@withLock Failure.Refused(RefusalReason.AlreadyAnswered).failed()
            val result = action()
            if (result is Outcome.Ok) used = true
            result
        }
}

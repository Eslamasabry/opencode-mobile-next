package io.github.eslamasabry.oc.engine.composer.stash

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.composer.drafts.Drafts
import io.github.eslamasabry.oc.engine.composer.drafts.SessionDraft
import io.github.eslamasabry.oc.engine.composer.drafts.sessionDraftFromJson
import io.github.eslamasabry.oc.engine.composer.drafts.toJson
import io.github.eslamasabry.oc.engine.composer.store.ComposerPrefs
import io.github.eslamasabry.oc.engine.composer.store.ComposerProfiles
import io.github.eslamasabry.oc.engine.composer.store.PrefsEdit
import io.github.eslamasabry.oc.engine.composer.store.StoredFormatException
import io.github.eslamasabry.oc.engine.composer.store.commit
import io.github.eslamasabry.oc.engine.composer.store.parseJson
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentVault
import io.github.eslamasabry.oc.engine.composer.vault.Sha256
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray

public enum class DraftMigrationBlocker {
    UnknownOwner,
    CorruptDrafts,
    ChangedDuringMigration,
    Full,
    Storage,
}

public data class DraftMigrationResult(
    val complete: Boolean,
    val migrated: Int = 0,
    val alreadyComplete: Boolean = false,
    val blocker: DraftMigrationBlocker? = null,
)

/**
 * B-STATE-087: copy, read back and compare before removing an older draft. The native app calls
 * this through Drafts.migrateOlder, in the same lane as ordinary draft saves and deletion. Source
 * changes also fail the atomic comparison at publication. Unattributed payloads and the source
 * index survive every refused copy.
 */
public class SavedPromptMigration(
    private val prefs: ComposerPrefs,
    private val shelf: PromptShelf,
    private val draftVault: AttachmentVault,
    private val profiles: ComposerProfiles,
    private val redactor: Redactor,
) {
    public companion object {
        public fun markerKey(profile: String): String = "oc.savedPromptMigrationV1.$profile"

        public fun backupKey(profile: String): String = "oc.olderDraftsBackupV1.$profile"

        public fun promptId(draft: SessionDraft): String =
            "legacy-draft-" + Sha256.hex(draft.toJson().toString())
    }

    private val lane = Mutex()

    public suspend fun runForProfile(
        profile: String,
        current: () -> Boolean = { true },
    ): DraftMigrationResult = lane.withLock {
        try {
            migrate(profile, current)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            blocked(DraftMigrationBlocker.Storage)
        }
    }

    private fun blocked(reason: DraftMigrationBlocker): DraftMigrationResult =
        DraftMigrationResult(complete = false, blocker = reason)

    private suspend fun migrate(profile: String, current: () -> Boolean): DraftMigrationResult {
        fun admitted(): Boolean =
            profile.isNotEmpty() &&
                !redactor.containsSecret(profile) &&
                current() &&
                profiles.exists(profile)
        if (!admitted()) return blocked(DraftMigrationBlocker.UnknownOwner)
        if (prefs.bool(markerKey(profile)) == true)
            return DraftMigrationResult(complete = true, alreadyComplete = true)
        val raw = prefs.string(Drafts.KEY)
        val rows =
            try {
                if (raw == null) emptyList()
                else
                    (parseJson(raw) as? JsonArray
                            ?: throw StoredFormatException("Invalid draft index"))
                        .map {
                            sessionDraftFromJson(it)
                                ?: throw StoredFormatException("Invalid draft entry")
                        }
                        .also { list ->
                            if (list.map { it.storageKey }.toSet().size != list.size)
                                throw StoredFormatException("Duplicate draft")
                        }
            } catch (_: StoredFormatException) {
                return blocked(DraftMigrationBlocker.CorruptDrafts)
            }
        val older = rows.filter { it.profileId.isEmpty() }
        val existing =
            when (val saved = shelf.stashes(profile)) {
                is Outcome.Ok -> saved.value
                is Outcome.Failed -> return blocked(DraftMigrationBlocker.Storage)
            }
        val savedIds = existing.map { it.id }.toMutableSet()
        var savedCount = existing.size
        for (draft in older) {
            if (!admitted()) return blocked(DraftMigrationBlocker.UnknownOwner)
            val files = draftVault.restore("", draft.attachments, sameLocation = true)
            val prompt =
                try {
                    SavedPromptSafety(redactor)
                        .sanitize(
                            profile,
                            StashedPrompt(
                                id = promptId(draft),
                                text = draft.text,
                                createdAt = draft.updatedAt,
                                directory = draft.directory,
                                workspace = draft.workspace,
                                attachments = files.attachments,
                            ),
                        )
                } catch (_: StoredFormatException) {
                    return blocked(DraftMigrationBlocker.Storage)
                }
            // A draft consisting entirely of missing files still goes into the redacted backup.
            if (prompt.isEmpty) continue
            if (prompt.id !in savedIds) {
                if (savedCount >= PromptShelf.CAPACITY) return blocked(DraftMigrationBlocker.Full)
                if (shelf.stash(profile, prompt) is Outcome.Failed)
                    return blocked(DraftMigrationBlocker.Storage)
                savedIds += prompt.id
                savedCount += 1
            }
            val saved =
                when (val all = shelf.stashes(profile)) {
                    is Outcome.Failed -> return blocked(DraftMigrationBlocker.Storage)
                    is Outcome.Ok ->
                        all.value.firstOrNull { it.id == prompt.id }
                            ?: return blocked(DraftMigrationBlocker.Storage)
                }
            val copy =
                when (
                    val recovered =
                        shelf.restoreAttachments(profile, prompt.id, sameLocation = true)
                ) {
                    is Outcome.Failed -> return blocked(DraftMigrationBlocker.Storage)
                    is Outcome.Ok -> recovered.value
                }
            if (
                copy.unavailable.isNotEmpty() ||
                    saved.copy(attachments = copy.attachments, attachmentRefs = emptyList()) !=
                        prompt
            ) {
                return blocked(DraftMigrationBlocker.Storage)
            }
        }
        if (!admitted()) return blocked(DraftMigrationBlocker.UnknownOwner)
        if (prefs.string(Drafts.KEY) != raw)
            return blocked(DraftMigrationBlocker.ChangedDuringMigration)
        if (older.isNotEmpty()) {
            val oldBackup = prefs.string(backupKey(profile))
            val backup =
                try {
                    oldBackup
                        ?.let {
                            parseJson(it) as? JsonArray
                                ?: throw StoredFormatException("Invalid backup")
                        }
                        .orEmpty()
                } catch (_: StoredFormatException) {
                    return blocked(DraftMigrationBlocker.Storage)
                }
            val safeRows = older.map { draft ->
                draft
                    .copy(
                        text = redactor.redact(draft.text),
                        attachments =
                            draft.attachments.map {
                                it.copy(
                                    filename = redactor.redact(it.filename),
                                    mime = redactor.redact(it.mime),
                                    url = it.url?.let(redactor::redact),
                                )
                            },
                        directory = draft.directory?.let(redactor::redact),
                        workspace = draft.workspace?.let(redactor::redact),
                    )
                    .toJson()
            }
            val retained = rows.filter { it.profileId.isNotEmpty() }
            val edit =
                PrefsEdit().apply {
                    putString(
                        backupKey(profile),
                        JsonArray((backup + safeRows).distinct()).toString(),
                    )
                    if (retained.isEmpty()) remove(Drafts.KEY)
                    else putString(Drafts.KEY, JsonArray(retained.map { it.toJson() }).toString())
                }
            if (
                !prefs.compareAndCommit(
                    mapOf(Drafts.KEY to raw, backupKey(profile) to oldBackup),
                    edit,
                )
            ) {
                return blocked(DraftMigrationBlocker.Storage)
            }
            if (
                older.any { it.attachments.any { ref -> ref.blob != null } } &&
                    !draftVault.collect(emptyMap(), owner = "")
            ) {
                return blocked(DraftMigrationBlocker.Storage)
            }
        }
        if (!admitted()) return blocked(DraftMigrationBlocker.UnknownOwner)
        return if (prefs.commit { putBool(markerKey(profile), true) })
            DraftMigrationResult(complete = true, migrated = older.size)
        else blocked(DraftMigrationBlocker.Storage)
    }
}

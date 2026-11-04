package io.github.eslamasabry.oc.engine.composer.stash

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.composer.drafts.Drafts
import io.github.eslamasabry.oc.engine.composer.drafts.SessionDraft
import io.github.eslamasabry.oc.engine.composer.drafts.sessionDraftFromJson
import io.github.eslamasabry.oc.engine.composer.drafts.toJson
import io.github.eslamasabry.oc.engine.composer.store.ComposerPrefs
import io.github.eslamasabry.oc.engine.composer.store.ComposerProfiles
import io.github.eslamasabry.oc.engine.composer.store.StoredFormatException
import io.github.eslamasabry.oc.engine.composer.store.commit
import io.github.eslamasabry.oc.engine.composer.store.parseJson
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentVault
import io.github.eslamasabry.oc.engine.composer.vault.Sha256
import kotlinx.serialization.json.JsonArray

/** Why an older-drafts move did not run (Flutter `DraftMigrationBlocker`). */
public enum class DraftMigrationBlocker { UnknownOwner, CorruptDrafts, Full, Storage }

/** What one run did (Flutter `DraftMigrationResult`). */
public data class DraftMigrationResult(
    val complete: Boolean,
    val migrated: Int = 0,
    val alreadyComplete: Boolean = false,
    val blocker: DraftMigrationBlocker? = null,
)

/**
 * Moves "older drafts" (written before drafts named their server, `profileID` empty) into Saved prompts once per
 * server (port of Flutter `MigrationRunner.runForProfile`, `lib/state/migration_runner.dart`; B-STATE-087).
 *
 * Order: the redacted rows are saved as prompts with ids derived from the draft (a retry never duplicates), a
 * redacted backup of the moved rows is kept under `oc.savedPromptMigrationBackupV1.<profile>`, the older rows leave
 * `oc.sessionDrafts` (live drafts naming a server stay), then the marker `oc.savedPromptMigrationV1.<profile>` is
 * written and only then are the older drafts' files collected. A refused step leaves the source intact and no marker,
 * so the next run retries. A corrupt index or a full shelf moves nothing. A missing attachment does not hold back text.
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

        public fun backupKey(profile: String): String = "oc.savedPromptMigrationBackupV1.$profile"

        /** A stable saved-prompt id for one older draft (a retried run finds it and does not save it twice). */
        public fun promptId(draft: SessionDraft): String =
            "draft-" + Sha256.hex("${draft.sessionId}\u0000${draft.updatedAt}").take(24)
    }

    public suspend fun runForProfile(profile: String): DraftMigrationResult {
        if (prefs.bool(markerKey(profile)) == true) return DraftMigrationResult(complete = true, alreadyComplete = true)
        if (profile.isEmpty() || !profiles.exists(profile)) {
            return DraftMigrationResult(complete = false, blocker = DraftMigrationBlocker.UnknownOwner)
        }
        val raw = prefs.string(Drafts.KEY)
        val rows: List<SessionDraft> =
            if (raw.isNullOrEmpty()) {
                emptyList()
            } else {
                try {
                    val list = parseJson(raw) as? JsonArray ?: throw StoredFormatException("Invalid draft index")
                    list.map { sessionDraftFromJson(it) ?: throw StoredFormatException("Invalid draft entry") }
                } catch (e: StoredFormatException) {
                    return DraftMigrationResult(complete = false, blocker = DraftMigrationBlocker.CorruptDrafts)
                }
            }
        val older = rows.filter { it.profileId.isEmpty() }
        if (older.isEmpty()) {
            return if (prefs.commit { putBool(markerKey(profile), true) }) {
                DraftMigrationResult(complete = true)
            } else {
                DraftMigrationResult(complete = false, blocker = DraftMigrationBlocker.Storage)
            }
        }
        val existing =
            when (val saved = shelf.stashes(profile)) {
                is Outcome.Ok -> saved.value
                is Outcome.Failed -> return DraftMigrationResult(complete = false, blocker = DraftMigrationBlocker.Storage)
            }
        val ids = existing.map { it.id }.toSet()
        val toSave = older.filter { promptId(it) !in ids }
        if (existing.size + toSave.size > PromptShelf.CAPACITY) {
            return DraftMigrationResult(complete = false, blocker = DraftMigrationBlocker.Full)
        }
        for (draft in toSave) {
            val files = draftVault.restore("", draft.attachments, sameLocation = true)
            val prompt =
                StashedPrompt(
                    id = promptId(draft),
                    text = draft.text,
                    createdAt = draft.updatedAt,
                    directory = draft.directory,
                    workspace = draft.workspace,
                    attachments = files.attachments,
                )
            if (shelf.stash(profile, prompt) is Outcome.Failed) {
                return DraftMigrationResult(complete = false, blocker = DraftMigrationBlocker.Storage)
            }
        }
        val backup = JsonArray(older.map { it.redacted().toJson() }).toString()
        val live = rows.filter { it.profileId.isNotEmpty() }
        val landed =
            prefs.commit {
                putString(backupKey(profile), backup)
                if (live.isEmpty()) remove(Drafts.KEY) else putString(Drafts.KEY, JsonArray(live.map { it.toJson() }).toString())
            }
        if (!landed) return DraftMigrationResult(complete = false, blocker = DraftMigrationBlocker.Storage)
        if (!prefs.commit { putBool(markerKey(profile), true) }) {
            return DraftMigrationResult(complete = false, blocker = DraftMigrationBlocker.Storage)
        }
        // The older drafts' own files are no longer referenced by any draft (live drafts name a server).
        draftVault.collect(emptyMap(), owner = "")
        return DraftMigrationResult(complete = true, migrated = older.size)
    }

    private fun SessionDraft.redacted(): SessionDraft =
        copy(
            text = redactor.redact(text),
            attachments = attachments.map { it.copy(filename = redactor.redact(it.filename)) },
        )
}

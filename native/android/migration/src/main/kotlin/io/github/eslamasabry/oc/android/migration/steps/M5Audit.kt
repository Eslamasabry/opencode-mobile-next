package io.github.eslamasabry.oc.android.migration.steps

import io.github.eslamasabry.oc.android.migration.MigrationPaths
import io.github.eslamasabry.oc.android.migration.readers.json.MiniJson
import io.github.eslamasabry.oc.android.migration.readers.migration.Fs
import io.github.eslamasabry.oc.android.migration.readers.xml.RawPref
import java.io.File

/**
 * M5, the count-and-hash audit (plan 05 §7.4 M5). `files/native/migration-audit.json` holds counts and digests, never
 * values or full key names, and no hash of a secret (a hash of a short password can be guessed).
 *
 * The "nothing else changed" proof: `unchangedKeysDigest` over every key outside the declared write sets, from the
 * M1 baseline and from the live map, must be equal; otherwise `AuthorityChanged` with step M5. This also proves the
 * offline queue and its dispatch markers are byte-identical (state-data risk 6).
 */
internal class M5Audit(private val paths: MigrationPaths) {
    sealed interface Result {
        data class Done(val audit: Map<String, Any?>, val diagnosticsLine: String) : Result
        data object AuthorityChanged : Result
    }

    fun run(
        live: Map<String, RawPref>,
        baseline: PrefsBaseline?,
        m1: Map<String, Any?>,
        m2: Map<String, Any?>,
        m3: Map<String, Any?>,
        m4: Map<String, Any?>,
    ): Result {
        baseline ?: return Result.AuthorityChanged
        val before = baseline.unchangedKeysDigest()
        val now = PrefsBaseline.liveDigest(live)
        if (before != now) return Result.AuthorityChanged
        val p = LogicalPrefs(live)

        val queue = MiniJson.parseOrNull(p.string("oc.offlineQueue") ?: "[]") as? List<*>
        val drafts = MiniJson.parseOrNull(p.string("oc.sessionDrafts") ?: "{}")
        val draftEntries: Collection<*>? = when (drafts) {
            is Map<*, *> -> drafts.values
            is List<*> -> drafts
            else -> null
        }
        val changedDeclared = PrefsBaseline.entriesOf(live).let { nowEntries ->
            (nowEntries.keys + baseline.entries.keys).count { k ->
                val e = nowEntries[k] ?: baseline.entries[k]
                e?.declared == true && nowEntries[k] != baseline.entries[k]
            }
        }

        val stored = (m2["copied"] as? Map<*, *>)?.values?.sumOf { (it as? Long) ?: 0L } ?: 0L
        val flagged = (m2["flagged"] as? Map<*, *>)?.values?.sumOf { (it as? Long) ?: 0L } ?: 0L
        val audit = linkedMapOf<String, Any?>(
            "v" to 1L,
            "prefs" to linkedMapOf(
                "bytes" to m1["prefsBytes"],
                "keys" to live.size.toLong(),
                "kinds" to m1["kinds"],
                "undecodable" to m1["undecodable"],
                "unchangedKeysDigest" to now,
                "declaredKeysChanged" to changedDeclared.toLong(),
            ),
            "profiles" to linkedMapOf(
                "count" to m1["profiles"], "invalidIds" to m1["invalidIds"], "reservedCollisions" to m1["reservedCollisions"],
                "oc1MigrationRequired" to m1["oc1MigrationRequired"], "oc2HistoryPending" to m1["oc2HistoryPending"],
            ),
            "secrets" to m2,
            "transforms" to m3,
            "data" to linkedMapOf(
                "queueEntries" to queue?.size?.toLong(),
                "queueDispatched" to queue?.count { (it as? Map<*, *>)?.get("dispatchedAt") != null }?.toLong(),
                "keptQueued" to (MiniJson.parseOrNull(p.string("oc.keptQueuedPrompts") ?: "[]") as? List<*>)?.size?.toLong(),
                "drafts" to draftEntries?.size?.toLong(),
                "draftsUnattributed" to draftEntries?.count { (it as? Map<*, *>)?.get("profileID") == "" }?.toLong(),
                "stashPrompts" to p.keys("oc.promptStash.").size.toLong(),
                "pins" to p.keys("oc.sessionPins.").size.toLong(),
                "schedules" to p.keys("oc.scheduledPrompts.").size.toLong(),
                "widgetTargets" to p.keys("oc.widgetTarget.").size.toLong(),
            ),
            "restoreAuthority" to m4,
            "files" to linkedMapOf(
                "draftVaultFiles" to fileCount(File(paths.files, "draft-attachments-v1")),
                "stashVaultFiles" to fileCount(File(paths.files, "prompt-stash-attachments-v1")),
                "photoVaultFiles" to fileCount(File(paths.files, "prompt-photo-recovery-v1")),
                "voiceModelBytes" to bytes(File(paths.files, "voice_models")),
                "ubuntuReady" to File(paths.files, "linux/ubuntu.ready").exists(),
            ),
            "deferred" to m1["deferred"],
        )
        Fs.writeAtomically(paths.audit, MiniJson.encode(audit).toByteArray(Charsets.UTF_8))
        val servers = (m1["profiles"] as? Long) ?: 0L
        val line = "Update moved your data: $servers servers, $stored saved sign-ins ($flagged need the password again)."
        return Result.Done(audit, line)
    }

    private fun fileCount(dir: File): Long = if (dir.isDirectory) dir.walkTopDown().count { it.isFile }.toLong() else 0L

    private fun bytes(dir: File): Long = if (dir.isDirectory) dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
}

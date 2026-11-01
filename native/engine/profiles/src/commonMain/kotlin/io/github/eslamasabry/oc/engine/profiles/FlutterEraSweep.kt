package io.github.eslamasabry.oc.engine.profiles

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.kv.PrefValue

/** Which structural rules a retained copy gets. */
public enum class RetainedCopyKind {
    /** A copy of `FlutterSharedPreferences.xml` (or a `v2-sources/` file of the same keys): logical keys. */
    Prefs,

    /** A copy of (or the live) Flutter secure-storage XML: entry names with the FSS prefix already removed. */
    SecureStorage,
}

/**
 * One retained Flutter-era copy (plan 16 §1.4): the S0 backup, a `replaced-*` or `start-fresh-*` copy, EACH `.bak`
 * twin separately, a `v2-sources/` file, and the live FSS XML. The Android implementation (`:android:migration`)
 * parses and rewrites the file (`.tmp`, fsync, rename) and refreshes the copy's manifest hash; values stay opaque.
 */
public interface RetainedCopy {
    /** A stable label for logs and the receipt (a relative path, never content). */
    public val label: String
    public val kind: RetainedCopyKind

    /** The copy's entries; Ok(null) when the copy does not exist (already cleaned up by v3). */
    public suspend fun read(): Outcome<Map<String, PrefValue>?>

    /** Replaces the copy's entries with [entries] atomically. */
    public suspend fun rewrite(entries: Map<String, PrefValue>): Outcome<Unit>
}

/** Lists the retained copies that exist now (computed at deletion time, never cached). */
public fun interface RetainedCopies {
    public suspend fun list(): Outcome<List<RetainedCopy>>
}

/**
 * The `FlutterEraSweep` cleaner (plan 16 §1.4, Astra M1, CR16-14): applies the SAME [ProfileStructuralEdit] the live
 * data gets to every retained copy and its `.bak` twin. Runs after the generic sweep and before the row
 * ([DeletionStep.RETAINED_COPIES]), so a copy that refuses keeps the server and the deletion can be retried.
 * Every copy is tried; any refusal fails the step. Returns the number of copies rewritten.
 */
public class FlutterEraSweep(
    private val copies: RetainedCopies,
    private val edit: ProfileStructuralEdit = ProfileStructuralEdit(),
    override val order: Int = DeletionStep.RETAINED_COPIES,
) : ProfileResourceCleaner {
    override val owner: String = OWNER

    override suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int> {
        val all = when (val listed = copies.list()) {
            is Outcome.Failed -> return listed
            is Outcome.Ok -> listed.value
        }
        var rewritten = 0
        var firstFailure: Outcome.Failed? = null
        for (copy in all) {
            val entries = when (val read = copy.read()) {
                is Outcome.Failed -> {
                    firstFailure = firstFailure ?: read
                    continue
                }
                is Outcome.Ok -> read.value ?: continue
            }
            val next = LinkedHashMap(entries)
            val receipt = when (copy.kind) {
                RetainedCopyKind.Prefs -> edit.applyPrefs(next, profileId, options.structuralPlan)
                RetainedCopyKind.SecureStorage -> edit.applySecrets(next, profileId)
            }
            if (!receipt.changed) continue
            when (val written = copy.rewrite(next)) {
                is Outcome.Failed -> firstFailure = firstFailure ?: written
                is Outcome.Ok -> rewritten++
            }
        }
        return firstFailure ?: rewritten.ok()
    }

    public companion object {
        public const val OWNER: String = ":android:migration FlutterEraSweep"
    }
}

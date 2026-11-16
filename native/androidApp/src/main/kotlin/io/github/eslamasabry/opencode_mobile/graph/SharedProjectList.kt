package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefEdit
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape

/**
 * The folders of the phone's shared storage a profile was granted (`oc.sharedProjects.<profileId>`): the list the
 * phone's Linux binds from ([NativeSharedProjectFolders]).
 *
 * W3: this is the one place that writes it. [add] and [remove] compare and set, so two writers at the same moment (New
 * conversation choosing a folder, the Files page granting one, a project opened from storage) each keep the other's
 * folder; before, a plain read-then-write by one could drop the folder the other had just added, and the agents' next
 * start then bound the list without it. Everything else reads ([read], [all]) or registers the key ([shape]); the
 * profile deletion sweep removes the key by its registered shape. `SharedProjectListGuardTest` fails when the key's
 * name is spelled anywhere else.
 */
internal object SharedProjectList {
    private const val PREFIX = "oc.sharedProjects."
    private const val TRIES = 5

    private fun key(profile: ProfileId): String = PREFIX + profile.raw

    /** The registration of the key for the profile scope (its deletion sweep finds the key by it). */
    fun shape(ownerModule: String, why: String): KeyShape =
        KeyShape(PREFIX, KeyScope.Profile, PrefType.StrList, ownerModule, why)

    /** [profile]'s folders; empty when none (or when the value is not a list). */
    fun read(prefs: KeyValueStore, profile: ProfileId): List<String> =
        (prefs.get(key(profile)) as? PrefValue.StrList)?.value.orEmpty()

    /** Whether [profile]'s value is something other than a list (a store the app must not overwrite blindly). */
    fun unreadable(prefs: KeyValueStore, profile: ProfileId): Boolean =
        prefs.get(key(profile)).let { it != null && it !is PrefValue.StrList }

    /** Every saved list, by profile id. */
    fun all(prefs: KeyValueStore): Map<String, List<String>> =
        prefs.keys(PREFIX).associate { key ->
            key.removePrefix(PREFIX) to (prefs.get(key) as? PrefValue.StrList)?.value.orEmpty()
        }

    /**
     * Adds [root] to [profile]'s folders; [also] is written in the same commit (it is written even when [root] was
     * already there). Ok(true) when the list changed.
     */
    suspend fun add(
        prefs: KeyValueStore,
        lane: WriterLane,
        profile: ProfileId,
        root: String,
        also: (PrefEdit.() -> Unit)? = null,
    ): Outcome<Boolean> = change(prefs, lane, profile, also) { (it + root).distinct().sorted() }

    /** Removes [root] from [profile]'s folders (the key goes when none is left). Ok(true) when the list changed. */
    suspend fun remove(prefs: KeyValueStore, lane: WriterLane, profile: ProfileId, root: String): Outcome<Boolean> =
        change(prefs, lane, profile, null) { it - root }

    private suspend fun change(
        prefs: KeyValueStore,
        lane: WriterLane,
        profile: ProfileId,
        also: (PrefEdit.() -> Unit)?,
        next: (List<String>) -> List<String>,
    ): Outcome<Boolean> {
        val key = key(profile)
        repeat(TRIES) {
            val before = prefs.get(key)
            if (before != null && before !is PrefValue.StrList) return Outcome.Failed(Failure.StorageRefused())
            val known = (before as? PrefValue.StrList)?.value.orEmpty()
            val wanted = next(known)
            val changed = wanted != known
            if (!changed && also == null) return Outcome.Ok(false)
            when (
                val saved =
                    prefs.compareAndCommit(lane, mapOf(key to before)) {
                        if (changed) {
                            if (wanted.isEmpty()) remove(key) else putStringList(key, wanted)
                        }
                        also?.invoke(this)
                    }
            ) {
                is Outcome.Failed -> return saved
                is Outcome.Ok -> if (saved.value) return Outcome.Ok(changed)
            }
        }
        return Outcome.Failed(Failure.Conflict())
    }
}

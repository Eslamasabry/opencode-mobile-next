package io.github.eslamasabry.oc.engine.runtime.switching

import io.github.eslamasabry.oc.core.common.Outcome

/*
 * Port of `forgetInAppServerProfiles` (lib/ui/widgets/phone_server_card.dart) and the order of
 * `deleteProfileAndLocalData` (lib/state/connection/deletion.dart) it relies on, after the in-app Linux was removed:
 * every saved in-app server and its local data goes, its queued prompts are kept in Saved prompts, and the profile row
 * and its Keystore password go only once everything that depends on them is confirmed gone.
 */

/** The device-side state of saved profiles (Dart `ProfileStore` and its preferences). */
public interface ForgetStore {
    public fun profiles(): List<LocalProfile>

    public fun activeId(): String?

    /** Every saved preference key (the sweep picks the ones scoped `oc.<what>.<id>`). */
    public fun preferenceKeys(): Set<String>

    /** False when storage refused. */
    public suspend fun removePreference(key: String): Boolean

    /** Removes the row, the active pointer when it names [id], and the Keystore password `pw.<id>`, as one commit. */
    public suspend fun removeProfile(id: String): Outcome<Unit>
}

/** The offline queue as the deletion reads it. */
public interface ForgetQueue {
    /** The ids queued for [profileId]; null when the saved queue is unreadable (unknown is never empty). */
    public fun queuedFor(profileId: String): List<String>?

    /** Moves exactly these prompts into Saved prompts. */
    public suspend fun keepAsSaved(profileId: String, ids: List<String>): Boolean

    /** Rewrites the queue without [profileId]'s entries. */
    public suspend fun removeFor(profileId: String): Boolean
}

/** The phone-server owners the deletion revokes before anything goes. */
public interface ForgetRuntime {
    /** Revokes healing/recovery for [profileId] (Dart `BuiltinServerRecovery.suspendForProfile`). */
    public suspend fun suspendRecovery(profileId: String)

    /** Clears `oc.builtinServerOwner` to empty when it names [profileId], so no other profile inherits the server. */
    public suspend fun clearRuntimeOwner(profileId: String)

    /** The connection's current profile id, if connected. */
    public fun connectedProfileId(): String?

    /** Retires the connection to a forgotten profile. */
    public suspend fun disconnect()
}

/** What could not be cleared for one profile (each maps to the l10n key named on it, with [profileName]). */
public sealed interface ForgetProblem {
    public val profileName: String

    /** `linuxMgrResetLeft(name, left)`; [connectionKept] adds `linuxMgrResetLeftConnection` to the list. */
    public data class Left(override val profileName: String, val connectionKept: Boolean, val failures: List<LeftOver>) : ForgetProblem

    /** `serversRemoveQueuedChanged(name)`. */
    public data class QueuedChanged(override val profileName: String) : ForgetProblem

    /** `serversRemoveQueuedNotKept(name)`. */
    public data class QueuedNotKept(override val profileName: String) : ForgetProblem
}

/** One piece of local data that stayed (the Dart failure strings, as data). */
public sealed interface LeftOver {
    /** "N queued prompt(s)". */
    public data class QueuedPrompts(val count: Int) : LeftOver

    /** "N saved setting(s)". */
    public data class Settings(val count: Int) : LeftOver

    /** "automation settings". */
    public data object AutomationSettings : LeftOver
}

public class InAppServerForgetting(
    private val store: ForgetStore,
    private val queue: ForgetQueue,
    private val runtime: ForgetRuntime,
) {
    /** Forgets every saved in-app server profile; returns what could not be cleared (empty when all went). */
    public suspend fun forgetAll(): List<ForgetProblem> {
        val saved = store.profiles().filter(ManagedLocalServer::looksLikeInAppServer)
        return saved.mapNotNull { forget(it) }
    }

    private suspend fun forget(profile: LocalProfile): ForgetProblem? {
        val id = profile.id
        // Counted at the last moment, so every prompt still queued is kept. Unknown is never empty.
        val queued = queue.queuedFor(id) ?: return ForgetProblem.QueuedNotKept(profile.name)
        if (queued.isNotEmpty() && !queue.keepAsSaved(id, queued)) return ForgetProblem.QueuedNotKept(profile.name)
        // Preservation succeeded: only now revoke the destructive owners and begin cleanup.
        runtime.suspendRecovery(id)
        runtime.clearRuntimeOwner(id)
        val failures = mutableListOf<LeftOver>()
        if (queued.isNotEmpty() && !queue.removeFor(id)) failures += LeftOver.QueuedPrompts(queued.size)
        val retained = retainedKeys(id)
        val scoped = scopedKeys(id)
        val uncleared = scoped.filter { it !in retained && !store.removePreference(it) }
        if (uncleared.isNotEmpty()) failures += LeftOver.Settings(uncleared.size)
        // A server whose data is still on disk keeps its row: never say a deletion happened that did not.
        if (failures.isNotEmpty()) return ForgetProblem.Left(profile.name, connectionKept = true, failures = failures)
        if (runtime.connectedProfileId() == id) runtime.disconnect()
        if (store.removeProfile(id) is Outcome.Failed) return ForgetProblem.Left(profile.name, connectionKept = true, failures = emptyList())
        val committed = mutableListOf<LeftOver>()
        for (key in scoped.filter { it in retained }) {
            if (!store.removePreference(key)) committed += LeftOver.AutomationSettings
        }
        return if (committed.isEmpty()) null else ForgetProblem.Left(profile.name, connectionKept = false, failures = committed.distinct())
    }

    /** Owners that must survive a failed row commit (Dart `retainedKeys`); swept after the row goes. */
    private fun retainedKeys(id: String): Set<String> =
        setOf("oc.automaticActivity.$id", "oc.automation.$id", "oc.teamEngineDeleted.$id", "oc.scheduleDeleted.$id")

    /** `ProfileStore.profileScopedPreferenceKeys`: `oc.<what>.<id>` and `oc.<what>.<id>.<item>`, never app-wide keys. */
    public fun scopedKeys(id: String): Set<String> {
        if (id.isEmpty()) return emptySet()
        val longer = store.profiles().map { it.id }.filter { it.length > id.length }
        return store.preferenceKeys().filter { key -> owns(key, id, longer) }.toSet()
    }

    private fun owns(key: String, id: String, longer: List<String>): Boolean {
        if (!key.startsWith("oc.")) return false
        val namespaceEnd = key.indexOf('.', 3)
        if (namespaceEnd < 0) return false
        val namespace = key.substring(3, namespaceEnd)
        if (namespace in APP_WIDE) return false
        val ownerAndItem = key.substring(namespaceEnd + 1)
        if (namespace == "draft") return ownerAndItem.endsWith(".$id") && longer.none { ownerAndItem.endsWith(".$it") }
        return ownerAndItem == id || (ownerAndItem.startsWith("$id.") && longer.none { ownerAndItem.startsWith("$it.") || ownerAndItem == it })
    }

    private companion object {
        val APP_WIDE =
            setOf(
                "transcript", "notify", "firstRun", "nudges", "secure", "dev", "linuxReset", "mobile", "replySpeed",
                "readStateConsentMigration", "usageAlertSeen", "usageAlerts", "usageWidget", "a2aTasks", "a2aBearer",
            )
    }
}

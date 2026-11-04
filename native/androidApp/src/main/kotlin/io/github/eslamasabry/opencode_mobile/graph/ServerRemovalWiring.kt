package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.*
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.delivery.queue.*
import io.github.eslamasabry.oc.engine.profiles.*
import io.github.eslamasabry.oc.feature.servers.presentation.*
import java.util.UUID

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ServerRemovalPort>())
@Inject
class NativeServerRemoval(
    private val stores: PreviewStores,
    private val runtime: PreviewRuntime,
    private val redactor: Redactor,
) : ServerRemovalPort {
    private class Held(val profile: LoadedProfile, val queue: QueuedPromptRemovalPlan)

    private val plans = LinkedHashMap<String, Held>()

    override suspend fun inspect(profile: ProfileId): Outcome<ServerRemovalPlan> {
        when (val ready = stores.warm()) {
            is Outcome.Failed -> return ready
            is Outcome.Ok -> Unit
        }
        val saved = stores.profiles.profile(profile) ?: return stale()
        val queue =
            OfflineQueueStore(stores.prefs, stores.lanes.app).load() as? QueueRead.Readable
                ?: return Failure.StorageRefused().failed()
        val snapshot =
            QueuedPromptRemovalPlan.fromSnapshot(
                profile,
                stores.prefs.string(OfflineQueueStore.KEY),
            )
        if (snapshot is Outcome.Failed) return snapshot
        val rawDrafts = stores.prefs.get("oc.sessionDrafts")
        if (rawDrafts != null && rawDrafts !is PrefValue.Str)
            return Failure.StorageRefused().failed()
        val drafts =
            if (rawDrafts == null) emptyList<Any?>()
            else
                DartJson.decodeOrNull((rawDrafts as PrefValue.Str).value) as? List<*>
                    ?: return Failure.StorageRefused().failed()
        if (drafts.any { it !is Map<*, *> }) return Failure.StorageRefused().failed()
        val draftCount = drafts.count {
            val id = (it as Map<*, *>)["profileID"]
            id == profile.raw || (id == "" && stores.profiles.profiles.value.size == 1)
        }
        val mine = queue.entries.filter { it.profileId == profile }
        val token = UUID.randomUUID().toString()
        while (plans.size >= 8) plans.remove(plans.keys.first())
        plans[token] = Held(saved, (snapshot as Outcome.Ok).value)
        return ServerRemovalPlan(
                token,
                redactor.redact(saved.profile.name),
                mine.size,
                mine.count { it.dispatched },
                draftCount,
                stores.profiles.activeId.value == profile,
            )
            .ok()
    }

    override suspend fun remove(token: String, keepQueued: Boolean): Outcome<Unit> {
        val held = plans[token] ?: return stale()
        if (stores.profiles.profile(held.profile.id) !== held.profile) return stale()
        return runtime.removeConfirmed(
            held.profile.id,
            DeletionOptions(keepQueuedPromptsAsDrafts = keepQueued, confirmedQueue = held.queue),
        )
    }

    override fun forget(token: String) {
        plans.remove(token)
    }

    private fun stale(): Outcome<Nothing> = Failure.Refused(RefusalReason.StaleScope).failed()
}

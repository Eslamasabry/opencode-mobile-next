package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.attention.activity.*
import io.github.eslamasabry.oc.engine.profiles.*
import java.util.concurrent.ConcurrentHashMap

/**
 * One history writer and process-local inverse registry shared by the Inbox, the Activity screen
 * and every confirmed automatic producer (restart Undo included).
 */
@SingleIn(AppScope::class)
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
@ContributesIntoSet(AppScope::class, binding = binding<ProfileResourceCleaner>())
class AppActivityHistory(private val stores: PreviewStores, redactor: Redactor) :
    ProfileCloser, ProfileResourceCleaner {
    private val blocked = ConcurrentHashMap.newKeySet<ProfileId>()
    private val opened = ConcurrentHashMap<ProfileId, AutomaticActivity>()
    private val registry =
        AutomaticActivityRegistry(
            object : AttentionKeyStore {
                override suspend fun read(key: String): String? =
                    (stores.prefs.get(key) as? PrefValue.Str)?.value

                override suspend fun write(key: String, value: String): Boolean {
                    val id = ProfileId(key.removePrefix("oc.automaticActivity."))
                    if (stores.profiles.profile(id) == null) return false
                    return stores.prefs.commit(stores.lanes.forProfile(id)) {
                        putString(key, value)
                    } is Outcome.Ok
                }

                override suspend fun remove(key: String): Boolean =
                    stores.prefs.commit(stores.lanes.app) { remove(key) } is Outcome.Ok
            },
            redactor,
        )
    override val owner: String = ":engine:attention (automatic activity)"
    override val order: Int = DeletionStep.AFTER_ROW

    private fun admitting(id: ProfileId): Boolean =
        id !in blocked &&
            stores.profiles.profile(id) != null &&
            stores.lanes.forProfile(id).isAdmitting

    suspend fun forProfile(id: ProfileId): AutomaticActivity? {
        if (stores.warm() !is Outcome.Ok || !admitting(id)) return null
        val history =
            registry.forProfile(id.raw) { stores.profiles.profile(id) != null } ?: return null
        opened[id] = history
        if (id in blocked) {
            history.stopAdmission()
            return null
        }
        return history
    }

    override fun stopAdmission(profile: ProfileId) {
        blocked += profile
        opened[profile]?.stopAdmission()
    }

    override suspend fun drain(profile: ProfileId): Outcome<Unit> {
        opened[profile]?.prepareForDeletion()
        return Unit.ok()
    }

    override fun reopen(profile: ProfileId) {
        if (stores.profiles.profile(profile) == null) return
        opened[profile]?.cancelDeletion()
        blocked -= profile
    }

    override suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int> {
        val history = opened[profileId]
        val removed =
            if (history != null) history.deleteProfileData()
            else
                stores.prefs.commit(stores.lanes.app) {
                    remove(AutomaticActivity.keyFor(profileId.raw))
                } is Outcome.Ok
        if (!removed) return Outcome.Failed(Failure.StorageRefused())
        registry.forgetProfile(profileId.raw)
        opened.remove(profileId)
        return 1.ok()
    }

    suspend fun forgetRemoved(profile: ProfileId) {
        // The after-row cleaner owns erasure and retry. Do not dispose its owner on a list
        // emission.
        if (profile !in blocked) {
            registry.forgetProfile(profile.raw)
            opened.remove(profile)
        }
    }
}

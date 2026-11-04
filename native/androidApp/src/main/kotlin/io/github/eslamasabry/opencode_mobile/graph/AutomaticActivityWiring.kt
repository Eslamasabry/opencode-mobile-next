package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.attention.activity.AttentionKeyStore
import io.github.eslamasabry.oc.engine.attention.activity.AutomaticActivity
import io.github.eslamasabry.oc.engine.attention.activity.AutomaticActivityRegistry

/** The restart producer and Activity screen share one writer and process-local inverse registry. */
@Inject
@SingleIn(AppScope::class)
class AppAutomaticActivities(private val stores: PreviewStores, redactor: Redactor) {
    private val registry =
        AutomaticActivityRegistry(
            object : AttentionKeyStore {
                override suspend fun read(key: String): String? =
                    (stores.prefs.get(key) as? PrefValue.Str)?.value

                override suspend fun write(key: String, value: String): Boolean {
                    val profile = ProfileId(key.removePrefix("oc.automaticActivity."))
                    if (!admitting(profile)) return false
                    return stores.prefs.commit(stores.lanes.forProfile(profile)) {
                        putString(key, value)
                    } is Outcome.Ok
                }

                override suspend fun remove(key: String): Boolean {
                    val profile = ProfileId(key.removePrefix("oc.automaticActivity."))
                    return stores.prefs.commit(stores.lanes.forProfile(profile)) {
                        remove(key)
                    } is Outcome.Ok
                }
            },
            redactor,
        )

    private fun admitting(profile: ProfileId): Boolean =
        stores.profiles.profile(profile) != null && stores.lanes.forProfile(profile).isAdmitting

    suspend fun forProfile(profile: ProfileId): AutomaticActivity? {
        if (stores.warm() !is Outcome.Ok || !admitting(profile)) return null
        return registry.forProfile(profile.raw) { admitting(profile) }
    }

    suspend fun forget(profile: ProfileId) = registry.forgetProfile(profile.raw)
}

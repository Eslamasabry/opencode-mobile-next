package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.agents.feed.SessionInventoryCache
import io.github.eslamasabry.oc.engine.agents.feed.SessionInventoryStorage
import io.github.eslamasabry.oc.engine.profiles.StoredProfile
import kotlin.time.Clock

/**
 * P12-BUILD-CITED (B-STATE-103/104): the conversations list paints the last-known labels of the active server at a
 * cold start, before the server answers. The cache lives in the adopted preference file under
 * `oc.sessionInventory.<profileId>`, so a Flutter cache migrates as is and profile deletion's `oc.<what>.<id>` sweep
 * removes it. The preview is read-only: the server's first answer replaces it.
 */
@SingleIn(AppScope::class)
@Inject
class SessionInventoryWiring(private val stores: PreviewStores, redactor: Redactor, private val clock: Clock) {
    private val cache =
        SessionInventoryCache(
            object : SessionInventoryStorage {
                override fun read(key: String): String? = (stores.prefs.get(key) as? PrefValue.Str)?.value

                override suspend fun write(key: String, raw: String): Outcome<Unit> =
                    stores.prefs.commit(stores.lanes.app) { putString(key, raw) }
            },
            mask = redactor::redact,
        )

    /** The saved labels for exactly this profile at its default location, as listing rows; null when none fit. */
    fun preview(id: ProfileId): List<Session>? {
        val stored = stores.profiles.profile(id)?.profile ?: return null
        return cache.read(id.raw, scopeOf(stored))?.sessions?.map { it.toSession() }?.takeIf { it.isNotEmpty() }
    }

    /** Persists a fresh listing; [isCurrent] fences a profile removed or replaced meanwhile. */
    suspend fun remember(id: ProfileId, sessions: List<Session>, isCurrent: () -> Boolean) {
        val stored = stores.profiles.profile(id)?.profile ?: return
        cache.save(id.raw, scopeOf(stored), sessions, clock.now().toEpochMilliseconds()) {
            isCurrent() && stores.profiles.profile(id) != null
        }
    }

    private fun scopeOf(p: StoredProfile): String =
        SessionInventoryCache.scopeFor(
            backend = when (p.backend) { BackendKind.OpenCode2 -> "openCode"; BackendKind.Paseo -> "paseo"; BackendKind.Codex -> "codex" },
            flavor = "v2",
            baseUrl = p.baseUrl,
            username = p.username,
            directory = null,
            workspace = null,
        )
}

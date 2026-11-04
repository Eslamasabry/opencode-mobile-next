package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.feature.settings.presentation.*
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Local profile choices use the same acknowledged authorities as notifications and privacy. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ServerPreferencesSource>())
@Inject
class NativeServerPreferences(
    private val stores: PreviewStores,
    private val defaults: NativeServerDefaults,
    private val monitoring: AndroidProfileMonitoring,
    private val sharing: NativeReadStateConsent,
) : ServerPreferencesSource {
    private val writes = Mutex()

    override fun watch(profile: ProfileId): Flow<Outcome<ServerLocalFacts>> = flow {
        when (val warm = stores.warm()) {
            is Outcome.Failed -> {
                emit(warm)
                return@flow
            }
            is Outcome.Ok -> Unit
        }
        emitAll(
            combine(
                stores.profiles.profiles,
                monitoring.rows,
                sharing.revision,
                stores.prefs.changes.onStart { emit(emptySet()) },
            ) { _, _, _, _ ->
                facts(profile)
            }
        )
    }

    private suspend fun facts(profile: ProfileId): Outcome<ServerLocalFacts> {
        if (stores.profiles.profile(profile) == null)
            return Failure.Refused(RefusalReason.ProfileDeleted).failed()
        val model =
            when (val read = defaults.model(profile)) {
                is Outcome.Failed -> return read
                is Outcome.Ok -> read.value
            }
        // The app's one monitor: watching is its opt-in; it alerts whenever watching is on.
        val watched = monitoring.rows.value[profile]
        val read = sharing.snapshot(profile)
        return ServerLocalFacts(
                model,
                watched?.enabled ?: false,
                true,
                watched?.available ?: false,
                read.enabled,
                read.defaultEnabled,
                read.saving,
                stores.prefs.string("oc.variant." + profile.raw).orEmpty(),
            )
            .ok()
    }

    override suspend fun models(profile: ProfileId): Outcome<ImmutableList<ServerModelChoice>> =
        defaults.choices(profile)

    override suspend fun selectModel(
        profile: ProfileId,
        model: ModelRef?,
        variant: String,
    ): Outcome<Unit> = writes.withLock { defaults.set(profile, model, variant) }

    override suspend fun watching(profile: ProfileId, enabled: Boolean): Outcome<Unit> =
        writes.withLock {
            monitoring.enabled(profile, enabled)
        }

    /** The app's monitor alerts whenever watching is on; a separate per-server mute is not offered yet. */
    override suspend fun notifications(profile: ProfileId, enabled: Boolean): Outcome<Unit> =
        if (enabled) Unit.ok()
        else Failure.Unavailable(io.github.eslamasabry.oc.core.common.FeatureId("server-notifications")).failed()

    override suspend fun shareRead(profile: ProfileId, enabled: Boolean): Outcome<Unit> =
        writes.withLock {
            sharing.setEnabled(profile, enabled)
        }

    override suspend fun reset(profile: ProfileId): Outcome<Unit> = writes.withLock {
        val before =
            when (val read = facts(profile)) {
                is Outcome.Failed -> return@withLock read
                is Outcome.Ok -> read.value
            }
        var failure: Failure? = null
        fun record(result: Outcome<Unit>) {
            if (result is Outcome.Failed && failure == null) failure = result.failure
        }
        // Each independent reset is acknowledged separately; a refusal never hides successful
        // steps.
        if (before.model != null) record(defaults.set(profile, null))
        if (before.sharingRead != before.sharingDefault) record(sharing.reset(profile))
        failure?.failed() ?: Unit.ok()
    }
}

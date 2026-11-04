package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.profiles.StoredProfile
import io.github.eslamasabry.oc.engine.runtime.switching.ManagedLocalServer
import io.github.eslamasabry.oc.feature.onboarding.presentation.ManagedMoveReason
import io.github.eslamasabry.oc.feature.onboarding.presentation.ManagedMoveResult
import io.github.eslamasabry.oc.feature.onboarding.presentation.ManagedMoveSource
import io.github.eslamasabry.oc.protocol.oc2.live.HistoryMigrationPhase
import io.github.eslamasabry.oc.protocol.oc2.live.readHistoryMigrationStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The first-launch page uses the same admitted migration and setup finisher as Linux's visible
 * Update action.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ManagedMoveSource>())
@Inject
class AndroidManagedMove(
    private val stores: PreviewStores,
    private val setup: NativePhoneSetup,
    private val registry: ConnectionRegistry,
    dispatchers: AppDispatchers,
) : ManagedMoveSource {
    private val serial = dispatchers.serial("managed-move")
    private val lane = Mutex()
    private var target: ProfileId? = null

    private fun managed(profile: StoredProfile): Boolean =
        !profile.usesAgentSocket && ManagedLocalServer.isBuiltin(profile.baseUrl)

    private fun pending(profile: StoredProfile): Boolean =
        managed(profile) && (profile.requiresOpenCode2Migration || profile.openCode2HistoryPending)

    override suspend fun needed(): Boolean =
        withContext(serial) {
            lane.withLock {
                if (stores.warm() is Outcome.Failed) return@withLock false
                val active = stores.profiles.activeId.value ?: return@withLock false
                val profile = stores.profiles.profile(active)?.profile ?: return@withLock false
                pending(profile).also { if (it) target = active }
            }
        }

    override suspend fun move(): ManagedMoveResult =
        withContext(serial) {
            lane.withLock {
                when (val warm = stores.warm()) {
                    is Outcome.Failed ->
                        return@withLock failed(ManagedMoveReason.MoveStopped, warm.failure)
                    is Outcome.Ok -> Unit
                }
                val id =
                    target
                        ?: stores.profiles.activeId.value
                        ?: return@withLock failed(ManagedMoveReason.PhoneNotReady)
                target = id
                val original =
                    stores.profiles.profile(id)?.profile
                        ?: return@withLock failed(
                            ManagedMoveReason.MoveStopped,
                            Failure.Refused(RefusalReason.ProfileDeleted),
                        )
                if (!managed(original))
                    return@withLock failed(
                        ManagedMoveReason.MoveStopped,
                        Failure.Refused(RefusalReason.StaleScope),
                    )
                if (!pending(original)) return@withLock ManagedMoveResult.Done
                if (original.requiresOpenCode2Migration) {
                    // NativePhoneSetup calls NativeRuntimeSwitch admission before any
                    // install/start;
                    // its finisher preserves the legacy profile id and starts through the history
                    // copy guard.
                    when (val updated = setup.update()) {
                        is Outcome.Failed -> {
                            val reason =
                                when (val failure = updated.failure) {
                                    is Failure.Refused ->
                                        if (failure.reason == RefusalReason.MigrationPending)
                                            ManagedMoveReason.SeparateHistory
                                        else ManagedMoveReason.MoveStopped
                                    is Failure.Network,
                                    is Failure.TimedOut -> ManagedMoveReason.PhoneNotReady
                                    is Failure.Runtime ->
                                        if (
                                            failure.problem == RuntimeProblem.SlotBusy ||
                                                failure.problem ==
                                                    RuntimeProblem.RecoveryUnavailable
                                        )
                                            ManagedMoveReason.PhoneNotReady
                                        else ManagedMoveReason.MoveStopped
                                    else -> ManagedMoveReason.MoveStopped
                                }
                            return@withLock failed(reason, updated.failure)
                        }
                        is Outcome.Ok -> Unit
                    }
                }
                val current =
                    stores.profiles.profile(id)?.profile
                        ?: return@withLock failed(
                            ManagedMoveReason.MoveStopped,
                            Failure.Refused(RefusalReason.ProfileDeleted),
                        )
                if (
                    current.baseUrl != original.baseUrl ||
                        current.usesAgentSocket ||
                        current.requiresOpenCode2Migration
                )
                    return@withLock failed(ManagedMoveReason.NotConfirmed)
                if (!current.openCode2HistoryPending) return@withLock ManagedMoveResult.Done
                val graph =
                    registry.live.value[id]
                        ?: return@withLock failed(ManagedMoveReason.PhoneNotReady)
                val gateway = graph.session.server
                val confirmed =
                    withTimeoutOrNull(180_000L) {
                        while (true) {
                            if (
                                registry.live.value[id] !== graph ||
                                    stores.profiles.profile(id)?.profile != current
                            )
                                return@withTimeoutOrNull false
                            val result =
                                withTimeoutOrNull(15_000L) { gateway.readHistoryMigrationStatus() }
                            val status =
                                (result as? Outcome.Ok)?.value ?: return@withTimeoutOrNull false
                            when (status.phase) {
                                HistoryMigrationPhase.Completed -> return@withTimeoutOrNull true
                                HistoryMigrationPhase.Error -> return@withTimeoutOrNull false
                                HistoryMigrationPhase.Required,
                                HistoryMigrationPhase.Running -> delay(1_000L)
                            }
                        }
                        @Suppress("UNREACHABLE_CODE") false
                    } == true
                if (
                    !confirmed ||
                        registry.live.value[id] !== graph ||
                        stores.profiles.profile(id)?.profile != current
                )
                    return@withLock failed(ManagedMoveReason.NotConfirmed)
                // Only a reply from the still-current transport clears the durable pending marker.
                when (
                    val saved =
                        stores.profiles.upsert(current.copy(openCode2HistoryPending = false))
                ) {
                    is Outcome.Failed -> failed(ManagedMoveReason.NotConfirmed, saved.failure)
                    is Outcome.Ok ->
                        if (saved.value.profile.openCode2HistoryPending)
                            failed(ManagedMoveReason.NotConfirmed)
                        else ManagedMoveResult.Done
                }
            }
        }

    private fun failed(
        reason: ManagedMoveReason,
        failure: Failure? = null,
    ): ManagedMoveResult.Failed = ManagedMoveResult.Failed(reason, failure)
}

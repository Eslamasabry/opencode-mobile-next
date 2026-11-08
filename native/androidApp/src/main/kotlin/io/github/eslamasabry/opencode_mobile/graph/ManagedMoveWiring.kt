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
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.profiles.StoredProfile
import io.github.eslamasabry.oc.engine.runtime.switching.ManagedLocalServer
import io.github.eslamasabry.oc.feature.onboarding.presentation.ManagedMoveReason
import io.github.eslamasabry.oc.feature.onboarding.presentation.ManagedMoveResult
import io.github.eslamasabry.oc.feature.onboarding.presentation.ManagedMoveSource
import io.github.eslamasabry.oc.engine.runtime.history.HistoryShellPort
import io.github.eslamasabry.oc.engine.runtime.history.LinuxRunResult
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneImportStatus
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneMovePorts
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneMoveProfile
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneMoveResult
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneMoveStop
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneOpenCode2Move
import io.github.eslamasabry.oc.protocol.oc2.live.HistoryMigrationPhase
import io.github.eslamasabry.oc.protocol.oc2.live.readHistoryMigrationStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The move page (first launch, and This phone's "Switch" offer) runs :feature:phonesetup's
 * PhoneOpenCode2Move: the read-only history check first, then the same setup job as Linux's visible
 * Update action, then OpenCode 2's confirmation. This class only binds its ports.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ManagedMoveSource>())
@Inject
class AndroidManagedMove(
    private val stores: PreviewStores,
    private val setup: NativePhoneSetup,
    private val registry: ConnectionRegistry,
    private val runtime: BuiltinRuntime,
    dispatchers: AppDispatchers,
) : ManagedMoveSource {
    private val serial = dispatchers.serial("managed-move")
    private val lane = Mutex()

    /** The connection the import status was first read on; a confirmation from another one counts for nothing. */
    private var confirming: Pair<ProfileId, Any>? = null
    private var confirmingRow: StoredProfile? = null

    private fun managed(profile: StoredProfile): Boolean =
        !profile.usesAgentSocket && ManagedLocalServer.isBuiltin(profile.baseUrl)

    private fun pending(profile: StoredProfile): Boolean =
        managed(profile) && (profile.requiresOpenCode2Migration || profile.openCode2HistoryPending)

    private fun StoredProfile.forMove(): PhoneMoveProfile =
        PhoneMoveProfile(id.raw, requiresOpenCode2Migration, openCode2HistoryPending)

    private val ports =
        object : PhoneMovePorts {
            override suspend fun pending(activeOnly: Boolean): Outcome<PhoneMoveProfile?> {
                when (val warm = stores.warm()) {
                    is Outcome.Failed -> return warm
                    is Outcome.Ok -> Unit
                }
                val active = stores.profiles.activeId.value?.let { stores.profiles.profile(it)?.profile }
                if (active != null && pending(active)) return Outcome.Ok(active.forMove())
                if (activeOnly) return Outcome.Ok(null)
                return Outcome.Ok(
                    stores.profiles.profiles.value.map { it.profile }.firstOrNull(::pending)?.forMove()
                )
            }

            override suspend fun reread(id: String): Outcome<PhoneMoveProfile?> =
                Outcome.Ok(
                    stores.profiles.profile(ProfileId(id))?.profile?.takeIf(::managed)?.forMove()
                )

            override suspend fun installOpenCode2(): Outcome<Unit> = setup.update()

            override suspend fun importStatus(id: String): Outcome<PhoneImportStatus> {
                val key = ProfileId(id)
                val graph = registry.live.value[key] ?: return Outcome.Ok(PhoneImportStatus.NotConnected)
                val row = stores.profiles.profile(key)?.profile
                val first = confirming
                if (first == null || first.first != key) {
                    confirming = key to graph
                    confirmingRow = row
                } else if (first.second !== graph || confirmingRow != row) {
                    return Outcome.Ok(PhoneImportStatus.Failed)
                }
                return when (val read = graph.session.server.readHistoryMigrationStatus()) {
                    is Outcome.Failed -> read
                    is Outcome.Ok ->
                        Outcome.Ok(
                            when (read.value?.phase) {
                                HistoryMigrationPhase.Completed -> PhoneImportStatus.Done
                                HistoryMigrationPhase.Required,
                                HistoryMigrationPhase.Running -> PhoneImportStatus.Waiting
                                HistoryMigrationPhase.Error,
                                null -> PhoneImportStatus.Failed
                            }
                        )
                }
            }

            override suspend fun confirmed(id: String): Outcome<Unit> {
                val key = ProfileId(id)
                val first = confirming
                val row = confirmingRow
                // Only a reply from the still-current transport clears the durable pending marker.
                if (
                    first == null ||
                        first.first != key ||
                        registry.live.value[key] !== first.second ||
                        row == null ||
                        stores.profiles.profile(key)?.profile != row
                )
                    return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
                return when (val saved = stores.profiles.upsert(row.copy(openCode2HistoryPending = false))) {
                    is Outcome.Failed -> saved
                    is Outcome.Ok ->
                        if (saved.value.profile.openCode2HistoryPending)
                            Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
                        else Outcome.Ok(Unit)
                }
            }
        }

    private val shell = HistoryShellPort { script, timeout ->
        when (val ran = runtime.host.run(script, timeout.inWholeSeconds)) {
            is Outcome.Failed -> ran
            is Outcome.Ok -> Outcome.Ok(LinuxRunResult(ran.value.exitCode, ran.value.output))
        }
    }

    private val phoneMove = PhoneOpenCode2Move(ports, shell)

    override suspend fun needed(): Boolean = withContext(serial) { lane.withLock { phoneMove.needed() } }

    override suspend fun move(): ManagedMoveResult =
        withContext(serial) {
            lane.withLock {
                confirming = null
                confirmingRow = null
                when (val result = phoneMove.move()) {
                    PhoneMoveResult.Done -> ManagedMoveResult.Done
                    is PhoneMoveResult.Stopped ->
                        ManagedMoveResult.Failed(
                            when (result.reason) {
                                PhoneMoveStop.PhoneNotReady -> ManagedMoveReason.PhoneNotReady
                                PhoneMoveStop.SeparateHistory -> ManagedMoveReason.SeparateHistory
                                PhoneMoveStop.MoveStopped -> ManagedMoveReason.MoveStopped
                                PhoneMoveStop.NotConfirmed -> ManagedMoveReason.NotConfirmed
                            },
                            result.failure,
                        )
                }
            }
        }
}

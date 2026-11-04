package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.platform.DeviceFacts
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.integrations.usage.*
import io.github.eslamasabry.oc.feature.plans.presentation.CollectorTarget
import io.github.eslamasabry.oc.protocol.paseo.gateway.PaseoGateway
import io.github.eslamasabry.oc.protocol.paseo.gateway.providerUsagePayload
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Opt-in Paseo daemon readings for the visible Plans page and Android Usage widget. No service or
 * timer is started.
 */
internal class AndroidPlansPaseoSource(
    private val registry: ConnectionRegistry,
    private val stores: PreviewStores,
    private val widget: UsageWidgetFeed,
    private val clock: Clock,
    private val facts: DeviceFacts,
    scope: CoroutineScope,
) : UsageSource {
    private val blocked = HashSet<ProfileId>()
    private val monitor =
        PaseoUsageMonitor(
            store = stores.prefs,
            lane = stores.lanes.app,
            ports =
                object : PaseoUsagePorts {
                    override fun paseoProfiles(): List<PaseoUsageProfile> =
                        registry.live.value.mapNotNull { (id, graph) ->
                            if (currentGateway(id) == null) null
                            else PaseoUsageProfile(id.raw, graph.profile.name)
                        }

                    override fun isReadable(profileId: String): Boolean =
                        currentGateway(ProfileId(profileId)) != null

                    override suspend fun readServer(
                        profileId: String
                    ): Outcome<List<ProviderUsageReading>> {
                        val id = ProfileId(profileId)
                        val graph = registry.live.value[id] ?: return stale()
                        val gateway = currentGateway(id) ?: return stale()
                        val answer = gateway.providerUsagePayload()
                        if (
                            registry.live.value[id] !== graph ||
                                currentGateway(id) !== gateway ||
                                !facts.appVisible.value
                        )
                            return stale()
                        return when (answer) {
                            is Outcome.Failed -> answer
                            is Outcome.Ok ->
                                attempt({ Failure.UnreadableReply() }) {
                                    parseProviderUsageList(
                                        DartJson.decodeOrNull(answer.value.toString()),
                                        clock.now(),
                                    )
                                }
                        }
                    }
                },
            widget = widget,
            clock = { clock.now() },
        )

    init {
        monitor.setRuntime(foreground = facts.appVisible.value, backgroundAllowed = false)
        scope.launch {
            facts.appVisible.collect { visible ->
                monitor.setRuntime(foreground = visible, backgroundAllowed = false)
            }
        }
    }

    private fun <T> stale(): Outcome<T> = Failure.Refused(RefusalReason.StaleScope).failed()

    private fun currentGateway(id: ProfileId): PaseoGateway? {
        if (id in blocked || stores.profiles.profile(id) == null) return null
        val graph = registry.live.value[id] ?: return null
        if (!graph.session.capabilities.value.hostAgentProviders) return null
        return graph.session.server as? PaseoGateway
    }

    private fun binding(id: ProfileId, gateway: PaseoGateway): String? {
        val profile = stores.profiles.profile(id)?.profile ?: return null
        return DartJson.sha256Hex(
            DartJson.encode(listOf(profile.baseUrl, profile.username, gateway.transport.epoch))
        )
    }

    fun targets(): List<CollectorTarget> =
        registry.live.value.mapNotNull { (id, graph) ->
            val gateway = currentGateway(id) ?: return@mapNotNull null
            val binding = binding(id, gateway) ?: return@mapNotNull null
            CollectorTarget(id, graph.profile.name, PROVIDER, monitor.enabled(id.raw), binding)
        }

    fun connections(): List<UsageConnection> =
        targets().map {
            UsageConnection(it.profile.raw, it.name, UsageConnectionKind.PaseoServer)
        }

    suspend fun setEnabled(target: CollectorTarget, enabled: Boolean): Boolean {
        if (target.provider != PROVIDER || target.profile in blocked) return false
        val gateway = currentGateway(target.profile) ?: return false
        if (target.binding != binding(target.profile, gateway)) return false
        if (!monitor.setEnabled(target.profile.raw, enabled)) return false
        return enabled || widget.clearForProfile(target.profile.raw)
    }

    /**
     * Admission closes before any suspension; a pending result cannot recreate the removed widget
     * rows.
     */
    suspend fun removeProfile(id: ProfileId): Boolean {
        blocked += id
        monitor.removeProfile(id.raw)
        val removed =
            stores.prefs.commit(stores.lanes.app) { remove(PaseoUsageMonitor.key(id.raw)) }
        return removed is Outcome.Ok && widget.clearForProfile(id.raw)
    }

    override suspend fun read(context: UsageReadContext): Outcome<UsageSourceResult> {
        monitor.setRuntime(foreground = facts.appVisible.value, backgroundAllowed = false)
        if (!facts.appVisible.value) return Outcome.Ok(UsageSourceResult())
        monitor.refresh()
        val readings = ArrayList<UsageReading>()
        val signIns = ArrayList<UsageSignIn>()
        val authoritative = HashSet<String>()
        val unreached = HashSet<String>()
        for (target in targets().filter { it.enabled }) {
            if (!facts.appVisible.value) break
            val rows = monitor.readingsFor(target.profile.raw)
            if (monitor.statusFor(target.profile.raw) != PaseoUsageStatus.Current || rows == null) {
                unreached += target.profile.raw
                continue
            }
            authoritative += target.profile.raw
            for (row in rows) {
                val id = canonicalUsageProviderId(row.providerId) ?: continue
                signIns +=
                    UsageSignIn(
                        id,
                        target.profile.raw,
                        if (row.needsSignIn) SignInState.NotSignedIn else SignInState.SignedIn,
                        SignInMethod.Plan,
                        row.displayName,
                    )
                readings +=
                    UsageReading(
                        providerId = id,
                        displayName = row.displayName,
                        connectionId = target.profile.raw,
                        status =
                            if (row.needsSignIn) UsageReadingStatus.NotSignedIn
                            else UsageReadingStatus.Ok,
                        fetchedAt = row.fetchedAt,
                        windows =
                            row.windows.mapIndexed { index, window ->
                                UsageWindow(
                                    "paseo_$index",
                                    100.0 - window.remainingPercent,
                                    window.durationSeconds,
                                    window.resetsAt,
                                    window.label,
                                )
                            },
                        method = SignInMethod.Plan,
                        checkedAt = context.now,
                    )
            }
        }
        return Outcome.Ok(
            UsageSourceResult(
                readings,
                signIns,
                authoritativeSignIns = authoritative,
                unreached = unreached,
            )
        )
    }

    companion object {
        const val PROVIDER = "paseo"
    }
}

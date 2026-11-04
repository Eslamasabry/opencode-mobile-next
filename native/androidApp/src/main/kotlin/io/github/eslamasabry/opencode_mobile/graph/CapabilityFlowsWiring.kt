package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.ui.routes.*
import kotlinx.coroutines.withContext

/**
 * Each enable door opens the real setup surface; no permission or server mutation is inferred from
 * navigation.
 */
@BindingContainer
@ContributesTo(AppScope::class)
object CapabilityFlowsWiring {
    @Provides
    @SingleIn(AppScope::class)
    fun flows(
        nav: AppNavigator,
        registry: ConnectionRegistry,
        stores: PreviewStores,
        app: Application,
        dispatchers: AppDispatchers,
    ): CapabilityFlows =
        CapabilityFlows(
            (CapabilityFlows.androidFlows - unavailable.keys).associateWith { flow ->
                CapabilityEnableHandler { request ->
                    withContext(dispatchers.main) {
                        val ref = request.ref
                        val profile = ref?.profileId ?: stores.profiles.activeId.value
                        val graph = profile?.let { registry.live.value[it] }
                        if (ref != null && stores.profiles.profile(ref.profileId) == null) {
                            Outcome.Failed(
                                Failure.Refused(
                                    io.github.eslamasabry.oc.core.common.RefusalReason
                                        .ProfileDeleted
                                )
                            )
                        } else
                            attempt({ Failure.Device() }) {
                                when (flow) {
                                    CapabilityEnableFlow.AddServer ->
                                        nav.open(
                                            CapabilityServerSetupRoute(
                                                CapabilityServerDoor.AddServer
                                            )
                                        )
                                    CapabilityEnableFlow.ServerGeneration ->
                                        nav.open(
                                            CapabilityServerSetupRoute(
                                                CapabilityServerDoor.OpenCode2
                                            )
                                        )
                                    CapabilityEnableFlow.AddServerCodex ->
                                        nav.open(
                                            CapabilityAddComputerRoute(CapabilityComputerKind.Codex)
                                        )
                                    CapabilityEnableFlow.AddServerPaseo ->
                                        nav.open(
                                            CapabilityAddComputerRoute(CapabilityComputerKind.Paseo)
                                        )
                                    CapabilityEnableFlow.AddToolClaude ->
                                        nav.open(CapabilityPhoneAgentRoute("claude"))
                                    CapabilityEnableFlow.PhoneSetup ->
                                        nav.open(PhoneSetupRoute.Setup)
                                    CapabilityEnableFlow.ModelSignIn ->
                                        when {
                                            graph?.session?.capabilities?.value?.serverCatalog ==
                                                true && profile != null ->
                                                nav.open(ProvidersRoute(profile))
                                            graph?.session?.capabilities?.value?.agentAccount ==
                                                true && profile != null ->
                                                nav.open(CapabilityAgentAccountRoute(profile))
                                            else ->
                                                nav.open(
                                                    CapabilityServerSetupRoute(
                                                        CapabilityServerDoor.AddServer
                                                    )
                                                )
                                        }
                                    CapabilityEnableFlow.VoiceModelSetup ->
                                        nav.open(VoiceRoute.Settings)
                                    CapabilityEnableFlow.McpAdd ->
                                        nav.open(
                                            profile?.let {
                                                McpCatalogRoute(
                                                    it,
                                                    graph
                                                        ?.session
                                                        ?.server
                                                        ?.location
                                                        ?.value
                                                        ?.directory,
                                                )
                                            } ?: ServersRoute.Home
                                        )
                                    CapabilityEnableFlow.ProjectChoose ->
                                        nav.open(ProjectsRoute.Picker(profile))
                                    CapabilityEnableFlow.ProjectGitInit ->
                                        if (ref != null) nav.open(GitRoute(ref))
                                        else nav.open(ProjectsRoute.Picker(profile))
                                    CapabilityEnableFlow.AllowNotifications ->
                                        app.startActivity(
                                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                                .putExtra(
                                                    Settings.EXTRA_APP_PACKAGE,
                                                    app.packageName,
                                                )
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    CapabilityEnableFlow.AllowBackground ->
                                        nav.open(PhoneRoute.KeepRunning)
                                    CapabilityEnableFlow.AllowCamera,
                                    CapabilityEnableFlow.AllowMicrophone ->
                                        app.startActivity(
                                            Intent(
                                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                    Uri.parse("package:${app.packageName}"),
                                                )
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    CapabilityEnableFlow.TailscaleSetup ->
                                        nav.open(
                                            CapabilityServerSetupRoute(
                                                CapabilityServerDoor.Tailscale
                                            )
                                        )
                                    CapabilityEnableFlow.QuotaCollectorGuide -> nav.open(PlansRoute)
                                    CapabilityEnableFlow.TeamTurnOn,
                                    CapabilityEnableFlow.TeamHostGuide,
                                    CapabilityEnableFlow.AddExternalAgent,
                                    CapabilityEnableFlow.PhoneSetupTermux ->
                                        return@withContext Outcome.Failed(
                                            Failure.Unavailable(
                                                io.github.eslamasabry.oc.core.common.FeatureId(
                                                    flow.capability
                                                )
                                            )
                                        )
                                }
                            }
                    }
                }
            },
            unavailable = unavailable,
        )

    private val unavailable =
        mapOf(
            CapabilityEnableFlow.TeamTurnOn to CapabilityFlowLimit.TeamAfterSwitch,
            CapabilityEnableFlow.TeamHostGuide to CapabilityFlowLimit.TeamAfterSwitch,
            CapabilityEnableFlow.AddExternalAgent to CapabilityFlowLimit.ExternalAgentsDropped,
            CapabilityEnableFlow.PhoneSetupTermux to CapabilityFlowLimit.TermuxMigrationOnly,
        )
}

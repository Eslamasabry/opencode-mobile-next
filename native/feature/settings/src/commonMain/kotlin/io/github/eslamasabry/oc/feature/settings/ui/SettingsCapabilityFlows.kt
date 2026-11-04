package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsHost
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.ui.kit.capability.KitCapabilities
import io.github.eslamasabry.oc.ui.kit.capability.KitEnableFlow
import io.github.eslamasabry.oc.ui.kit.capability.KitEnableFlowHandler
import io.github.eslamasabry.oc.ui.kit.capability.LocalKitCapabilities

/**
 * Only routes this host actually owns are registered. Unsupported flows keep their explanation
 * without a button.
 */
@Composable
internal fun SettingsCapabilityFlows(host: SettingsHost, content: @Composable () -> Unit) {
    // Collected, so a server connecting or leaving while Settings is open re-registers flows.
    val facts by host.facts.collectAsState()
    val registry =
        remember(host, facts) {
            KitCapabilities().apply {
                fun door(flow: KitEnableFlow, place: SettingsOutside) {
                    if (facts.canOpen(place))
                        register(
                            flow,
                            KitEnableFlowHandler {
                                host.openers.open(place)
                                Outcome.Ok(Unit)
                            },
                        )
                }
                door(KitEnableFlow.AddServer, SettingsOutside.Servers)
                door(KitEnableFlow.ServerGeneration, SettingsOutside.Servers)
                door(KitEnableFlow.AddServerCodex, SettingsOutside.Servers)
                door(KitEnableFlow.AddServerPaseo, SettingsOutside.Servers)
                if (facts.platform.builtinLinux) {
                    door(KitEnableFlow.PhoneSetup, SettingsOutside.ThisPhone)
                    door(KitEnableFlow.AddToolClaude, SettingsOutside.Agents)
                }
                if (facts.server != null) {
                    door(KitEnableFlow.ModelSignIn, SettingsOutside.Providers)
                    door(KitEnableFlow.McpAdd, SettingsOutside.Tools)
                }
                if (facts.platform.voice)
                    door(KitEnableFlow.VoiceModelSetup, SettingsOutside.VoiceSetup)
                if (facts.platform.notifications)
                    door(
                        KitEnableFlow.AllowNotifications,
                        SettingsOutside.AndroidNotificationSettings,
                    )
                if (facts.platform.backgroundService)
                    door(KitEnableFlow.AllowBackground, SettingsOutside.KeepRunning)
            }
        }
    CompositionLocalProvider(LocalKitCapabilities provides registry, content = content)
}

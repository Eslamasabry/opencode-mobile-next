package io.github.eslamasabry.oc.feature.settings.automation

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.*
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
public fun ColumnScope.AutomationSection(state: AutomationUi, actions: AutomationActions) {
    if (state.servers.isEmpty()) return
    KitGroupNote(stringResource(Res.string.secAutomationTitle))
    KitGroupNote(stringResource(Res.string.secAutomationDetail))
    if (state.failed) KitNotice(stringResource(Res.string.automationSaveFailed))
    state.servers.forEach { server ->
        var expanded by remember(server.id) { mutableStateOf(false) }
        KitExpandRow(
            title = server.name,
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            KitRowGroup {
                server.choices.forEach { choice ->
                    val label =
                        when (choice.id) {
                            "reconnect" -> Res.string.secAutomationReconnect
                            "retryIntake" -> Res.string.secAutomationRetryIntake
                            "reconcileQueuedSends" -> Res.string.secAutomationReconcileQueuedSends
                            "retryProviderErrors" -> Res.string.secAutomationRetryProviderErrors
                            "restartPhoneServer" -> Res.string.secAutomationRestartPhoneServer
                            "resumeSetup" -> Res.string.secAutomationResumeSetup
                            "recheckEnvironment" -> Res.string.secAutomationRecheckEnvironment
                            "pollRestartHealth" -> Res.string.secAutomationPollRestartHealth
                            "thermalRecovery" -> Res.string.secAutomationThermalRecovery
                            "reduceEffects" -> Res.string.secAutomationReduceEffects
                            "chooseDefaults" -> Res.string.secAutomationChooseDefaults
                            "detectInstallations" -> Res.string.secAutomationDetectInstallations
                            "preserveDrafts" -> Res.string.secAutomationPreserveDrafts
                            "followOutput" -> Res.string.secAutomationFollowOutput
                            "pollOpenPages" -> Res.string.secAutomationPollOpenPages
                            "boundedPaging" -> Res.string.secAutomationBoundedPaging
                            "routeReadyWork" -> Res.string.secAutomationRouteReadyWork
                            "wakeStalledPool" -> Res.string.secAutomationWakeStalledPool
                            "restartWorkers" -> Res.string.secAutomationRestartWorkers
                            "recycleWorkers" -> Res.string.secAutomationRecycleWorkers
                            "holdAtProviderLimit" -> Res.string.secAutomationHoldAtProviderLimit
                            "retryUnconfirmedAnswer" ->
                                Res.string.secAutomationRetryUnconfirmedAnswer
                            "retryTransientTask" -> Res.string.secAutomationRetryTransientTask
                            "fallBackToDirectTask" -> Res.string.secAutomationFallBackToDirectTask
                            "applyCodePush" -> Res.string.secAutomationApplyCodePush
                            "restartDevServices" -> Res.string.secAutomationRestartDevServices
                            "stopIdleHelpers" -> Res.string.secAutomationStopIdleHelpers
                            "cleanCaches" -> Res.string.secAutomationCleanCaches
                            "updateWhenIdle" -> Res.string.secAutomationUpdateWhenIdle
                            "monitorOtherServers" -> Res.string.secAutomationMonitorOtherServers
                            "monitorQuota" -> Res.string.secAutomationMonitorQuota
                            else -> return@forEach
                        }
                    KitSwitchRow(
                        title = stringResource(label),
                        checked = choice.enabled,
                        enabled =
                            KitEnabled.of(
                                !state.saving,
                                stringResource(Res.string.automationSaving),
                            ),
                        onCheckedChange = { actions.setBehavior(server.id, choice.id, it) },
                    )
                }
            }
        }
    }
}

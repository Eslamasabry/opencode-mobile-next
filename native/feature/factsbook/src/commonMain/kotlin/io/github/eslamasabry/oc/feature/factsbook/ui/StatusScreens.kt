package io.github.eslamasabry.oc.feature.factsbook.ui

import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.factsbook.FactsBookCapabilities
import io.github.eslamasabry.oc.engine.factsbook.FactsBookLink
import io.github.eslamasabry.oc.engine.factsbook.ModuleState
import io.github.eslamasabry.oc.engine.factsbook.ModuleStatus
import io.github.eslamasabry.oc.engine.factsbook.NodeStatus
import io.github.eslamasabry.oc.feature.factsbook.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.StatusActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.StatusUi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowState
import io.github.eslamasabry.oc.ui.kit.row.KitRowStateTone
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.text.KitBidi
import io.github.eslamasabry.oc.ui.kit.text.kitRelativeAge
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
private fun pageBar(title: String, onBack: () -> Unit): @Composable () -> Unit = {
    KitTopBar(title = title, exit = KitTopBarExit.Back, exitLabel = stringResource(Res.string.kitTopBarBack), onExit = onBack)
}

@Composable
private fun pageGutter() = LocalKitTokens.current.gutter.dp

@Composable
private fun PageError(error: io.github.eslamasabry.oc.engine.factsbook.FactsBookError?, retry: () -> Unit) {
    val details = failureDetails(error)
    KitStateView(
        KitStateKind.Error,
        stringResource(Res.string.fbErrorTitle),
        body = failureWords(error) + (details?.let { "\n\n$it" } ?: ""),
        primary = KitAction(stringResource(Res.string.commonRetry), retry),
    )
}

/** "this phone's memory", "this memory" or the node's host and port (F: `nodeName`). */
@Composable
internal fun nodeName(link: FactsBookLink?): String {
    val authority = link?.baseUrl?.substringAfter("://")?.substringBefore('/')?.takeIf { it.isNotEmpty() }
        ?: return stringResource(Res.string.fbNodeNameDefault)
    val host = authority.substringBeforeLast(':').removePrefix("[").removeSuffix("]")
    return if (host == "127.0.0.1" || host == "localhost") stringResource(Res.string.fbNodeNameThisPhone) else KitBidi.ltr(authority)
}

@Composable
internal fun moduleStateWords(state: ModuleState): String =
    stringResource(
        when (state) {
            ModuleState.Ok -> Res.string.fbStateReady
            ModuleState.Waiting -> Res.string.fbStateWaiting
            ModuleState.Stale -> Res.string.fbStateStale
            ModuleState.Failed -> Res.string.fbStateFailed
            ModuleState.Disabled -> Res.string.fbStateOff
            ModuleState.Absent -> Res.string.fbStateNotSetUp
        }
    )

@Composable
private fun moduleName(name: String): String =
    when (name) {
        "storage" -> stringResource(Res.string.fbModuleStorage)
        "cloud" -> stringResource(Res.string.fbModuleCloud)
        "care" -> stringResource(Res.string.fbModuleCare)
        "replication" -> stringResource(Res.string.fbModuleReplication)
        "index" -> stringResource(Res.string.fbModuleIndex)
        "membership" -> stringResource(Res.string.fbModuleMembership)
        else -> name
    }

private fun stateTone(state: ModuleState): KitRowStateTone? =
    when (state) {
        ModuleState.Ok -> KitRowStateTone.Quiet
        ModuleState.Waiting -> KitRowStateTone.Running
        ModuleState.Failed -> KitRowStateTone.Failed
        else -> null
    }

/** What is not right, in the person's words; empty when all is well (F: `_problems`). */
@Composable
private fun problems(state: StatusUi, status: NodeStatus): List<String> {
    val found = mutableListOf<String>()
    status.modules.forEach { (name, module) ->
        if (name != "cloud" && module.state in setOf(ModuleState.Failed, ModuleState.Stale, ModuleState.Waiting)) found += moduleName(name)
    }
    if ("index" !in status.modules && (status.indexNormal == ModuleState.Failed || status.indexNormal == ModuleState.Waiting)) {
        found += stringResource(Res.string.fbStatusSearch)
    }
    val control = state.control
    if (control != null && (control.forked || !control.trusted)) found += stringResource(Res.string.fbStatusHistory)
    return found
}

@Composable
private fun historyWords(state: StatusUi): String {
    val control = state.control ?: return stringResource(Res.string.fbStatusHistoryNotChecked)
    return stringResource(
        when {
            control.forked -> Res.string.fbStatusHistoryForked
            control.trusted -> Res.string.fbStatusHistoryVerified
            else -> Res.string.fbStatusHistoryFailed
        }
    )
}

/**
 * Status (F: status_screen.dart): one line that says everything works or what does not, the backup, what is not
 * turned on yet, this phone (approval, Disconnect), and module by module under Details. "Ready" means recently proven
 * healthy, never "working now"; with no cloud the page says so and makes no claim of a backup.
 */
@Composable
public fun StatusScreen(state: StatusUi, actions: StatusActions) {
    val title = stringResource(Res.string.fbStatusTitle)
    val name = nodeName(state.link)
    KitSheetHost(Modifier.fillMaxSize()) {
        KitScreen(
            topBar = pageBar(title, actions::back),
            width = KitScreenWidth.Reading,
            loading = state.loading && state.status != null,
            loadingLabel = title,
            edges = KitScreenEdges.Bleed,
        ) {
            val status = state.status
            when {
                status == null && state.error == null -> KitSkeletonRows()
                status == null -> PageError(state.error, actions::reload)
                else -> StatusBody(state, status, actions, name)
            }
        }
        KitConfirm(
            spec =
                if (!state.confirmDisconnect) null
                else
                    KitConfirmSpec(
                        title = stringResource(Res.string.fbDisconnectTitle, name),
                        confirmLabel = stringResource(Res.string.fbDisconnect, name),
                        kind = KitConfirmKind.Destructive,
                        icon = KitIcons.unlink,
                        body = stringResource(Res.string.fbDisconnectBody),
                    ),
            onConfirm = actions::disconnect,
            onCancel = actions::cancelDisconnect,
        )
    }
}

@Composable
private fun StatusBody(state: StatusUi, status: NodeStatus, actions: StatusActions, name: String) {
    val gutter = pageGutter()
    val found = problems(state, status)
    LazyColumn(Modifier.fillMaxSize()) {
        item("summary") {
            KitRowGroup {
                KitRow(
                    title =
                        if (found.isEmpty()) stringResource(Res.string.fbStatusAllWorking)
                        else pluralStringResource(Res.plurals.fbStatusNeedsLook, found.size, found.size),
                    titleMaxLines = 2,
                    supporting = found.takeIf { it.isNotEmpty() }?.joinToString(", "),
                    supportingMaxLines = 3,
                    leading = KitRowLeading.Glyph(if (found.isEmpty()) KitIcons.checkCircle else KitIcons.warning),
                )
                val backup =
                    when {
                        !status.safeInCloud -> stringResource(Res.string.fbStatusNoBackup)
                        status.staleSeconds == null -> stringResource(Res.string.fbStatusBackedUp)
                        else ->
                            stringResource(
                                Res.string.fbStatusBackedUpAge,
                                kitRelativeAge(state.nowMs - status.staleSeconds!! * 1000, state.nowMs),
                            )
                    }
                KitRow(title = stringResource(Res.string.fbStatusBackup), supporting = backup)
            }
        }
        if (status.modules["replication"]?.state != ModuleState.Ok) {
            item("replication") { KitGroupNote(stringResource(Res.string.fbStatusNoReplication)) }
        }
        val notYet =
            listOf(
                "cloud_transport" to Res.string.fbNotYetCloud,
                FactsBookCapabilities.SENSITIVE_UNLOCK to Res.string.fbNotYetUnlock,
                "phone_alarm_delivery" to Res.string.fbNotYetAlarms,
            ).filter { state.capabilities.isDisabled(it.first) }
        if (notYet.isNotEmpty()) {
            item("notYet") {
                KitRowGroup(label = stringResource(Res.string.fbStatusNotYet)) {
                    notYet.forEach { (_, words) ->
                        KitRow(title = stringResource(words), supporting = stringResource(Res.string.fbNotAvailableYet))
                    }
                }
            }
        }
        item("phone") {
            KitRowGroup(
                label = stringResource(Res.string.fbStatusThisPhone),
                destructive = {
                    KitRow(
                        title = stringResource(Res.string.fbDisconnect, name),
                        titleMaxLines = 2,
                        supporting = stringResource(Res.string.fbDisconnectDetail),
                        supportingMaxLines = 3,
                        onClick = actions::askDisconnect,
                    )
                },
            ) {
                KitRow(
                    title = stringResource(Res.string.fbStatusApprove),
                    supporting = stringResource(if (state.canApprove) Res.string.fbStateReady else Res.string.fbStateNotSetUp),
                    trailing = if (state.canApprove) KitRowTrailing.None else KitRowTrailing.Chevron,
                    onClick = if (state.canApprove) null else actions::setUpApproval,
                )
                if (state.phoneNode) {
                    KitRow(title = stringResource(Res.string.fbNodeTitle), trailing = KitRowTrailing.Chevron, onClick = actions::openPhoneNode)
                }
            }
        }
        item("details") {
            KitDetailsFold(
                label = stringResource(Res.string.kitDetails),
                copyLabel = stringResource(Res.string.kitCopyAll),
                modifier = Modifier.padding(horizontal = gutter, vertical = Space.S2.dp),
                child = { ModuleRows(state, status) },
            )
        }
    }
}

@Composable
private fun ModuleRows(state: StatusUi, status: NodeStatus) {
    KitRowGroup {
        status.modules.filterKeys { it != "cloud" }.forEach { (name, module) -> ModuleRow(state, name, module) }
        KitRow(
            title = stringResource(Res.string.fbStatusChecked),
            supporting = status.generatedAt?.let { kitRelativeAge(it.toEpochMilliseconds(), state.nowMs) } ?: stringResource(Res.string.fbStatusUnknown),
        )
        KitRow(title = stringResource(Res.string.fbStatusHistory), supporting = historyWords(state), supportingMaxLines = 3)
        if ("index" !in status.modules) {
            KitRow(
                title = stringResource(Res.string.fbStatusSearch),
                supporting = moduleStateWords(status.indexNormal),
                state = stateTone(status.indexNormal)?.let { KitRowState(moduleStateWords(status.indexNormal), it) },
            )
        }
        KitRow(
            title = stringResource(Res.string.fbAreasLockedFolder),
            supporting =
                stringResource(
                    when (status.key) {
                        "unlocked" -> Res.string.fbKeyUnlocked
                        "locked" -> Res.string.fbKeyLocked
                        else -> Res.string.fbStateNotSetUp
                    }
                ),
        )
    }
}

@Composable
private fun ModuleRow(state: StatusUi, name: String, module: ModuleStatus) {
    val reason =
        when (module.code) {
            "not_configured" -> stringResource(Res.string.fbReasonModuleNotConfigured)
            "not_enabled" -> stringResource(Res.string.fbReasonModuleNotEnabled)
            else -> null
        }
    val since = module.since?.let { stringResource(Res.string.fbStatusSince, kitRelativeAge(it.toEpochMilliseconds(), state.nowMs)) }
    KitRow(
        title = moduleName(name),
        supporting = listOfNotNull(moduleStateWords(module.state), reason, since).joinToString(" · "),
        supportingMaxLines = 3,
        state = stateTone(module.state)?.let { KitRowState(null, it) },
    )
}

// ---- Settings -----------------------------------------------------------------------------------------------------

/** Memory settings (F: settings_screen.dart). Changes need this phone's approval; until then they are shown only. */
@Composable
public fun SettingsScreen(state: SettingsUi, actions: SettingsActions) {
    val title = stringResource(Res.string.fbSettingsTitle)
    val gutter = pageGutter()
    KitScreen(
        topBar = pageBar(title, actions::back),
        width = KitScreenWidth.Reading,
        loading = (state.loading && state.settings != null) || state.saving,
        loadingLabel = title,
        edges = KitScreenEdges.Bleed,
    ) {
        val settings = state.settings
        when {
            settings == null && state.error == null -> KitSkeletonRows()
            settings == null -> PageError(state.error, actions::reload)
            else ->
                LazyColumn(Modifier.fillMaxSize()) {
                    if (!state.canApprove) {
                        item("owner") {
                            KitNotice(
                                stringResource(Res.string.fbSettingsNeedsOwner),
                                Modifier.padding(horizontal = gutter, vertical = Space.S2.dp),
                                icon = KitIcons.info,
                                actions = listOf(KitLineAction(stringResource(Res.string.fbCareSetupApproval), actions::setUpApproval)),
                            )
                        }
                    }
                    state.failure?.let { failure ->
                        item("failure") {
                            KitNotice(
                                failureWords(failure),
                                Modifier.padding(horizontal = gutter, vertical = Space.S2.dp),
                                tone = StatusTone.Attention,
                                dismiss = KitLineAction(stringResource(Res.string.fbDismiss), actions::dismiss),
                            )
                        }
                    }
                    item("brief") {
                        val cap = settings.brief.capPerDay
                        val caps = (listOf(0L, 1L, 3L, 5L, 10L, 20L) + cap).distinct().sorted()
                        @Composable
                        fun capWords(n: Long) =
                            if (n == 0L) stringResource(Res.string.fbSettingsBriefOff)
                            else pluralStringResource(Res.plurals.fbSettingsBriefItems, n.toInt(), n.toInt())
                        KitRowGroup(label = stringResource(Res.string.fbSettingsBrief)) {
                            KitRow(
                                title = stringResource(Res.string.fbSettingsBriefCap),
                                supporting = stringResource(Res.string.fbSettingsBriefCapDetail),
                                supportingMaxLines = 2,
                                enabled = state.canApprove && !state.saving,
                                disabledReason = if (state.canApprove) null else stringResource(Res.string.fbCareNeedsOwnerShort),
                                trailing =
                                    if (state.canApprove) {
                                        KitRowTrailing.Menu(
                                            capWords(cap),
                                            caps.map { n -> KitMenuItem(capWords(n), { actions.setBriefCap(n) }, checked = n == cap) },
                                            title = stringResource(Res.string.fbSettingsBriefCap),
                                        )
                                    } else KitRowTrailing.Value(capWords(cap), chevron = false),
                            )
                            KitRow(title = stringResource(Res.string.fbSettingsBriefTime), supporting = KitBidi.ltr(settings.brief.time))
                            KitRow(title = stringResource(Res.string.fbSettingsQuietHours), supporting = KitBidi.ltr(settings.brief.quietHours))
                        }
                    }
                    item("privacy") {
                        KitRowGroup(label = stringResource(Res.string.fbSettingsPrivacy)) {
                            KitRow(
                                title = stringResource(Res.string.fbSettingsLockTimeout),
                                supporting =
                                    pluralStringResource(
                                        Res.plurals.fbSettingsMinutes,
                                        settings.lockTimeoutMinutes.toInt(),
                                        settings.lockTimeoutMinutes.toInt(),
                                    ),
                                leading = KitRowLeading.Glyph(KitIcons.locked),
                            )
                            if (state.showTriggers) {
                                KitRow(
                                    title = stringResource(Res.string.fbTriggersTitle),
                                    supporting = stringResource(Res.string.fbTriggersSettingsDetail),
                                    leading = KitRowLeading.Glyph(KitIcons.sparkle),
                                    trailing = KitRowTrailing.Chevron,
                                    onClick = actions::openTriggers,
                                )
                            }
                            KitRow(
                                title = stringResource(Res.string.fbAreasTitle),
                                supporting = stringResource(Res.string.fbSettingsAreasDetail),
                                leading = KitRowLeading.Glyph(KitIcons.privacy),
                                trailing = KitRowTrailing.Chevron,
                                onClick = actions::openAreas,
                            )
                        }
                    }
                    item("node") {
                        KitRowGroup(label = stringResource(Res.string.fbSettingsThisNode)) {
                            KitRow(title = stringResource(Res.string.fbSettingsHomeZone), supporting = KitBidi.ltr(settings.vaultZone))
                            val phoneZone = state.phoneZone
                            if (phoneZone != null && phoneZone != settings.vaultZone && state.canApprove) {
                                KitRow(
                                    title = stringResource(Res.string.fbSettingsUsePhoneZone),
                                    supporting = stringResource(Res.string.fbSettingsUsePhoneZoneDetail, KitBidi.ltr(phoneZone)),
                                    supportingMaxLines = 2,
                                    leading = KitRowLeading.Glyph(KitIcons.clock),
                                    enabled = !state.saving,
                                    onClick = actions::usePhoneZone,
                                )
                            }
                            KitRow(title = stringResource(Res.string.fbSettingsSpending), supporting = stringResource(Res.string.fbSettingsSpendingNone))
                        }
                    }
                    item("foot") { KitGroupNote(stringResource(Res.string.fbSettingsFootnote)) }
                }
        }
    }
}

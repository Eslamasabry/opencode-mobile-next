package io.github.eslamasabry.oc.feature.linux.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.eslamasabry.oc.feature.linux.manager.LinuxPackage
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxPackagesActions
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxPackagesUi
import io.github.eslamasabry.oc.feature.linux.presentation.PackagesConfirm
import io.github.eslamasabry.oc.feature.linux.presentation.PackagesNotice
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.commonRetry
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.linuxMgrJobStopBody
import io.github.eslamasabry.oc.ui.words.res.linuxMgrLoadFailedTitle
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPkgAlready
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPkgAlsoRemoved
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPkgBlockedPlan
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPkgCheck
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPkgChecking
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPkgInstall
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPkgInstallBody
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPkgInstallLabel
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPkgInstallTitle
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPkgLocked
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPkgName
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPkgNameHelper
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPkgNameInvalid
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPkgNoMatch
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPkgRemove
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPkgRemoveBody
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPkgRemoveTitle
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPkgSearch
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPkgShowAll
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPkgTitle
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPkgUnknown
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPkgUpdate
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPkgUpdateBody
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPkgUpdateDetail
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPkgUpdateTitle
import io.github.eslamasabry.oc.ui.words.res.linuxMgrProblemJob
import io.github.eslamasabry.oc.ui.words.res.workspaceDismissNotice
import org.jetbrains.compose.resources.stringResource

/** Rows shown before "Show all" when nothing is searched: a long list in one column stays light. */
private const val FIRST_ROWS = 60

/**
 * LinuxRoute.Packages (Flutter LinuxPackagesPage): the long job in place, Update system, Install a package (typed names
 * are checked before anything runs), then the installed list with search. A package Linux or the tools need has no
 * Remove and says where tools are removed. Every act asks first; one job runs at a time.
 */
@Composable
public fun LinuxPackagesScreen(state: LinuxPackagesUi, actions: LinuxPackagesActions) {
    LifecycleStartEffect(actions) {
        actions.shown(true)
        onStopOrDispose { actions.shown(false) }
    }
    val typed = rememberTextFieldState()
    var showAll by remember { mutableStateOf(false) }
    val busy = KitEnabled.Disabled(stringResource(Res.string.linuxMgrProblemJob))
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.linuxMgrPkgTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
        loading = state.loading && state.packages.isEmpty(),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            state.problem?.let { problem ->
                KitErrorNotice(
                    title = stringResource(Res.string.linuxMgrLoadFailedTitle),
                    message = stringResource(problem.words()),
                    retry = KitLineAction(stringResource(Res.string.commonRetry), actions::retry),
                )
            }
            LinuxJobPanel(state.job, actions::askStopJob, actions::dismissJob)
            KitRowGroup {
                KitRow(
                    title = stringResource(Res.string.linuxMgrPkgUpdate),
                    supporting = stringResource(Res.string.linuxMgrPkgUpdateDetail),
                    supportingMaxLines = 2,
                    enabled = state.canStartJob,
                    disabledReason = stringResource(Res.string.linuxMgrProblemJob),
                    onClick = actions::askUpdate,
                )
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S3.dp),
                verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
            ) {
                KitField(
                    typed,
                    stringResource(Res.string.linuxMgrPkgInstall),
                    placeholder = stringResource(Res.string.linuxMgrPkgName),
                    helper = stringResource(Res.string.linuxMgrPkgNameHelper),
                    error = if (state.notice == PackagesNotice.InvalidName) stringResource(Res.string.linuxMgrPkgNameInvalid) else null,
                    checking = state.checking,
                    enabled = if (state.canStartJob) KitEnabled.Enabled else busy,
                    onSubmit = { actions.checkInstall(typed.text.toString()) },
                )
                KitButton(
                    stringResource(Res.string.linuxMgrPkgCheck),
                    { actions.checkInstall(typed.text.toString()) },
                    role = KitButtonRole.Secondary,
                    enabled = if (state.canStartJob) KitEnabled.Enabled else busy,
                    working = state.checking,
                )
            }
            val dismiss = KitLineAction(stringResource(Res.string.workspaceDismissNotice), actions::dismiss)
            when (val notice = state.notice) {
                null, PackagesNotice.InvalidName -> Unit
                is PackagesNotice.AlreadyInstalled -> KitNotice(message = stringResource(Res.string.linuxMgrPkgAlready, names(listOf(notice.name))), dismiss = dismiss)
                is PackagesNotice.Unknown -> KitNotice(message = stringResource(Res.string.linuxMgrPkgUnknown, names(listOf(notice.name))), tone = StatusTone.Attention, dismiss = dismiss)
                is PackagesNotice.BlockedPlan -> KitNotice(message = stringResource(Res.string.linuxMgrPkgBlockedPlan, names(listOf(notice.needed))), tone = StatusTone.Attention, dismiss = dismiss)
                is PackagesNotice.Problem -> KitNotice(message = stringResource(notice.problem.words()), tone = StatusTone.Attention, dismiss = dismiss)
            }
            if (state.checking) KitNotice(message = stringResource(Res.string.linuxMgrPkgChecking), tone = StatusTone.Progress, liveRegion = false)
            if (state.packages.isNotEmpty()) {
                KitSearchField(
                    label = stringResource(Res.string.linuxMgrPkgSearch),
                    onSearch = actions::search,
                    resultCount = state.shown.size,
                )
                val shown = state.shown
                if (shown.isEmpty()) {
                    KitNotice(message = stringResource(Res.string.linuxMgrPkgNoMatch), liveRegion = false)
                } else {
                    val capped = state.query.isBlank() && !showAll && shown.size > FIRST_ROWS
                    KitRowGroup {
                        (if (capped) shown.take(FIRST_ROWS) else shown).forEach { PackageRow(it, state.canStartJob, actions) }
                        if (capped) {
                            KitRow(
                                title = stringResource(Res.string.linuxMgrPkgShowAll, shown.size),
                                trailing = KitRowTrailing.Chevron,
                                onClick = { showAll = true },
                            )
                        }
                    }
                }
            }
        }
    }
    val spec =
        when (val asked = state.confirm) {
            null -> null
            is PackagesConfirm.Install ->
                KitConfirmSpec(
                    title = stringResource(Res.string.linuxMgrPkgInstallTitle, names(asked.names)),
                    confirmLabel = stringResource(Res.string.linuxMgrPkgInstallLabel, names(asked.names)),
                    body = stringResource(Res.string.linuxMgrPkgInstallBody),
                )
            is PackagesConfirm.Remove ->
                KitConfirmSpec(
                    title = stringResource(Res.string.linuxMgrPkgRemoveTitle, names(listOf(asked.name))),
                    confirmLabel = stringResource(Res.string.linuxMgrPkgRemove, names(listOf(asked.name))),
                    kind = KitConfirmKind.Destructive,
                    body = stringResource(Res.string.linuxMgrPkgRemoveBody),
                    consequences =
                        if (asked.alsoRemoved.isEmpty()) emptyList() else listOf(stringResource(Res.string.linuxMgrPkgAlsoRemoved, names(asked.alsoRemoved))),
                )
            PackagesConfirm.Update ->
                KitConfirmSpec(
                    title = stringResource(Res.string.linuxMgrPkgUpdateTitle),
                    confirmLabel = stringResource(Res.string.linuxMgrPkgUpdate),
                    body = stringResource(Res.string.linuxMgrPkgUpdateBody),
                )
            is PackagesConfirm.StopJob -> {
                val (title, label) = stopJobWords(asked.kind, asked.names)
                KitConfirmSpec(title = title, confirmLabel = label, kind = KitConfirmKind.Stop, body = stringResource(Res.string.linuxMgrJobStopBody))
            }
        }
    KitConfirm(spec = spec, onConfirm = actions::confirm, onCancel = actions::dismiss)
}

@Composable
private fun PackageRow(pkg: LinuxPackage, canAct: Boolean, actions: LinuxPackagesActions) {
    val facts = "⁦${pkg.version}⁩ · ${size(pkg.sizeKb * 1024)}"
    KitRow(
        title = pkg.name,
        titleIsFileName = true,
        supporting = if (pkg.locked) "$facts · ${stringResource(Res.string.linuxMgrPkgLocked)}" else facts,
        supportingMaxLines = 3,
        trailing =
            if (!pkg.locked && canAct) {
                KitRowTrailing.Action(KitLineAction(stringResource(Res.string.linuxMgrPkgRemove, pkg.name), { actions.askRemove(pkg.name) }))
            } else {
                KitRowTrailing.None
            },
    )
}

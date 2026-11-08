package io.github.eslamasabry.oc.feature.linux.ui

import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitCheckedInputDialog
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import org.jetbrains.compose.resources.pluralStringResource
import io.github.eslamasabry.oc.ui.words.res.*
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPkgInstallDetail
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
    var installOpen by remember { mutableStateOf(false) }
    var detailName by remember { mutableStateOf<String?>(null) }
    val selected = state.packages.firstOrNull { it.name == detailName }
    LaunchedEffect(state.confirm) { if (state.confirm != null) installOpen = false }
    val busy = KitEnabled.Disabled(stringResource(Res.string.linuxMgrProblemJob))
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.linuxMgrPkgTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
                actions = listOf(KitLineAction(stringResource(Res.string.workRefresh), actions::retry, icon = KitIcons.retry,
                    enabled = !state.loading, disabledReason = stringResource(Res.string.setupNativeRefreshing))),
            )
        },
        width = KitScreenWidth.Reading,
        loading = state.checking,
        edges = KitScreenEdges.Bleed,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            if (state.loading && state.packages.isEmpty()) KitSkeletonRows()
            else {
            state.problem?.let { problem ->
                KitErrorNotice(
                    title = stringResource(Res.string.linuxMgrLoadFailedTitle),
                    message = problem.words(),
                    retry = KitLineAction(stringResource(Res.string.commonRetry), actions::retry),
                )
            }
            LinuxJobPanel(state.job, actions::askStopJob, actions::dismissJob)
            KitRowGroup(leadingTiles = true) {
                KitRow(
                    title = stringResource(Res.string.linuxMgrPkgInstall),
                    supporting = stringResource(Res.string.linuxMgrPkgInstallDetail),
                    leading = KitRowLeading.Glyph(KitIcons.download), trailing = KitRowTrailing.Chevron,
                    enabled = state.canStartJob, disabledReason = stringResource(Res.string.linuxMgrProblemJob),
                    onClick = { installOpen = true },
                )
                KitRow(
                    title = stringResource(Res.string.linuxMgrPkgUpdate),
                    leading = KitRowLeading.Glyph(KitIcons.systemDownload), trailing = KitRowTrailing.Chevron,
                    supporting = stringResource(Res.string.linuxMgrPkgUpdateDetail),
                    supportingMaxLines = 2,
                    enabled = state.canStartJob,
                    disabledReason = stringResource(Res.string.linuxMgrProblemJob),
                    onClick = actions::askUpdate,
                )
            }
            val dismiss = KitLineAction(stringResource(Res.string.workspaceDismissNotice), actions::dismiss)
            when (val notice = state.notice) {
                null, PackagesNotice.InvalidName -> Unit
                is PackagesNotice.AlreadyInstalled -> KitNotice(message = stringResource(Res.string.linuxMgrPkgAlready, names(listOf(notice.name))), dismiss = dismiss)
                is PackagesNotice.Unknown -> KitNotice(message = stringResource(Res.string.linuxMgrPkgUnknown, names(listOf(notice.name))), tone = StatusTone.Attention, dismiss = dismiss)
                is PackagesNotice.BlockedPlan -> KitNotice(message = stringResource(Res.string.linuxMgrPkgBlockedPlan, names(listOf(notice.needed))), tone = StatusTone.Attention, dismiss = dismiss)
                is PackagesNotice.Problem -> KitNotice(message = notice.problem.words(), tone = StatusTone.Attention, dismiss = dismiss)
            }
            if (state.checking) KitNotice(message = stringResource(Res.string.linuxMgrPkgChecking), tone = StatusTone.Progress, liveRegion = false)
            run {
                KitSearchField(
                    label = stringResource(Res.string.linuxMgrPkgSearch),
                    onSearch = actions::search,
                    resultCount = if (state.query.isBlank()) null else state.shown.size,
                    modifier = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
                )
                KitText(pluralStringResource(Res.plurals.linuxMgrPkgCount, state.packages.size, state.packages.size),
                    Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp), role = TypeRole.Caption)
                val shown = state.shown
                if (shown.isEmpty()) {
                    KitText(stringResource(Res.string.linuxMgrPkgNoMatch),
                        Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp), role = TypeRole.Secondary)
                } else {
                    val capped = state.query.isBlank() && !showAll && shown.size > FIRST_ROWS
                    KitRowGroup(leadingTiles = true) {
                        (if (capped) shown.take(FIRST_ROWS) else shown).forEach { PackageRow(it) { detailName = it.name } }
                        if (capped) {
                            KitRow(
                                title = stringResource(Res.string.linuxMgrPkgShowAll, shown.size),
                                leading = KitRowLeading.Glyph(KitIcons.expand),
                                trailing = KitRowTrailing.Chevron,
                                onClick = { showAll = true },
                            )
                        }
                    }
                }
            }
            }
        }
        KitCheckedInputDialog(
            visible = installOpen,
            title = stringResource(Res.string.linuxMgrPkgInstall),
            state = typed,
            fieldLabel = stringResource(Res.string.linuxMgrPkgName),
            confirmLabel = stringResource(Res.string.linuxMgrPkgCheck),
            helper = stringResource(Res.string.linuxMgrPkgNameHelper),
            placeholder = "htop", // F: lib/ui/screens/linux_manager/linux_packages_page.dart:152
            error = when (val notice = state.notice) {
                PackagesNotice.InvalidName -> stringResource(Res.string.linuxMgrPkgNameInvalid)
                is PackagesNotice.AlreadyInstalled -> stringResource(Res.string.linuxMgrPkgAlready, notice.name)
                is PackagesNotice.Unknown -> stringResource(Res.string.linuxMgrPkgUnknown, notice.name)
                is PackagesNotice.BlockedPlan -> stringResource(Res.string.linuxMgrPkgBlockedPlan, notice.needed)
                is PackagesNotice.Problem -> notice.problem.words()
                null -> null
            },
            working = state.checking,
            enabled = if (state.job.running) busy else KitEnabled.Enabled,
            onConfirm = { actions.checkInstall(typed.text.toString()) },
            onDismiss = { installOpen = false; actions.dismiss() },
        )
        KitSheet(visible = selected != null, title = selected?.name ?: stringResource(Res.string.linuxMgrPkgTitle),
            subtitle = selected?.version, icon = KitIcons.`package`, onDismissRequest = { detailName = null },
            secondary = selected?.takeUnless { it.locked }?.let { pkg ->
                KitAction(stringResource(Res.string.linuxMgrPkgRemove, pkg.name), {
                    detailName = null
                    actions.askRemove(pkg.name)
                }, icon = KitIcons.delete, destructive = true,
                    enabled = KitEnabled.of(state.canStartJob, stringResource(Res.string.linuxMgrProblemJob)))
            }) {
            selected?.let { pkg ->
                if (pkg.locked) KitText(stringResource(Res.string.linuxMgrPkgLocked))
                KitDetailsFold(stringResource(Res.string.kitDetails), stringResource(Res.string.kitCopyDetails),
                    notes = listOf(pkg.name, pkg.version, size(pkg.sizeKb * 1024))) // F: lib/ui/screens/linux_manager/linux_packages_page.dart:280-284
            }
        }
        val spec =
            when (val asked = state.confirm) {
                null -> null
                is PackagesConfirm.Install ->
                    KitConfirmSpec(
                        title = stringResource(Res.string.linuxMgrPkgInstallTitle, names(asked.names)),
                        confirmLabel = stringResource(Res.string.linuxMgrPkgInstallLabel, names(asked.names)),
                        kind = KitConfirmKind.Neutral,
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
                        kind = KitConfirmKind.Neutral,
                        body = stringResource(Res.string.linuxMgrPkgUpdateBody),
                    )
                is PackagesConfirm.StopJob -> {
                    val (title, label) = stopJobWords(asked.kind, asked.names)
                    KitConfirmSpec(title = title, confirmLabel = label, kind = KitConfirmKind.Stop, body = stringResource(Res.string.linuxMgrJobStopBody))
                }
            }
        KitConfirm(spec = spec, onConfirm = actions::confirm, onCancel = actions::dismiss)
    }
}

@Composable
private fun PackageRow(pkg: LinuxPackage, onDetails: () -> Unit) {
    val facts = listOfNotNull("\u2066${pkg.version}\u2069", pkg.sizeKb.takeIf { it > 0 }?.let { size(it * 1024) }).joinToString(" · ")
    KitRow(
        title = pkg.name,
        leading = KitRowLeading.Glyph(if (pkg.locked) KitIcons.locked else KitIcons.`package`),
        titleIsFileName = true,
        supporting = facts,
        supportingMaxLines = 2, // F: lib/ui/screens/linux_manager/linux_packages_page.dart:477
        trailing = KitRowTrailing.Chevron,
        onClick = onDetails,
    )
}

package io.github.eslamasabry.oc.feature.factsbook.ui

import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.factsbook.PhoneNodeProblem
import io.github.eslamasabry.oc.engine.factsbook.PhoneNodeState
import io.github.eslamasabry.oc.engine.factsbook.PhoneNodeStep
import io.github.eslamasabry.oc.feature.factsbook.presentation.PhoneNodeActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.PhoneNodeUi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitMonoText
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private fun stepWords(step: PhoneNodeStep): StringResource =
    when (step) {
        PhoneNodeStep.Copying -> Res.string.fbNodeStepCopying
        PhoneNodeStep.Preparing -> Res.string.fbNodeStepPreparing
        PhoneNodeStep.Starting -> Res.string.fbNodeStepStarting
        PhoneNodeStep.Pairing -> Res.string.fbNodeStepPairing
    }

private fun failureWords(problem: PhoneNodeProblem?): StringResource =
    when (problem) {
        PhoneNodeProblem.Install -> Res.string.fbNodeFailInstall
        PhoneNodeProblem.Start, null -> Res.string.fbNodeFailStart
        PhoneNodeProblem.Pair -> Res.string.fbNodeFailPair
        PhoneNodeProblem.Stop -> Res.string.fbNodeFailStop
        PhoneNodeProblem.Remove -> Res.string.fbNodeFailRemove
    }

/** The page's one state line: its mark, title and words (F: phone_node_screen.dart `switch (state)`). */
private data class NodeWords(val icon: ImageVector, val title: StringResource, val body: StringResource?)

private fun wordsOf(ui: PhoneNodeUi): NodeWords {
    val node = ui.node
    return when (node.state) {
        PhoneNodeState.Checking -> NodeWords(KitIcons.database, Res.string.fbNodeTitle, null)
        PhoneNodeState.Unsupported -> NodeWords(KitIcons.info, Res.string.fbNodeUnsupportedTitle, Res.string.fbNodeUnsupportedBody)
        PhoneNodeState.LinuxNotSetUp -> NodeWords(KitIcons.info, Res.string.fbNodeLinuxTitle, Res.string.fbNodeLinuxBody)
        PhoneNodeState.NoBuild -> NodeWords(KitIcons.info, Res.string.fbNodeNoBuildTitle, Res.string.fbNodeNoBuildBody)
        PhoneNodeState.NotInstalled -> NodeWords(KitIcons.database, Res.string.fbNodeNotInstalledTitle, Res.string.fbNodeNotInstalledBody)
        PhoneNodeState.Installing -> NodeWords(KitIcons.database, stepWords(node.step), Res.string.fbNodeNotInstalledBody)
        PhoneNodeState.Running -> NodeWords(KitIcons.checkCircle, Res.string.fbNodeRunningTitle, Res.string.fbNodeRunningBody)
        PhoneNodeState.Stopping -> NodeWords(KitIcons.database, Res.string.fbNodeStopping, null)
        PhoneNodeState.Stopped -> NodeWords(KitIcons.pause, Res.string.fbNodeStoppedTitle, Res.string.fbNodeStoppedBody)
        PhoneNodeState.Removing -> NodeWords(KitIcons.database, Res.string.fbNodeRemoving, null)
        PhoneNodeState.Failed -> NodeWords(KitIcons.info, Res.string.fbErrorTitle, failureWords(node.problem))
    }
}

/** Memory on this phone (F: phone_node_screen.dart). States in plain words; the technical reason under Details. */
@Composable
public fun PhoneNodeScreen(state: PhoneNodeUi, actions: PhoneNodeActions) {
    val node = state.node
    val gutter = LocalKitTokens.current.gutter.dp
    val working =
        node.busy || node.state in setOf(PhoneNodeState.Checking, PhoneNodeState.Installing, PhoneNodeState.Stopping, PhoneNodeState.Removing)
    val words = wordsOf(state)
    val canRun = node.state == PhoneNodeState.NotInstalled || (node.state == PhoneNodeState.Failed && node.problem == PhoneNodeProblem.Install)
    val canStart =
        node.state == PhoneNodeState.Stopped ||
            (node.state == PhoneNodeState.Failed && (node.problem == PhoneNodeProblem.Start || node.problem == PhoneNodeProblem.Pair))
    val installed = node.facts.dataExists || node.state == PhoneNodeState.Running || node.state == PhoneNodeState.Stopped
    val primary =
        when {
            canRun -> KitAction(stringResource(Res.string.fbNodeRun), actions::runHere, icon = KitIcons.database, working = working)
            canStart -> KitAction(stringResource(Res.string.fbNodeStart), actions::start, icon = KitIcons.play, working = working)
            node.state == PhoneNodeState.Running -> KitAction(stringResource(Res.string.fbNodeOpen), actions::openCapture)
            node.state == PhoneNodeState.LinuxNotSetUp -> KitAction(stringResource(Res.string.fbNodeLinuxAction), actions::setUpPhone)
            else -> null
        }
    val title = stringResource(Res.string.fbNodeTitle)
    KitSheetHost(Modifier.fillMaxSize()) {
        KitScreen(
            topBar = { KitTopBar(title = title, exit = KitTopBarExit.Back, exitLabel = stringResource(Res.string.kitTopBarBack), onExit = actions::back) },
            width = KitScreenWidth.Reading,
            loading = working,
            loadingLabel = stringResource(words.title),
            bottom =
                primary?.let { action ->
                    { KitActionBlock(Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = Space.S2.dp), primary = action) }
                },
            edges = KitScreenEdges.Bleed,
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                if (state.removed) {
                    item("removed") {
                        KitNotice(
                            stringResource(Res.string.fbNodeRemoved),
                            Modifier.padding(horizontal = gutter, vertical = Space.S2.dp),
                            icon = KitIcons.check,
                            tone = StatusTone.Ok,
                            dismiss = KitLineAction(stringResource(Res.string.fbDismiss), actions::dismiss),
                        )
                    }
                }
                item("what") {
                    Column(Modifier.padding(horizontal = gutter, vertical = Space.S2.dp), verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                        KitText(stringResource(Res.string.fbNodeWhatTitle), role = TypeRole.Label, tone = TextTone.Secondary)
                        KitText(stringResource(Res.string.fbNodeWhatBody), role = TypeRole.Secondary, tone = TextTone.Secondary)
                    }
                }
                item("state") {
                    KitStateView(
                        if (node.state == PhoneNodeState.Failed) KitStateKind.Error else KitStateKind.Calm,
                        stringResource(words.title),
                        body = words.body?.let { stringResource(it) },
                        icon = words.icon,
                    )
                }
                if (installed && node.state != PhoneNodeState.Removing && node.state != PhoneNodeState.Installing) {
                    item("address") {
                        KitRowGroup { KitRow(title = stringResource(Res.string.fbNodeAddress), supporting = stringResource(Res.string.fbNodeAddressValue)) }
                    }
                }
                if (installed && !working && node.state == PhoneNodeState.Running) {
                    item("controls") {
                        Row(Modifier.padding(horizontal = gutter, vertical = Space.S3.dp), horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                            KitButton(KitAction(stringResource(Res.string.fbNodeStop), actions::stop, icon = KitIcons.pause), role = KitButtonRole.Secondary)
                            KitButton(KitAction(stringResource(Res.string.fbNodeRestart), actions::restart, icon = KitIcons.sync), role = KitButtonRole.Secondary)
                        }
                    }
                }
                if (!working) {
                    item("details") {
                        KitDetailsFold(
                            label = stringResource(Res.string.kitDetails),
                            copyLabel = stringResource(Res.string.kitCopyAll),
                            modifier = Modifier.padding(horizontal = gutter, vertical = Space.S3.dp),
                            notes = listOfNotNull(node.failure?.takeIf { node.state == PhoneNodeState.Failed }),
                            child = { Details(state, actions, installed) },
                        )
                    }
                }
            }
        }
        KitConfirm(
            spec =
                if (!state.confirmRemove) null
                else
                    KitConfirmSpec(
                        title = stringResource(Res.string.fbNodeRemoveTitle),
                        confirmLabel = stringResource(Res.string.fbNodeRemove),
                        kind = KitConfirmKind.Destructive,
                        body = stringResource(Res.string.fbNodeRemoveBody),
                        consequences = listOf(stringResource(Res.string.fbNodeRemoveLoses1), stringResource(Res.string.fbNodeRemoveLoses2)),
                    ),
            onConfirm = actions::remove,
            onCancel = actions::cancelRemove,
        )
    }
}

@Composable
private fun Details(state: PhoneNodeUi, actions: PhoneNodeActions, installed: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        if (installed) {
            KitButton(
                KitAction(stringResource(Res.string.fbNodeRemove), actions::askRemove, icon = KitIcons.delete, destructive = true),
                role = KitButtonRole.Secondary,
            )
            val log = state.log
            if (log == null) KitButton(KitAction(stringResource(Res.string.fbNodeLog), actions::showLog), role = KitButtonRole.Tertiary)
            else KitMonoText(log.ifEmpty { "-" })
        }
        KitText(stringResource(Res.string.fbNodeOwnServerBody), role = TypeRole.Secondary, tone = TextTone.Secondary)
        KitButton(KitAction(stringResource(Res.string.fbNodeOwnServer), actions::ownServer, icon = KitIcons.link), role = KitButtonRole.Tertiary)
    }
}

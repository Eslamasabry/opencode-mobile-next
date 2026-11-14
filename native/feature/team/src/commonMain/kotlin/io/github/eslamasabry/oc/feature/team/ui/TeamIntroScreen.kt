package io.github.eslamasabry.oc.feature.team.ui

import io.github.eslamasabry.oc.ui.kit.frame.kitPageListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.setupscripts.HostScripts
import io.github.eslamasabry.oc.feature.team.presentation.TeamIntroActions
import io.github.eslamasabry.oc.feature.team.presentation.TeamIntroPrimary
import io.github.eslamasabry.oc.feature.team.presentation.TeamIntroUi
import io.github.eslamasabry.oc.feature.team.presentation.TeamServer
import io.github.eslamasabry.oc.feature.team.presentation.TeamServerKind
import io.github.eslamasabry.oc.feature.team.presentation.primary
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.state.KitCostNotice
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitCodeCopyCommand
import io.github.eslamasabry.oc.ui.words.res.teamDiscoverBatteryTitle
import io.github.eslamasabry.oc.ui.words.res.teamDiscoverComputerBody
import io.github.eslamasabry.oc.ui.words.res.teamDiscoverComputerTitle
import io.github.eslamasabry.oc.ui.words.res.teamDiscoverEntryTitle
import io.github.eslamasabry.oc.ui.words.res.teamDiscoverHowHeading
import io.github.eslamasabry.oc.ui.words.res.teamDiscoverInAppBatteryBody
import io.github.eslamasabry.oc.ui.words.res.teamDiscoverIntroBody
import io.github.eslamasabry.oc.ui.words.res.teamDiscoverNeedsPhone
import io.github.eslamasabry.oc.ui.words.res.teamDiscoverNeedsServer
import io.github.eslamasabry.oc.ui.words.res.teamDiscoverProjectBody
import io.github.eslamasabry.oc.ui.words.res.teamDiscoverProjectTitle
import io.github.eslamasabry.oc.ui.words.res.teamDiscoverSpeedBody
import io.github.eslamasabry.oc.ui.words.res.teamDiscoverSpeedTitle
import io.github.eslamasabry.oc.ui.words.res.teamDiscoverStepCheckBody
import io.github.eslamasabry.oc.ui.words.res.teamDiscoverStepCheckTitle
import io.github.eslamasabry.oc.ui.words.res.teamDiscoverStepMergeBody
import io.github.eslamasabry.oc.ui.words.res.teamDiscoverStepMergeTitle
import io.github.eslamasabry.oc.ui.words.res.teamDiscoverStepPlanBody
import io.github.eslamasabry.oc.ui.words.res.teamDiscoverStepPlanTitle
import io.github.eslamasabry.oc.ui.words.res.teamDiscoverStepWorkBody
import io.github.eslamasabry.oc.ui.words.res.teamDiscoverStepWorkTitle
import io.github.eslamasabry.oc.ui.words.res.teamIntroCostMemory
import io.github.eslamasabry.oc.ui.words.res.teamIntroCostTime
import io.github.eslamasabry.oc.ui.words.res.teamIntroCostTitle
import io.github.eslamasabry.oc.ui.words.res.teamIntroInstalledBody
import io.github.eslamasabry.oc.ui.words.res.teamIntroInstalledTitle
import io.github.eslamasabry.oc.ui.words.res.teamIntroSetUpOn
import io.github.eslamasabry.oc.ui.words.res.teamIntroSetUpPhone
import io.github.eslamasabry.oc.ui.words.res.teamIntroTurnOnPhone
import io.github.eslamasabry.oc.ui.words.res.teamUiHostGuideEnterAddress
import io.github.eslamasabry.oc.ui.words.res.teamUiHostGuideIntro
import io.github.eslamasabry.oc.ui.words.res.teamUiHostGuideStep1
import io.github.eslamasabry.oc.ui.words.res.teamUiHostGuideStep2
import io.github.eslamasabry.oc.ui.words.res.teamUiHostGuideStep3
import io.github.eslamasabry.oc.ui.words.res.teamUiHostGuideStep4
import io.github.eslamasabry.oc.ui.words.res.teamUiHostGuideTitle
import org.jetbrains.compose.resources.stringResource

/**
 * The team page while the team is off (Flutter team_intro_screen.dart, merged into the team page): what the team does
 * in four plain steps, what it needs on this kind of server, the cost before the set-up (KIT-37), and the one
 * primary in [TeamIntroBottom]. The moment the team is on, the same page shows the team.
 */
@Composable
internal fun TeamIntro(intro: TeamIntroUi, actions: TeamIntroActions) {
    val inset = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
    val server = intro.server
    LazyColumn(
        Modifier.fillMaxSize().testTag("team-intro"),
        contentPadding = PaddingValues(top = Space.S2.dp, bottom = Space.S4.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        state = kitPageListState(),
    ) {
        item(key = "intro") {
            Column(inset, verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                KitText(stringResource(Res.string.teamDiscoverEntryTitle), role = TypeRole.Title)
                KitText(stringResource(Res.string.teamDiscoverIntroBody), tone = TextTone.Secondary)
            }
        }
        item(key = "how") {
            KitRowGroup(label = stringResource(Res.string.teamDiscoverHowHeading), leadingTiles = true) {
                Fact(
                    KitIcons.checklist,
                    stringResource(Res.string.teamDiscoverStepPlanTitle),
                    stringResource(Res.string.teamDiscoverStepPlanBody),
                )
                Fact(
                    KitIcons.agent,
                    stringResource(Res.string.teamDiscoverStepWorkTitle),
                    stringResource(Res.string.teamDiscoverStepWorkBody),
                )
                Fact(
                    KitIcons.review,
                    stringResource(Res.string.teamDiscoverStepCheckTitle),
                    stringResource(Res.string.teamDiscoverStepCheckBody),
                )
                Fact(
                    KitIcons.branch,
                    stringResource(Res.string.teamDiscoverStepMergeTitle),
                    stringResource(Res.string.teamDiscoverStepMergeBody),
                )
            }
        }
        if (server != null) item(key = "needs") { Needs(server, intro.canTurnOn, inset) }
    }
    HostGuide(intro.guide, actions::closeGuide, onEnterAddress = actions::enterAddress)
}

/** What the team needs on this kind of server, then its cost before the primary (KIT-37). */
@Composable
private fun Needs(server: TeamServer, installed: Boolean, inset: Modifier) {
    val label =
        if (server.kind == TeamServerKind.Computer) stringResource(
            Res.string.teamDiscoverNeedsServer,
            server.name,
        )
        else stringResource(Res.string.teamDiscoverNeedsPhone)
    Column(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
        KitRowGroup(Modifier.testTag("team-intro-needs"), label = label, leadingTiles = true) {
            when (server.kind) {
                TeamServerKind.ThisPhone -> {
                    // The engine is bundled with the app: nothing to download, said once.
                    if (installed)
                        Fact(
                            KitIcons.checkCircle,
                            stringResource(Res.string.teamIntroInstalledTitle),
                            stringResource(Res.string.teamIntroInstalledBody),
                        )
                    Fact(
                        KitIcons.batteryWarning,
                        stringResource(Res.string.teamDiscoverBatteryTitle),
                        stringResource(Res.string.teamDiscoverInAppBatteryBody),
                    )
                    Fact(
                        KitIcons.projects,
                        stringResource(Res.string.teamDiscoverProjectTitle),
                        stringResource(Res.string.teamDiscoverProjectBody),
                    )
                }
                TeamServerKind.Computer -> {
                    Fact(
                        KitIcons.computer,
                        stringResource(Res.string.teamDiscoverComputerTitle, server.name),
                        stringResource(Res.string.teamDiscoverComputerBody),
                    )
                    Fact(
                        KitIcons.speed,
                        stringResource(Res.string.teamDiscoverSpeedTitle),
                        stringResource(Res.string.teamDiscoverSpeedBody),
                    )
                }
            }
        }
        if (server.kind == TeamServerKind.ThisPhone)
            KitCostNotice(
                listOf(
                    stringResource(Res.string.teamIntroCostTime),
                    stringResource(Res.string.teamIntroCostMemory),
                ),
                inset.testTag("team-intro-cost"),
                title = stringResource(Res.string.teamIntroCostTitle),
            )
    }
}

/** The off page's one primary, named after what it sets up (Flutter `_TeamIntroScreenState.build`). */
@Composable
internal fun TeamIntroBottom(intro: TeamIntroUi, actions: TeamIntroActions) {
    val primary =
        when (intro.primary()) {
            null -> null
            TeamIntroPrimary.TurnOnPhone ->
                KitAction(stringResource(Res.string.teamIntroTurnOnPhone), actions::turnOn, icon = KitIcons.play)
            TeamIntroPrimary.SetUpPhone -> KitAction(
                stringResource(Res.string.teamIntroSetUpPhone),
                actions::setUpOnPhone,
            )
            TeamIntroPrimary.SetUpComputer ->
                KitAction(stringResource(Res.string.teamIntroSetUpOn, intro.server?.name.orEmpty()), actions::openGuide)
        }
    KitActionBlock(
        Modifier
            .padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S2.dp)
            .testTag("team-intro-actions"),
        primary = primary,
    )
}

@Composable
private fun Fact(icon: ImageVector, title: String, body: String) {
    KitRow(title = title, supporting = body, supportingMaxLines = 3, leading = KitRowLeading.Glyph(icon))
}

/**
 * Flutter `showTeamHostGuideSheet`: Gas City on the computer in four steps, each with its command (the pinned,
 * checksummed front from :engine:setupscripts; nothing is ever piped into a shell).
 */
@Composable
internal fun HostGuide(visible: Boolean, onDismiss: () -> Unit, onEnterAddress: (() -> Unit)? = null) {
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    val steps =
        listOf(
            stringResource(Res.string.teamUiHostGuideStep1) to HostScripts.TEAM_CHECK_TOOLS,
            stringResource(Res.string.teamUiHostGuideStep2) to TEAM_CREATE_CITY,
            stringResource(Res.string.teamUiHostGuideStep3) to TEAM_START,
            stringResource(Res.string.teamUiHostGuideStep4) to HostScripts.teamFront,
        )
    // The next step is the team's address (Flutter `enterAddress`): its primary closes the guide and opens the address form.
    val enter = onEnterAddress?.let { KitAction(stringResource(Res.string.teamUiHostGuideEnterAddress), it, icon = KitIcons.edit) }
    KitSheet(visible = visible, onDismissRequest = onDismiss, title = stringResource(Res.string.teamUiHostGuideTitle), primary = enter) {
        Column(Modifier.testTag("team-host-guide"), verticalArrangement = Arrangement.spacedBy(Space.S4.dp)) {
            KitText(
                stringResource(Res.string.teamUiHostGuideIntro),
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
            )
            steps.forEachIndexed { i, (step, command) ->
                Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                    KitText("${i + 1}. $step")
                    KitCodeBlock(
                        command,
                        KitCodeKind.Command,
                        Modifier.testTag("team-host-guide-step-${i + 1}"),
                        copyLabel = if ('\n' in command) stringResource(Res.string.kitCodeCopyCommand) else null,
                        onCopy = { clipboard.setText(AnnotatedString(it)) },
                    )
                }
            }
        }
    }
}

/** Flutter setup_commands.dart `HostScripts.teamCreateCity`: the city from the guide's file, then the project. */
internal const val TEAM_CREATE_CITY: String =
    "gc init --file ./city.toml --name myteam --no-start city &&\n" +
        "cd city &&\n" +
        "gc rig add ~/code/myproject --name myproject &&\n" +
        "gc import install"

/** Flutter `HostScripts.teamStart`: start the supervisor and ask whether the city answers. */
internal const val TEAM_START: String =
    "gc start &&\n" + "curl -s http://127.0.0.1:8372/v0/city/myteam/health"

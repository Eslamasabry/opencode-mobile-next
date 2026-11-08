package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.settings.presentation.GuideUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitBulletLine
import io.github.eslamasabry.oc.ui.kit.row.KitExpandRow
import io.github.eslamasabry.oc.ui.kit.row.KitNumberedStep
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** The commands the guide hands over (F: lib/ui/setup_commands.dart:11,14). Commands, not copy. */
internal object GuideCommands {
    const val PAIR: String = "opencode2 pair"
    const val SERVE: String = "opencode2 serve --hostname 127.0.0.1 --port 4096"
}

/**
 * Help › Guide (F: lib/ui/screens/guide_screen.dart): the one story a first-time person needs (run
 * `opencode2 pair`, scan or paste, start talking), this phone's own path, the demo, and every other
 * route folded under Advanced. Step 2 acts as well as explains: its button opens Add server itself.
 * Termux is retired in this app (the built-in phone server replaced it), so the Termux section and
 * its line in Advanced are not drawn; the phone path row leads to phone setup instead.
 */
@Composable
internal fun ColumnScope.GuideBody(guide: GuideUi, actions: SettingsActions) {
    val gutter = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
    val copy = stringResource(Res.string.handoffCopyCommand)
    Column(gutter, verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        KitText(
            stringResource(Res.string.e7SharedThreeStepsToYourFirstSession),
            Modifier.semantics { heading() },
            role = TypeRole.Title,
        )
        KitText(
            stringResource(Res.string.e7SharedOpenCodeRunsOnYourComputerThisApp),
            tone = TextTone.Secondary,
        )
    }
    KitRowGroup(Modifier.testTag("guide-steps")) {
        KitNumberedStep(
            1,
            stringResource(Res.string.e7SharedOnYourComputerRunOneCommand),
            stringResource(Res.string.e7SharedDetail307, 1),
            body = stringResource(Res.string.e7SharedInATerminalOnTheComputerWhere),
        ) {
            KitCodeBlock(
                GuideCommands.PAIR,
                KitCodeKind.Command,
                Modifier.testTag("guide-pair-command"),
                copyLabel = copy,
                showWrapToggle = false,
            )
            KitText(
                stringResource(Res.string.e7SharedItStartsTheServerAndPrintsA),
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
            )
        }
        KitNumberedStep(
            2,
            stringResource(
                if (guide.canScan) Res.string.e7SharedScanTheQROrPasteTheCode
                else Res.string.e7SharedPasteTheCodeInThisApp
            ),
            stringResource(Res.string.e7SharedDetail307, 2),
            body =
                stringResource(
                    if (guide.canScan) Res.string.guideStepTwoScan else Res.string.guideStepTwoPaste
                ),
            below =
                if (guide.addServer) {
                    {
                        KitButton(
                            stringResource(Res.string.e7SetupAddServer),
                            { actions.onOutside(SettingsOutside.AddServer) },
                            Modifier.testTag("guide-add-server"),
                            role = KitButtonRole.Secondary,
                            icon = KitIcons.add,
                            expand = false,
                        )
                    }
                } else null,
        )
        KitNumberedStep(
            3,
            stringResource(Res.string.e7SharedStartTalking),
            stringResource(Res.string.e7SharedDetail307, 3),
            body = stringResource(Res.string.e7SharedPickAProjectAndSendYourFirst),
        )
    }
    if (guide.phonePath) {
        KitRowGroup {
            KitRow(
                title = stringResource(Res.string.guidePhonePathTitle),
                modifier = Modifier.testTag("guide-phone-path"),
                titleMaxLines = 2,
                supporting = stringResource(Res.string.guidePhonePathBody),
                supportingMaxLines = 2,
                leading = KitRowLeading.Glyph(KitIcons.phone),
                trailing = KitRowTrailing.Chevron,
                onClick = { actions.onOutside(SettingsOutside.PhoneSetup) },
            )
        }
    }
    // Not ready to connect anything yet: the offline demo, still one tap away (F: guide_screen.dart:128).
    if (guide.demo) {
        KitRowGroup {
            KitRow(
                title = stringResource(Res.string.settingsTryDemo),
                modifier = Modifier.testTag("settings-try-demo"),
                supporting = stringResource(Res.string.demoScreenSimulated),
                supportingMaxLines = 2,
                leading = KitRowLeading.Glyph(KitIcons.play),
                trailing = KitRowTrailing.Chevron,
                onClick = { actions.onOutside(SettingsOutside.Demo) },
            )
        }
    }
    var advanced by rememberSaveable { mutableStateOf(false) }
    KitRowGroup {
        KitExpandRow(
            title = stringResource(Res.string.e7SharedAdvanced),
            expanded = advanced,
            modifier = Modifier.testTag("guide-advanced"),
            supporting = stringResource(Res.string.e7SharedHTTPSSSHTunnelsOlderServers),
            onExpandedChange = { advanced = it },
            leading = KitIcons.settingsAdvanced,
            inset = true,
            titleMaxLines = 1,
            supportingMaxLines = 2,
            bare = false,
        ) {
            Column(
                Modifier.padding(Space.S4.dp),
                verticalArrangement = Arrangement.spacedBy(Space.S5.dp),
            ) {
                GuideSection(stringResource(Res.string.e7SharedReachAServerOverHTTPSOrA)) {
                    KitText(stringResource(Res.string.e7SharedPairingWorksWhenTheAddressTheServer))
                    KitNotice(
                        stringResource(Res.string.connectionHelpGuideTip),
                        icon = KitIcons.idea,
                        liveRegion = false,
                    )
                }
                GuideSection(stringResource(Res.string.e7SharedOlderServersWithoutPairing)) {
                    KitText(stringResource(Res.string.e7SharedServersStartedWithOpencodeServeDoNot))
                    KitCodeBlock(
                        GuideCommands.SERVE,
                        KitCodeKind.Command,
                        copyLabel = copy,
                        showWrapToggle = false,
                    )
                    KitText(stringResource(Res.string.e7SharedThenAddTheServerManuallyWithUsername))
                }
                GuideSection(stringResource(Res.string.e7SharedSecurityNotes)) {
                    KitBulletLine(
                        stringResource(Res.string.e7SharedAlwaysSetOPENCODESERVERPASSWORDWhenBinding)
                    )
                    KitBulletLine(
                        stringResource(
                            if (guide.androidKeystore)
                                Res.string.e7SharedPasswordsAreStoredInTheAndroidKeystore
                            else Res.string.platformSecureStorageGuide
                        )
                    )
                    KitBulletLine(stringResource(Res.string.e7SharedTheServerCanExecuteCommandsOnIts))
                }
            }
        }
    }
}

@Composable
private fun GuideSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        KitText(title, Modifier.semantics { heading() }, role = TypeRole.Label)
        content()
    }
}

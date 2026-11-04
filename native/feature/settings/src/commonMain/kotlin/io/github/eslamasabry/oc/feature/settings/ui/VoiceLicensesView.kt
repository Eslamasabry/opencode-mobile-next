package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.feature.settings.presentation.LicenseUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.settings.presentation.VoiceLicenseComponent
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerAction
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerFrame
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerTextBody
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.commonRetry
import io.github.eslamasabry.oc.ui.words.res.kitViewerLoadFailed
import io.github.eslamasabry.oc.ui.words.res.reportProblemCopyShort
import io.github.eslamasabry.oc.ui.words.res.voiceNoticesIntro
import io.github.eslamasabry.oc.ui.words.res.voiceNoticesMadeBy
import io.github.eslamasabry.oc.ui.words.res.voiceNoticesOpenWebsite
import io.github.eslamasabry.oc.ui.words.res.voiceNoticesTitle
import io.github.eslamasabry.oc.ui.words.res.voiceNoticesWhisper
import org.jetbrains.compose.resources.stringResource

/** About's bundled voice notices, reachable even while offline. */
@Composable
internal fun VoiceLicenseRows(actions: SettingsActions) {
    KitRowGroup(
        label = stringResource(Res.string.voiceNoticesTitle),
        modifier = Modifier.testTag("about-voice-licences"),
    ) {
        for (component in VoiceLicenseComponent.entries) {
            KitRow(
                title = licenseName(component),
                supporting =
                    stringResource(
                        Res.string.voiceNoticesMadeBy,
                        component.maker,
                        component.license,
                    ),
                supportingMaxLines = 2,
                trailing = KitRowTrailing.Chevron,
                modifier = Modifier.testTag("voice-notice-${component.id}"),
                onClick = { actions.onVoiceLicense(component.id) },
            )
        }
    }
    KitGroupNote(stringResource(Res.string.voiceNoticesIntro))
}

@Composable
internal fun VoiceLicenseScreen(id: String, state: SettingsUi, actions: SettingsActions) {
    val component = VoiceLicenseComponent.entries.firstOrNull { it.id == id } ?: return
    val name = licenseName(component)
    val text = (state.license as? LicenseUi.Ready)?.text
    KitScreen(page = true, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            KitViewerFrame(
                name = name,
                path = component.license,
                modifier = Modifier.weight(1f).testTag("voice-notice-viewer-$id"),
                primary =
                    KitViewerAction(
                        stringResource(Res.string.voiceNoticesOpenWebsite, name),
                        actions::onLicenseWebsite,
                        "voice-notice-website-$id",
                    ),
                secondary =
                    if (text != null)
                        KitViewerAction(
                            stringResource(Res.string.reportProblemCopyShort),
                            actions::onCopyLicense,
                        )
                    else null,
                onClose = { actions.onBack() },
            ) {
                when (state.license) {
                    is LicenseUi.Ready -> KitViewerTextBody(state.license.text)
                    LicenseUi.Failed ->
                        KitStateView(
                            kind = KitStateKind.Error,
                            title = stringResource(Res.string.kitViewerLoadFailed, name),
                            primary =
                                KitAction(
                                    stringResource(Res.string.commonRetry),
                                    actions::onRetryLicense,
                                ),
                        )
                    null,
                    LicenseUi.Loading -> KitStateView(kind = KitStateKind.Loading, title = name)
                }
            }
            state.note?.let { KitNotice(noteWords(it)) }
        }
    }
}

@Composable
private fun licenseName(component: VoiceLicenseComponent): String =
    component.componentName ?: stringResource(Res.string.voiceNoticesWhisper)

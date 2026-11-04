package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashReportError
import io.github.eslamasabry.oc.feature.settings.presentation.CrashUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** Visible consent, erase, preview and share controls; there is no hidden gesture. */
@Composable
internal fun ColumnScope.CrashBody(state: CrashUi, actions: SettingsActions) {
    val gutter = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
    KitRowGroup(label = stringResource(Res.string.secCrashTitle)) {
        KitSwitchRow(
            title = stringResource(Res.string.secCrashCapture),
            checked = state.enabled,
            supporting = stringResource(Res.string.secCrashPrivacy),
            onCheckedChange = actions::onCrashCapture,
        )
    }
    Box(gutter) {
        KitActionBlock(
            primary =
                KitAction(stringResource(Res.string.secCrashPreview), actions::onPreviewCrashes),
            secondary =
                KitAction(stringResource(Res.string.secCrashClear), actions::onClearCrashes),
        )
    }
    Box(gutter) {
        KitActionBlock(
            primary =
                KitAction(
                    stringResource(Res.string.secHistoryRefresh),
                    actions::onRefreshExitHistory,
                )
        )
    }
    state.history?.let { history ->
        if (history.unavailable) KitGroupNote(stringResource(Res.string.secHistoryUnavailable))
        KitRowGroup(label = stringResource(Res.string.secAppExits)) {
            if (history.app.isEmpty()) KitGroupNote(stringResource(Res.string.secHistoryEmpty))
            history.app.forEach { entry ->
                KitGroupNote(stringResource(Res.string.secHistoryEntry, entry.at, entry.code))
            }
        }
        KitRowGroup(label = stringResource(Res.string.secHelperExits)) {
            if (history.helpers.isEmpty()) KitGroupNote(stringResource(Res.string.secHistoryEmpty))
            history.helpers.forEach { entry ->
                KitGroupNote(stringResource(Res.string.secHistoryEntry, entry.at, entry.code))
            }
        }
    }
    val error = state.error
    if (error != null)
        KitGroupNote(
            stringResource(
                when (error) {
                    CrashReportError.CaptureDisabled -> Res.string.secCrashDisabled
                    CrashReportError.NoCapture -> Res.string.secCrashEmpty
                    CrashReportError.StalePreview -> Res.string.secCrashStale
                    CrashReportError.ShareUnavailable -> Res.string.secCrashShareUnavailable
                    CrashReportError.Busy -> Res.string.secCrashBusy
                    CrashReportError.Unavailable,
                    CrashReportError.StorageFailed -> Res.string.secCrashUnavailable
                }
            )
        )
    state.preview?.let { preview ->
        Box(gutter) { KitText(preview.text, role = TypeRole.Mono) }
        Box(gutter) {
            KitActionBlock(
                primary =
                    KitAction(
                        stringResource(Res.string.diagnosticsReportShare),
                        actions::onShareCrashes,
                    )
            )
        }
    }
}

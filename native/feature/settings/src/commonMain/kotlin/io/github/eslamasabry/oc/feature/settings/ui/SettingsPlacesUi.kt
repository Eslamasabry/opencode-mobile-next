package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPlaceId
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.aiSetupEntryDetail
import io.github.eslamasabry.oc.ui.words.res.aiSetupTitle
import io.github.eslamasabry.oc.ui.words.res.discoverNotifyBackgroundAliases
import io.github.eslamasabry.oc.ui.words.res.discoverNotifyQuietAliases
import io.github.eslamasabry.oc.ui.words.res.discoverToolsAliases
import io.github.eslamasabry.oc.ui.words.res.e7SettingsDetailUi25
import io.github.eslamasabry.oc.ui.words.res.e7SettingsUi25
import io.github.eslamasabry.oc.ui.words.res.libraryModelsAgentsTitle
import io.github.eslamasabry.oc.ui.words.res.linuxMgrTitle
import io.github.eslamasabry.oc.ui.words.res.linuxSvcTitle
import io.github.eslamasabry.oc.ui.words.res.monitorBackgroundChecks
import io.github.eslamasabry.oc.ui.words.res.monitorQuiet
import io.github.eslamasabry.oc.ui.words.res.runningPhoneTitle
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearchModelsAliases
import io.github.eslamasabry.oc.ui.words.res.settingsParityBackgroundAliases
import io.github.eslamasabry.oc.ui.words.res.settingsParityRunningAliases
import io.github.eslamasabry.oc.ui.words.res.settingsParityServicesAliases
import io.github.eslamasabry.oc.ui.words.res.textSizeTitle
import org.jetbrains.compose.resources.stringResource

/** A place's own name (Flutter's inside-settings entry titles). */
@Composable
internal fun placeWords(place: SettingsPlaceId): String =
    stringResource(
        when (place) {
            SettingsPlaceId.BackgroundChecks -> Res.string.monitorBackgroundChecks
            SettingsPlaceId.QuietHours -> Res.string.monitorQuiet
            SettingsPlaceId.TextSize -> Res.string.textSizeTitle
            SettingsPlaceId.Models -> Res.string.libraryModelsAgentsTitle
            SettingsPlaceId.CodingTools -> Res.string.e7SettingsDetailUi25
            SettingsPlaceId.RunningOnPhone -> Res.string.runningPhoneTitle
            SettingsPlaceId.LinuxServices -> Res.string.linuxSvcTitle
            SettingsPlaceId.AiSetup -> Res.string.aiSetupTitle
        },
    )

/** A place's acceptance words (its parent row is shown beside it, not matched, so a row's name finds the row alone). */
@Composable
internal fun placeAliases(place: SettingsPlaceId): String {
    val own =
        when (place) {
            SettingsPlaceId.BackgroundChecks ->
                stringResource(Res.string.e7SettingsUi25) + " " + stringResource(Res.string.discoverNotifyBackgroundAliases) +
                    " " + stringResource(Res.string.settingsParityBackgroundAliases)
            SettingsPlaceId.QuietHours -> stringResource(Res.string.discoverNotifyQuietAliases)
            SettingsPlaceId.TextSize -> ""
            SettingsPlaceId.Models -> stringResource(Res.string.settingsHubSearchModelsAliases)
            SettingsPlaceId.CodingTools -> stringResource(Res.string.discoverToolsAliases)
            SettingsPlaceId.RunningOnPhone -> stringResource(Res.string.settingsParityRunningAliases)
            SettingsPlaceId.LinuxServices ->
                stringResource(Res.string.linuxMgrTitle) + " " + stringResource(Res.string.settingsParityServicesAliases)
            SettingsPlaceId.AiSetup -> stringResource(Res.string.aiSetupEntryDetail)
        }
    return own
}

/**
 * The anchor a search result or a deep link lands on: tagged with [tag], and brought into view once when [focus] names
 * it. A plain scrolling helper (no drawn part), so pages stay kit-only.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.settingsAnchor(tag: String, focus: String?): Modifier {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(focus, tag) { if (focus == tag) requester.bringIntoView() }
    return this.testTag(tag).bringIntoViewRequester(requester)
}

package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.analytics.CollectedEvent
import io.github.eslamasabry.oc.engine.analytics.DetailedConsent
import io.github.eslamasabry.oc.engine.analytics.SendingState
import io.github.eslamasabry.oc.engine.analytics.WhatWeCollect
import io.github.eslamasabry.oc.feature.settings.presentation.PrivacyUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.privacyPolicyTitle
import io.github.eslamasabry.oc.ui.words.res.settingsV3Off
import io.github.eslamasabry.oc.ui.words.res.settingsV3On
import org.jetbrains.compose.resources.stringResource

/**
 * I08's analytics rows (plan 16 §5.8): "What we collect" with the sending state, the DETAILED switch (absent while
 * nothing is sent: there is nothing to opt into), and the privacy policy. Unsent work (drafts, queue) is S4-SETTINGS'.
 */
@Composable
internal fun ColumnScope.PrivacyBody(privacy: PrivacyUi?, actions: SettingsActions) {
    if (privacy != null) {
        KitRowGroup {
            KitRow(
                title = PendingWords.WHAT_WE_COLLECT,
                modifier = Modifier.testTag("privacy-what-we-collect"),
                supporting = sendingLine(privacy.collect),
                supportingMaxLines = 3,
                trailing = KitRowTrailing.Chevron,
                onClick = { actions.onOpen(SettingsPage.WhatWeCollect) },
            )
            DetailedSwitch(privacy, actions)
        }
    }
    KitRowGroup {
        KitRow(
            title = stringResource(Res.string.privacyPolicyTitle),
            trailing = KitRowTrailing.Chevron,
            onClick = { actions.onOutside(SettingsOutside.PrivacyPolicy) },
        )
    }
}

/**
 * Plan 18 §6: generated from `WhatWeCollectSource`, so it cannot drift from what the code can send: the sending state,
 * the switch, every BASIC and DETAILED event with every value it can take, the envelope, the never-collected list
 * and the id rotation.
 */
@Composable
internal fun ColumnScope.WhatWeCollectBody(privacy: PrivacyUi, actions: SettingsActions) {
    val c = privacy.collect
    KitNotice(sendingLine(c), Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp), liveRegion = false)
    if (c.detailedSwitchShown) KitRowGroup { DetailedSwitch(privacy, actions) }
    KitRowGroup(label = PendingWords.BASIC_LABEL, note = PendingWords.BASIC_NOTE) {
        c.basicEvents.forEach { EventRow(it, showState = false) }
    }
    KitRowGroup(label = PendingWords.DETAILED_LABEL) {
        c.detailedEvents.forEach { EventRow(it, showState = true) }
    }
    KitRowGroup(label = PendingWords.ENVELOPE_LABEL) {
        c.envelope.forEach { KitRow(title = PendingWords.envelope(it)) }
    }
    KitRowGroup(label = PendingWords.NEVER_LABEL) {
        c.never.forEach { KitRow(title = PendingWords.never(it)) }
    }
    KitGroupNote(
        PendingWords.IDS_NOTE
            .replace("%1\$d", c.installIdRotationDays.toString())
            .replace("%2\$d", c.sessionIdleMinutes.toString()),
    )
}

@Composable
private fun DetailedSwitch(privacy: PrivacyUi, actions: SettingsActions) {
    if (!privacy.collect.detailedSwitchShown) return
    KitSwitchRow(
        title = PendingWords.DETAILED_TITLE,
        checked = privacy.collect.detailed == DetailedConsent.Granted,
        modifier = Modifier.testTag("privacy-detailed-usage"),
        supporting = PendingWords.DETAILED_BODY,
        onCheckedChange = actions::onDetailedUsage,
    )
}

@Composable
private fun EventRow(event: CollectedEvent, showState: Boolean) {
    val values = event.props.filter { it.values.isNotEmpty() }.joinToString(" · ") { it.values.joinToString(", ") }
    KitRow(
        title = PendingWords.events[event.name] ?: event.name,
        supporting = values.ifBlank { null },
        supportingMaxLines = 3,
        trailing =
            if (showState) KitRowTrailing.Value(stringResource(if (event.active) Res.string.settingsV3On else Res.string.settingsV3Off), chevron = false)
            else KitRowTrailing.None,
    )
}

private fun sendingLine(c: WhatWeCollect): String =
    when (c.sending) {
        SendingState.NotSentInThisVersion -> PendingWords.NOT_SENT_YET
        SendingState.LocalDebugOnly -> PendingWords.LOCAL_ONLY
        SendingState.Sending -> PendingWords.SENT_TO.replace("%1\$s", c.destinationHost.orEmpty())
    }

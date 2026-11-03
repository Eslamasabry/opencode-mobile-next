package io.github.eslamasabry.oc.feature.about.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.about.presentation.TipEntry
import io.github.eslamasabry.oc.feature.about.presentation.TipsActions
import io.github.eslamasabry.oc.feature.about.presentation.TipsRegistry
import io.github.eslamasabry.oc.feature.about.presentation.TipsUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.teaching.KitTip
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.discoverShowTipsAgain
import io.github.eslamasabry.oc.ui.words.res.discoverShowTipsDone
import io.github.eslamasabry.oc.ui.words.res.discoverShowTipsSubtitle
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.tipHoldMic
import io.github.eslamasabry.oc.ui.words.res.tipHoldMicTitle
import io.github.eslamasabry.oc.ui.words.res.tipSendLater
import io.github.eslamasabry.oc.ui.words.res.tipSendLaterTitle
import io.github.eslamasabry.oc.ui.words.res.tipStopAfterStep
import io.github.eslamasabry.oc.ui.words.res.tipStopAfterStepTitle
import io.github.eslamasabry.oc.ui.words.res.tipsIntro
import io.github.eslamasabry.oc.ui.words.res.tipsShowMe
import io.github.eslamasabry.oc.ui.words.res.tipsTipsLabel
import io.github.eslamasabry.oc.ui.words.res.tipsTitle
import io.github.eslamasabry.oc.ui.words.res.tipsToursLabel
import io.github.eslamasabry.oc.ui.words.res.tourMakeItYoursOffer
import io.github.eslamasabry.oc.ui.words.res.tourMakeItYoursTitle
import io.github.eslamasabry.oc.ui.words.res.whatsNewRowDetail
import io.github.eslamasabry.oc.ui.words.res.whatsNewTipsTitle
import org.jetbrains.compose.resources.stringResource

/**
 * Help › Tips (Flutter `TipsScreen`, docs/ux-system/teaching.md): the intro, every tip, every mini tutorial ("Make it
 * yours" among them), each with Show me, and Show tips again. A replayed tip shows on its own row and fades by itself.
 */
@Composable
public fun TipsScreen(state: TipsUi, actions: TipsActions) {
    val gutter = LocalKitTokens.current.gutter.dp
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.tipsTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::onBack,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            KitText(stringResource(Res.string.tipsIntro), KitTextRole.Supporting, Modifier.padding(horizontal = gutter))
            KitSectionLabel(stringResource(Res.string.tipsTipsLabel))
            KitRowGroup { state.tips.forEach { TipRow(it, state, actions) } }
            KitSectionLabel(stringResource(Res.string.tipsToursLabel))
            KitRowGroup { state.tours.forEach { TipRow(it, state, actions) } }
            KitRowGroup {
                KitRow(
                    title = stringResource(Res.string.discoverShowTipsAgain),
                    supporting = stringResource(if (state.resetDone) Res.string.discoverShowTipsDone else Res.string.discoverShowTipsSubtitle),
                    supportingMaxLines = 2,
                    onClick = actions::onShowTipsAgain,
                )
            }
        }
    }
}

@Composable
private fun TipRow(entry: TipEntry, state: TipsUi, actions: TipsActions) {
    val (title, sentence) = tipWords(entry.id) ?: return
    val row: @Composable () -> Unit = {
        KitRow(
            title = title,
            supportingMaxLines = 3,
            trailing = KitRowTrailing.Action(KitLineAction(stringResource(Res.string.tipsShowMe), { actions.onShowMe(entry.id) })),
        )
    }
    // A tip replays in place; a tutorial that this build cannot run shows its offer the same way.
    KitTip(
        message = sentence,
        visible = state.replaying == entry.id,
        onDismissed = { actions.onReplayDismissed(entry.id) },
        anchor = row,
    )
}

/** Title and sentence of each registered entry (the Flutter ARB keys, now `:ui:words`). */
@Composable
private fun tipWords(id: String): Pair<String, String>? =
    when (id) {
        TipsRegistry.STOP_AFTER_STEP -> stringResource(Res.string.tipStopAfterStepTitle) to stringResource(Res.string.tipStopAfterStep)
        TipsRegistry.SEND_LATER -> stringResource(Res.string.tipSendLaterTitle) to stringResource(Res.string.tipSendLater)
        TipsRegistry.HOLD_MIC -> stringResource(Res.string.tipHoldMicTitle) to stringResource(Res.string.tipHoldMic)
        TipsRegistry.MAKE_IT_YOURS -> stringResource(Res.string.tourMakeItYoursTitle) to stringResource(Res.string.tourMakeItYoursOffer)
        TipsRegistry.WHATS_NEW -> stringResource(Res.string.whatsNewTipsTitle) to stringResource(Res.string.whatsNewRowDetail)
        else -> null
    }

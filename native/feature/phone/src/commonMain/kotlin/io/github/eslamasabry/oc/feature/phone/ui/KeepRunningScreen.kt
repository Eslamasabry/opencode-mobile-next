package io.github.eslamasabry.oc.feature.phone.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.eslamasabry.oc.feature.phone.presentation.KeepRunningActions
import io.github.eslamasabry.oc.feature.phone.presentation.KeepRunningUi
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneBudgetActions
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.fullSpeedSetting
import io.github.eslamasabry.oc.ui.words.res.usageSaveFailed
import io.github.eslamasabry.oc.ui.words.res.fullSpeedSettingDetail
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.keepRunningAllSetBody
import io.github.eslamasabry.oc.ui.words.res.keepRunningAllSetTitle
import io.github.eslamasabry.oc.ui.words.res.keepRunningDailyLimit
import io.github.eslamasabry.oc.ui.words.res.keepRunningFootnote
import io.github.eslamasabry.oc.ui.words.res.keepRunningIntro
import io.github.eslamasabry.oc.ui.words.res.keepRunningOpen
import io.github.eslamasabry.oc.ui.words.res.keepRunningOpenFailed
import io.github.eslamasabry.oc.ui.words.res.keepRunningSwipeWarning
import io.github.eslamasabry.oc.ui.words.res.keepRunningTitle
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import org.jetbrains.compose.resources.stringResource

/**
 * PhoneRoute.KeepRunning (plan 07 F15): one list of steps ordered by what is left ("You're set" once nothing checkable
 * is left), the swipe warning on phones that close a swiped app, and Android's own daily limit stated plainly. Each
 * open row says "Open" because it opens Android's screen, not a page of this app. Kit parts only (K1).
 */
@Composable
public fun KeepRunningScreen(state: KeepRunningUi, actions: KeepRunningActions) {
    // Back from a Settings screen the battery switch may have changed: read again on every resume.
    LifecycleResumeEffect(actions) {
        actions.resumed()
        onPauseOrDispose { }
    }
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.keepRunningTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
        loading = !state.loaded,
    ) {
        if (!state.loaded) return@KitScreen
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            if (state.allSet) {
                KitNotice(
                    title = stringResource(Res.string.keepRunningAllSetTitle),
                    message = stringResource(Res.string.keepRunningAllSetBody),
                    tone = StatusTone.Ok,
                    liveRegion = false,
                )
            }
            if (state.swipeWarning) {
                KitNotice(message = stringResource(Res.string.keepRunningSwipeWarning), tone = StatusTone.Attention, liveRegion = false)
            }
            KitGroupNote(stringResource(Res.string.keepRunningIntro))
            KitRowGroup(label = stringResource(Res.string.keepRunningTitle)) {
                state.steps.forEach { step ->
                    val (title, detail) = step.kind.words(state.maker, step.done)
                    val opens = step.opens != null
                    KitRow(
                        title = title,
                        supporting = detail,
                        supportingMaxLines = 4,
                        trailing = if (opens) KitRowTrailing.Value(stringResource(Res.string.keepRunningOpen)) else KitRowTrailing.None,
                        onClick = if (opens) ({ actions.open(step.kind) }) else null,
                    )
                }
            }
            if (state.speedAvailable && actions is PhoneBudgetActions) {
                KitSwitchRow(
                    title = stringResource(Res.string.fullSpeedSetting),
                    supporting = stringResource(Res.string.fullSpeedSettingDetail),
                    checked = state.fullSpeed,
                    enabled = if (state.savingSpeed) KitEnabled.Disabled(stringResource(Res.string.fullSpeedSetting)) else KitEnabled.Enabled,
                    onCheckedChange = actions::setFullSpeed,
                )
            }
            if (state.speedSaveFailed) KitNotice(message = stringResource(Res.string.usageSaveFailed))
            if (state.openFailed) KitNotice(message = stringResource(Res.string.keepRunningOpenFailed))
            if (state.dailyLimit) KitGroupNote(stringResource(Res.string.keepRunningDailyLimit))
            KitGroupNote(stringResource(Res.string.keepRunningFootnote))
        }
    }
}

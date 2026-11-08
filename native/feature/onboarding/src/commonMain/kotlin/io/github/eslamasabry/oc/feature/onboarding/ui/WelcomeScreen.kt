package io.github.eslamasabry.oc.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.eslamasabry.oc.feature.onboarding.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceCard
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceCards
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitHeroQuestion
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.words.res.*
import kotlin.math.max
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
public fun WelcomeScreen(actions: WelcomeActions) {
    WelcomeScreen(WelcomeUi(), actions)
}

/**
 * First run asks the one real question once (Flutter `_WelcomeView`, vision 2026-10-20 concept A): "Where should your
 * agent run?", a card for this phone with what setting up costs, a card for a computer, and one main button that names
 * the chosen answer. The demo and phone setup's own page are quiet text actions under the cards. The choice lives in
 * the view model, so it survives leaving and coming back; "Connect to my computer" goes straight to the server editor.
 */
@Composable
public fun WelcomeScreen(state: WelcomeUi, actions: WelcomeActions) {
    LifecycleStartEffect(actions) {
        actions.refresh()
        onStopOrDispose {}
    }
    val phone = state.phone
    val working = KitEnabled.of(!phone.working, stringResource(Res.string.r40SetupWorking))
    // Off the phone path the computer is the only answer, chosen.
    val chosen = if (phone.available) state.place else WelcomePlace.Computer
    KitScreen(width = KitScreenWidth.Reading, page = true) {
        Column(Modifier.fillMaxSize().testTag("welcome-body")) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.S4.dp),
            ) {
                // Air above the question (Flutter: space6 + space6).
                Spacer(Modifier.height((Space.S6 + Space.S6).dp))
                KitHeroQuestion(stringResource(Res.string.firstRunWhereQuestion), Modifier.testTag("welcome-question"))
                Spacer(Modifier.height((Space.S6 + Space.S2).dp))
                KitChoiceCards(Modifier.testTag("welcome-choices")) {
                    if (phone.available)
                        KitChoiceCard(
                            title = stringResource(Res.string.onboardingTermuxSetup),
                            selected = chosen == WelcomePlace.Phone,
                            onSelect = { if (working.isEnabled) actions.select(WelcomePlace.Phone) },
                            supporting = phoneLine(phone),
                            icon = KitIcons.phone,
                            busy = phone.working,
                        )
                    KitChoiceCard(
                        title = stringResource(Res.string.firstRunOnComputer),
                        selected = chosen == WelcomePlace.Computer,
                        onSelect = { if (working.isEnabled) actions.select(WelcomePlace.Computer) },
                        supporting = stringResource(Res.string.welcomeComputerLine),
                        icon = KitIcons.computer,
                    )
                }
                if (phone.failure != null) {
                    Spacer(Modifier.height(Space.S2.dp))
                    KitErrorNotice(stringResource(Res.string.r40SetupActionFailed))
                }
                // The quiet ways in, under the answers they sit beside.
                Spacer(Modifier.height(Space.S2.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S4.dp)) {
                    KitButton(
                        stringResource(Res.string.firstRunJustShowMe),
                        actions::showDemo,
                        role = KitButtonRole.Tertiary,
                        enabled = working,
                    )
                    if (phone.available)
                        KitButton(
                            stringResource(Res.string.welcomeMoreWays),
                            actions::moreWays,
                            role = KitButtonRole.Tertiary,
                            enabled = working,
                        )
                    // Native addition kept from first run: language and appearance before anything else, for a
                    // person who cannot read the default language.
                    KitButton(
                        stringResource(Res.string.e7LocaleUiSettings),
                        actions::openSettings,
                        role = KitButtonRole.Tertiary,
                    )
                }
                Spacer(Modifier.height(Space.S4.dp))
            }
            // The one main action at the thumb, naming the chosen answer.
            Box(
                Modifier.fillMaxWidth().padding(horizontal = Space.S4.dp, vertical = Space.S2.dp),
                contentAlignment = Alignment.Center,
            ) {
                KitButton(
                    stringResource(
                        if (chosen == WelcomePlace.Computer) Res.string.welcomeComputerConnect
                        else
                            when (phone.phase) {
                                WelcomeSetupPhase.Idle -> Res.string.welcomePhoneSetUp
                                WelcomeSetupPhase.Running -> Res.string.phoneSetupOpenWelcomeShowProgress
                                WelcomeSetupPhase.Stopped -> Res.string.welcomePhoneContinue
                                WelcomeSetupPhase.Done -> Res.string.welcomePhoneOpen
                            }
                    ),
                    if (chosen == WelcomePlace.Computer) actions::connect else actions::begin,
                    modifier = Modifier.testTag("welcome-primary"),
                    enabled = working,
                    working = phone.working,
                    fullWidth = true,
                    hero = true,
                )
            }
        }
    }
}

/** The phone card's one calm line (Flutter `PhoneWelcomeOffer.line`): the cost, or how far a started setup got. */
@Composable
private fun phoneLine(phone: PhoneWelcomeUi): String =
    when (phone.phase) {
        WelcomeSetupPhase.Idle -> {
            val seconds = phone.seconds
            if (seconds == null) stringResource(Res.string.r40WelcomePhoneBody)
            else {
                val minutes = max(1, (seconds / 60.0).roundToInt())
                val time =
                    if (minutes == 1) stringResource(Res.string.phoneSetupStartAboutMinutes__eq1)
                    else pluralStringResource(Res.plurals.phoneSetupStartAboutMinutes, minutes, minutes)
                val bytes = phone.bytes ?: 0L
                if (bytes > 0) stringResource(Res.string.welcomePhoneCost, time, sizeText(bytes)) else time
            }
        }
        WelcomeSetupPhase.Running -> stringResource(Res.string.welcomePhoneSettingUp, phone.percent.coerceIn(0, 99))
        WelcomeSetupPhase.Stopped -> stringResource(Res.string.welcomePhoneStopped, phone.percent.coerceIn(0, 99))
        WelcomeSetupPhase.Done -> stringResource(Res.string.welcomePhoneReady)
    }

/** "165 MB" or "1.2 GB" in decimal units (Flutter `setupSizeText`). */
@Composable
private fun sizeText(bytes: Long): String {
    val megabytes = bytes / 1_000_000.0
    if (megabytes >= 1000) {
        val tenths = (megabytes / 100).roundToInt()
        return stringResource(Res.string.phoneSetupStartGigabytes, "${tenths / 10}.${tenths % 10}")
    }
    return stringResource(Res.string.phoneSetupStartMegabytes, max(1, megabytes.roundToInt()).toString())
}

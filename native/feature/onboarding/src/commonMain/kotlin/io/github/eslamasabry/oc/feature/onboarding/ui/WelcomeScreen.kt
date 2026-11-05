package io.github.eslamasabry.oc.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.feature.onboarding.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceCard
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceCards
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.words.res.*
import kotlin.math.ceil
import org.jetbrains.compose.resources.stringResource

@Composable
public fun WelcomeScreen(actions: WelcomeActions) {
    WelcomeScreen(WelcomeUi(), actions)
}

/** Phone and computer are visible choices; the phone answer comes from its durable setup job. */
@Composable
public fun WelcomeScreen(state: WelcomeUi, actions: WelcomeActions) {
    LifecycleStartEffect(actions) {
        actions.refresh()
        onStopOrDispose {}
    }
    val phone = state.phone
    val working = KitEnabled.of(!phone.working, stringResource(Res.string.r40SetupWorking))
    val idleTotals =
        if (phone.phase == WelcomeSetupPhase.Idle && phone.bytes != null && phone.seconds != null)
            stringResource(
                Res.string.r40SetupTotals,
                stringResource(
                    Res.string.phoneSetupStartMegabytes,
                    Numbers.format(ceil(phone.bytes / 1_000_000.0).toLong()),
                ),
                Numbers.format(ceil(phone.seconds / 60.0).toLong().coerceAtLeast(1)),
            )
        else null
    // Looks pass, owner pick A (2026-11-05): the question, two choice cards (never two switches for one choice),
    // and one full-width button at the foot; Settings is a quiet text button under it.
    KitScreen(width = KitScreenWidth.Reading, page = true) {
        Column(Modifier.fillMaxSize().testTag("welcome-body")) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Space.S4.dp),
                verticalArrangement = Arrangement.spacedBy(Space.S4.dp),
            ) {
                Spacer(Modifier.height(Space.S6.dp))
                KitText(stringResource(Res.string.phoneSetupStartHeadline), role = TypeRole.LargeTitle, pageTitle = true)
                KitText(stringResource(Res.string.appTitle), role = TypeRole.Secondary, tone = TextTone.Secondary)
                if (phone.available) {
                    KitText(stringResource(Res.string.firstRunWhereQuestion), role = TypeRole.Headline)
                    KitChoiceCards {
                        KitChoiceCard(
                            title = stringResource(Res.string.r40WelcomePhone),
                            selected = state.place == WelcomePlace.Phone,
                            onSelect = { if (working.isEnabled) actions.select(WelcomePlace.Phone) },
                            supporting = idleTotals ?: stringResource(Res.string.r40WelcomePhoneBody),
                            icon = KitIcons.phone,
                            tag = stringResource(Res.string.kitChoiceRecommended),
                        )
                        KitChoiceCard(
                            title = stringResource(Res.string.r40WelcomeComputer),
                            selected = state.place == WelcomePlace.Computer,
                            onSelect = { if (working.isEnabled) actions.select(WelcomePlace.Computer) },
                            supporting = stringResource(Res.string.r40WelcomeComputerBody),
                            icon = KitIcons.computer,
                        )
                    }
                }
                if (phone.available && state.place == WelcomePlace.Phone && phone.phase != WelcomeSetupPhase.Idle) {
                    KitText(
                        when (phone.phase) {
                            WelcomeSetupPhase.Running ->
                                stringResource(Res.string.r40WelcomeRunning, Numbers.format(phone.percent))
                            WelcomeSetupPhase.Stopped ->
                                stringResource(Res.string.r40WelcomeStopped, Numbers.format(phone.percent))
                            else -> stringResource(Res.string.phoneSetupStartReadyHeadline)
                        },
                        role = TypeRole.RowTitle,
                    )
                    KitText(
                        stringResource(
                            when (phone.phase) {
                                WelcomeSetupPhase.Running -> Res.string.phoneSetupStartRunningBody
                                WelcomeSetupPhase.Stopped -> Res.string.phoneSetupStartStoppedBody
                                else -> Res.string.phoneSetupStartReadyBody
                            }
                        ),
                        role = TypeRole.Secondary,
                        tone = TextTone.Secondary,
                    )
                }
                if (phone.available && state.place == WelcomePlace.Phone && phone.phase == WelcomeSetupPhase.Idle)
                    KitText(stringResource(Res.string.r40WelcomeSetupBody), role = TypeRole.Secondary, tone = TextTone.Secondary)
                if (phone.failure != null) KitErrorNotice(stringResource(Res.string.r40SetupActionFailed))
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = Space.S4.dp, vertical = Space.S2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                KitButton(
                    stringResource(
                        if (state.place == WelcomePlace.Computer || !phone.available)
                            Res.string.welcomeComputerConnect
                        else
                            when (phone.phase) {
                                WelcomeSetupPhase.Running -> Res.string.r40WelcomeViewProgress
                                WelcomeSetupPhase.Stopped -> Res.string.phoneSetupStartContinue
                                WelcomeSetupPhase.Done -> Res.string.phoneSetupStartOpen
                                WelcomeSetupPhase.Idle -> Res.string.phoneSetupStartSetUp
                            }
                    ),
                    if (state.place == WelcomePlace.Computer || !phone.available) actions::connect
                    else actions::begin,
                    enabled = working,
                    working = phone.working,
                    fullWidth = true,
                )
                KitButton(
                    stringResource(Res.string.e7LocaleUiSettings),
                    actions::openSettings,
                    role = KitButtonRole.Tertiary,
                )
            }
        }
    }
}

package io.github.eslamasabry.oc.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.feature.onboarding.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
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
    KitScreen(width = KitScreenWidth.Reading, page = true) {
        Column(
            Modifier.padding(Space.S4.dp)
                .verticalScroll(rememberScrollState())
                .testTag("welcome-body"),
            verticalArrangement = Arrangement.spacedBy(Space.S4.dp),
        ) {
            KitText(stringResource(Res.string.appTitle), pageTitle = true)
            if (phone.available) {
                KitSwitchRow(
                    stringResource(Res.string.r40WelcomePhone),
                    state.place == WelcomePlace.Phone,
                    supporting = stringResource(Res.string.r40WelcomePhoneBody),
                    enabled = working,
                    onCheckedChange = { actions.select(WelcomePlace.Phone) },
                )
                KitSwitchRow(
                    stringResource(Res.string.r40WelcomeComputer),
                    state.place == WelcomePlace.Computer,
                    supporting = stringResource(Res.string.r40WelcomeComputerBody),
                    enabled = working,
                    onCheckedChange = { actions.select(WelcomePlace.Computer) },
                )
            }
            if (phone.available && state.place == WelcomePlace.Phone) {
                KitText(
                    when (phone.phase) {
                        WelcomeSetupPhase.Running ->
                            stringResource(
                                Res.string.r40WelcomeRunning,
                                Numbers.format(phone.percent),
                            )
                        WelcomeSetupPhase.Stopped ->
                            stringResource(
                                Res.string.r40WelcomeStopped,
                                Numbers.format(phone.percent),
                            )
                        WelcomeSetupPhase.Done ->
                            stringResource(Res.string.phoneSetupStartReadyHeadline)
                        WelcomeSetupPhase.Idle -> stringResource(Res.string.phoneSetupStartHeadline)
                    }
                )
                KitText(
                    stringResource(
                        when (phone.phase) {
                            WelcomeSetupPhase.Running -> Res.string.phoneSetupStartRunningBody
                            WelcomeSetupPhase.Stopped -> Res.string.phoneSetupStartStoppedBody
                            WelcomeSetupPhase.Done -> Res.string.phoneSetupStartReadyBody
                            WelcomeSetupPhase.Idle -> Res.string.r40WelcomeSetupBody
                        }
                    )
                )
                if (
                    phone.phase == WelcomeSetupPhase.Idle &&
                        phone.bytes != null &&
                        phone.seconds != null
                ) {
                    KitText(
                        stringResource(
                            Res.string.r40SetupTotals,
                            stringResource(
                                Res.string.phoneSetupStartMegabytes,
                                Numbers.format(ceil(phone.bytes / 1_000_000.0).toLong()),
                            ),
                            Numbers.format(ceil(phone.seconds / 60.0).toLong().coerceAtLeast(1)),
                        )
                    )
                }
                if (phone.failure != null)
                    KitErrorNotice(stringResource(Res.string.r40SetupActionFailed))
            }
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
            )
            KitButton(
                stringResource(Res.string.e7LocaleUiSettings),
                actions::openSettings,
                role = KitButtonRole.Secondary,
            )
        }
    }
}

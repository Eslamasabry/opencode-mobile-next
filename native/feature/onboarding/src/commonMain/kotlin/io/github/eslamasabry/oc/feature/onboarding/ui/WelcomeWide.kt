package io.github.eslamasabry.oc.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.onboarding.presentation.PhoneWelcomeUi
import io.github.eslamasabry.oc.feature.onboarding.presentation.WelcomeActions
import io.github.eslamasabry.oc.feature.onboarding.presentation.WelcomePlace
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceCard
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitPaneColumn
import io.github.eslamasabry.oc.ui.kit.frame.kitPageScrollState
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitHeroQuestion
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.firstRunJustShowMe
import io.github.eslamasabry.oc.ui.words.res.firstRunOnComputer
import io.github.eslamasabry.oc.ui.words.res.firstRunWhereQuestion
import io.github.eslamasabry.oc.ui.words.res.r40SetupActionFailed
import io.github.eslamasabry.oc.ui.words.res.welcomeComputerLine
import io.github.eslamasabry.oc.ui.words.res.welcomeMoreWays
import org.jetbrains.compose.resources.stringResource

/**
 * Expanded-width (>= 840 dp) first-run layout: hero question on the start edge (clear of the top-centre system
 * handle), two equal-height choice cards side by side filling the width between page gutters with no side bands, and
 * an aligned action row on the exact same edges (quiet links at start, hero confirm button at end).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WelcomeWideBody(
    phone: PhoneWelcomeUi,
    chosen: WelcomePlace,
    working: KitEnabled,
    tablet: Boolean,
    deviceChoiceTitle: String,
    deviceSupporting: String,
    primaryLabel: String,
    actions: WelcomeActions,
) {
    BoxWithConstraints(Modifier.fillMaxSize().testTag("welcome-body")) {
        val availableWidth = maxWidth
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(kitPageScrollState())
                .padding(top = (Space.S6 + Space.S2).dp, bottom = Space.S6.dp),
        ) {
            KitHeroQuestion(
                stringResource(Res.string.firstRunWhereQuestion),
                Modifier.testTag("welcome-question"),
            )
            Spacer(Modifier.height((Space.S6 + Space.S2).dp))
            CompositionLocalProvider(LocalKitPaneColumn provides availableWidth) {
                Row(
                    Modifier.fillMaxWidth()
                        .height(IntrinsicSize.Max)
                        .semantics { selectableGroup() }
                        .testTag("welcome-choices"),
                    horizontalArrangement = Arrangement.spacedBy(Space.S4.dp),
                ) {
                    if (phone.available) {
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            KitChoiceCard(
                                title = deviceChoiceTitle,
                                selected = chosen == WelcomePlace.Phone,
                                onSelect = { if (working.isEnabled) actions.select(WelcomePlace.Phone) },
                                modifier = Modifier.fillMaxHeight().heightIn(min = WIDE_CHOICE_MIN_HEIGHT_DP.dp),
                                supporting = deviceSupporting,
                                icon = if (tablet) KitIcons.tablet else KitIcons.phone,
                                busy = phone.working,
                            )
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        KitChoiceCard(
                            title = stringResource(Res.string.firstRunOnComputer),
                            selected = chosen == WelcomePlace.Computer,
                            onSelect = { if (working.isEnabled) actions.select(WelcomePlace.Computer) },
                            modifier = Modifier.fillMaxHeight().heightIn(min = WIDE_CHOICE_MIN_HEIGHT_DP.dp),
                            supporting = stringResource(Res.string.welcomeComputerLine),
                            icon = KitIcons.computer,
                        )
                    }
                }
            }
            if (phone.failure != null) {
                Spacer(Modifier.height(Space.S3.dp))
                KitErrorNotice(stringResource(Res.string.r40SetupActionFailed))
            }
            Spacer(Modifier.height(Space.S6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.S4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlowRow(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(Space.S6.dp),
                    verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
                ) {
                    KitButton(
                        stringResource(Res.string.firstRunJustShowMe),
                        actions::showDemo,
                        role = KitButtonRole.Tertiary,
                        enabled = working,
                    )
                    if (phone.available) {
                        KitButton(
                            stringResource(Res.string.welcomeMoreWays),
                            actions::moreWays,
                            role = KitButtonRole.Tertiary,
                            enabled = working,
                        )
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    KitButton(
                        primaryLabel,
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
}

private const val WIDE_CHOICE_MIN_HEIGHT_DP = 224

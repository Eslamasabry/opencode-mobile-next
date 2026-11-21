package io.github.eslamasabry.oc.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.domain.capabilities.DeviceClass
import io.github.eslamasabry.oc.feature.onboarding.presentation.PhoneWelcomeUi
import io.github.eslamasabry.oc.feature.onboarding.presentation.WelcomeActions
import io.github.eslamasabry.oc.feature.onboarding.presentation.WelcomePlace
import io.github.eslamasabry.oc.feature.onboarding.presentation.WelcomeUi
import io.github.eslamasabry.oc.feature.onboarding.ui.WelcomeScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import java.util.Locale
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/** Wide-window behavior tests for first-run WelcomeScreen (B-ONB-300..302). */
@OptIn(ExperimentalTestApi::class)
class WelcomeWideTest {
    private var previousLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun localeBefore() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun localeAfter() {
        Locale.setDefault(previousLocale)
    }

    private object NoActions : WelcomeActions {
        override fun connect() {}

        override fun openSettings() {}
    }

    private val defaultUi =
        WelcomeUi(
            phone = PhoneWelcomeUi(available = true, seconds = 240, bytes = 208_000_000),
            place = WelcomePlace.Phone,
        )

    @Test
    fun `B-ONB-300 first run choices sit side by side with no side band wider than gutter across 840 to 1700`() {
        for (width in 840..1700 step 40) {
            var gutter = 16
            runDesktopComposeUiTest(width, 800) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f)) {
                        KitTheme(
                            appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                            platform = DesktopKitPlatform(),
                            localeTag = "en",
                            stillFrame = true,
                        ) {
                            gutter = LocalKitTokens.current.gutter
                            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                WelcomeScreen(defaultUi, NoActions, deviceClass = DeviceClass.Phone)
                            }
                        }
                    }
                }
                waitForIdle()
                val phoneCard = onNode(hasContentDescription("On this phone", substring = true)).getBoundsInRoot()
                val computerCard = onNode(hasContentDescription("On my computer", substring = true)).getBoundsInRoot()
                val primary = onNodeWithTag("welcome-primary").getBoundsInRoot()
                val question = onNodeWithTag("welcome-question").getBoundsInRoot()
                val at = "width=${width}dp"
                // Side by side: computer card starts to the right of phone card, tops and heights aligned.
                assertTrue(
                    computerCard.left.value > phoneCard.right.value &&
                        abs(phoneCard.top.value - computerCard.top.value) <= 1f &&
                        abs(phoneCard.bottom.value - computerCard.bottom.value) <= 1f,
                    "$at: expected equal-height side-by-side choices, phone=$phoneCard computer=$computerCard",
                )
                // Owner width rule: no side band wider than the gutter.
                val leftBand = phoneCard.left.value
                val rightBand = width.toFloat() - computerCard.right.value
                assertTrue(
                    leftBand <= gutter + 1f && rightBand <= gutter + 1f,
                    "$at: side bands ($leftBand, $rightBand) exceed gutter=$gutter",
                )
                // Confirm button right edge belongs to the same column as the choices.
                assertTrue(
                    abs(primary.right.value - computerCard.right.value) <= 1f,
                    "$at: primary right (${primary.right}) must align with choices right (${computerCard.right})",
                )
                // Nothing placed under the top-centre system handle (32 dp down from top edge).
                assertTrue(
                    question.top.value >= 32f,
                    "$at: hero question (${question.top}) must stay clear of top-centre system handle",
                )
            }
        }
    }

    @Test
    fun `B-ONB-301 first run stays stacked single column under 840`() {
        for (width in listOf(360, 600, 839)) {
            runDesktopComposeUiTest(width, 800) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f)) {
                        KitTheme(
                            appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                            platform = DesktopKitPlatform(),
                            localeTag = "en",
                            stillFrame = true,
                        ) {
                            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                WelcomeScreen(defaultUi, NoActions, deviceClass = DeviceClass.Phone)
                            }
                        }
                    }
                }
                waitForIdle()
                val phoneCard = onNode(hasContentDescription("On this phone", substring = true)).getBoundsInRoot()
                val computerCard = onNode(hasContentDescription("On my computer", substring = true)).getBoundsInRoot()
                assertTrue(
                    computerCard.top.value >= phoneCard.bottom.value &&
                        abs(phoneCard.left.value - computerCard.left.value) <= 1f,
                    "width=${width}dp: expected stacked choices, phone=$phoneCard computer=$computerCard",
                )
            }
        }
    }

    @Test
    fun `B-ONB-302 first run names a tablet as this tablet and keeps phone copy on a phone`() {
        runDesktopComposeUiTest(1280, 800) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    KitTheme(
                        appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                        platform = DesktopKitPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                            WelcomeScreen(defaultUi, NoActions, deviceClass = DeviceClass.Tablet)
                        }
                    }
                }
            }
            waitForIdle()
            onNode(hasContentDescription("On this tablet", substring = true)).assertExists()
            onNodeWithText("Set up on this tablet").assertExists()
        }
        runDesktopComposeUiTest(1280, 800) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    KitTheme(
                        appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                        platform = DesktopKitPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                            WelcomeScreen(defaultUi, NoActions, deviceClass = DeviceClass.Phone)
                        }
                    }
                }
            }
            waitForIdle()
            onNode(hasContentDescription("On this phone", substring = true)).assertExists()
            onNodeWithText("Set up on this phone").assertExists()
        }
    }
}

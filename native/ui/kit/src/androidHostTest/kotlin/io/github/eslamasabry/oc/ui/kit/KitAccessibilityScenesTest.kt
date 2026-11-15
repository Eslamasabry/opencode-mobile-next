package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.agent.KitAgentTile
import io.github.eslamasabry.oc.ui.kit.agent.KitAgentTileAction
import io.github.eslamasabry.oc.ui.kit.agent.KitAgentTileTone
import io.github.eslamasabry.oc.ui.kit.agent.KitAgentTiles
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.nav.KitBreadcrumb
import io.github.eslamasabry.oc.ui.kit.state.KitProgress
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * B-A11Y-SCENES (UX-22, UX-24, UX-37): the Android Accessibility Test Framework over the kit parts a person meets first in
 * a conversation and on the Agents page: the conversation's top bar (Back beside the title, icon actions, the overflow),
 * the agent tiles (open, install, sign in, progress), the breadcrumb and the tab strip. Every scene runs at 360 dp in
 * eight settings: light and dark, English and Arabic (right to left), and the person's system text size 1.0, 1.3 and 2.0.
 * ATF fails a touch target under 48 dp, a missing label, a duplicate clickable bounds, a low contrast and a clipped text.
 *
 * Text size is not asked to reach 200 % everywhere: the owner caps frame and chrome text at 1.3x and lets reading text
 * scale fully (`TextScalePolicy.FRAME_CAP`); the 2.0 setting therefore proves that the capped words still sit whole in
 * their targets, not that they grew. The composer, the approval card and the step list run the same eight settings in
 * `:ui:kit-chat` (`KitChatAccessibilityChecksTest`).
 * Run: `:ui:kit:testAndroidHostTest --tests '*KitAccessibilityScenesTest*'`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [KitRobolectric.SDK], qualifiers = KitRobolectric.PHONE)
class KitAccessibilityScenesTest(private val variant: Variant) {
    data class Variant(val dark: Boolean, val localeTag: String, val fontScale: Float) {
        override fun toString(): String =
            (if (dark) "dark" else "light") + "-" + localeTag + "-text" + (fontScale * 100).toInt()
    }

    @get:Rule val compose = createComposeRule()

    private fun check(content: @Composable () -> Unit) {
        compose.enableAccessibilityChecks()
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, variant.fontScale)) {
                KitTheme(
                    appearance =
                        KitAppearance(
                            mode = if (variant.dark) AppearanceMode.Dark else AppearanceMode.Light,
                            motion = MotionLevel.Off,
                        ),
                    platform = KitRobolectric.platform(),
                    localeTag = variant.localeTag,
                    stillFrame = true,
                ) {
                    Box(Modifier.width(360.dp)) { content() }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    private val stop = KitLineAction("Stop task", {}, icon = KitIcons.stop)
    private val copy = KitLineAction("Copy link", {}, icon = KitIcons.copy)

    @Test fun `B-A11Y-SCENES conversation top bar passes ATF`() =
        check {
            KitTopBar(
                "Fix login",
                subtitle = "Working · 2 min",
                subtitleTone = StatusTone.Progress,
                exit = KitTopBarExit.Back,
                exitLabel = "Back",
                onExit = {},
                actions = listOf(stop, copy),
                more = { KitIconButton(KitIcons.more, "More actions", {}) },
                backBesideTitle = true,
            )
        }

    @Test fun `B-A11Y-SCENES top bar with requests waiting elsewhere passes ATF`() =
        check {
            KitTopBar(
                "Fix login",
                subtitle = "Laptop",
                exit = KitTopBarExit.Back,
                exitLabel = "Back",
                onExit = {},
                onTitleTap = {},
                titleTapLabel = "Switch conversation",
                actions = listOf(stop),
                needsYou = 2,
            )
        }

    @Test fun `B-A11Y-SCENES agent tiles pass ATF`() =
        check {
            KitAgentTiles(
                listOf(
                    { KitAgentTile("Claude Code", "Ready", "claude", {}, ready = true) },
                    {
                        KitAgentTile(
                            "Gemini",
                            "Not installed",
                            "gemini",
                            {},
                            action = KitAgentTileAction("Install", "Install Gemini", onClick = {}),
                        )
                    },
                    {
                        KitAgentTile(
                            "Codex",
                            "Sign in needed",
                            "codex",
                            {},
                            tone = KitAgentTileTone.NeedsYou,
                            action = KitAgentTileAction("Sign in", "Sign in to Codex", prominent = true, onClick = {}),
                        )
                    },
                    { KitAgentTile("fx", "Installing…", "fx", {}, progress = KitProgress.Known(0.4f)) },
                    { KitAgentTile("Old agent", "Not available", "old", {}, tone = KitAgentTileTone.Dimmed) },
                )
            )
        }

    @Test fun `B-A11Y-SCENES breadcrumb passes ATF`() =
        check { Column { KitBreadcrumb(rootLabel = "Projects", segments = listOf("shopfront", "web", "src"), onSelected = {}) } }

    @Test fun `B-A11Y-SCENES tab strip passes ATF`() = check { TabsScene(KitbWords.of(variant.localeTag), index = 1) }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Variant> =
            listOf(
                Variant(dark = false, localeTag = "en", fontScale = 1f),
                Variant(dark = true, localeTag = "en", fontScale = 1f),
                Variant(dark = false, localeTag = "ar", fontScale = 1f),
                Variant(dark = true, localeTag = "ar", fontScale = 1f),
                Variant(dark = false, localeTag = "en", fontScale = 1.3f),
                Variant(dark = false, localeTag = "ar", fontScale = 1.3f),
                Variant(dark = true, localeTag = "en", fontScale = 2f),
                Variant(dark = false, localeTag = "ar", fontScale = 2f),
            )
    }
}

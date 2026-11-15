@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.A11yAudit
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-A11Y-AUDIT (UX-22, UX-24, UX-37): the accessibility audit ([A11yAudit]) over the kit parts a person meets first: the
 * conversation's top bar, the agent tiles, the breadcrumb, the tabs, search, filter chips and the motion parts. Every
 * scene is 360 dp wide and runs in eight settings: light and dark, English and Arabic (right to left), and the person's
 * system text size 1.0, 1.3 and 2.0. A scene fails on a touch box under 48 dp, a control a screen reader cannot name, two
 * controls on one box, a control outside the scene and text cut off by its box. Text size 2.0 does not ask the frame to
 * grow past the owner's 1.3x cap (`TextScalePolicy.FRAME_CAP`): reading text scales fully, chrome stops at 1.3x, and the
 * audit proves the capped words still sit whole in their targets.
 *
 * The composer, the approval card and the step list run the same eight settings in `:ui:kit-chat`
 * (`KitChatA11yAuditTest`). This replaces the earlier Robolectric ATF test, which never ran a check (see [A11yAudit]).
 */
class KitA11yAuditTest {
    private data class Variant(val dark: Boolean, val localeTag: String, val fontScale: Float) {
        override fun toString(): String = (if (dark) "dark" else "light") + "-" + localeTag + "-text" + (fontScale * 100).toInt()
    }

    private val variants =
        listOf(
            Variant(false, "en", 1f),
            Variant(true, "en", 1f),
            Variant(false, "ar", 1f),
            Variant(true, "ar", 1f),
            Variant(false, "en", 1.3f),
            Variant(false, "ar", 1.3f),
            Variant(true, "en", 2f),
            Variant(false, "ar", 2f),
        )

    private fun offenders(scene: String, only: List<Variant> = variants, content: @Composable (Variant) -> Unit): List<A11yAudit.Offender> {
        val found = ArrayList<A11yAudit.Offender>()
        for (v in only) {
            runDesktopComposeUiTest(412, 1400) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f, v.fontScale)) {
                        KitTheme(
                            KitAppearance(mode = if (v.dark) AppearanceMode.Dark else AppearanceMode.Light, motion = MotionLevel.Off),
                            FakeKitPlatform(),
                            v.localeTag,
                            stillFrame = true,
                        ) {
                            Box(Modifier.width(360.dp).verticalScroll(rememberScrollState())) { content(v) }
                        }
                    }
                }
                found += A11yAudit.run("$scene $v", this, 360f)
            }
        }
        return found
    }

    private fun check(scene: String, content: @Composable (Variant) -> Unit) {
        val found = offenders(scene, content = content)
        assertEquals("", found.joinToString("\n"), "$scene: ${found.size} accessibility offenders")
    }

    private val stop = KitLineAction("Stop task", {}, icon = KitIcons.stop)
    private val copy = KitLineAction("Copy link", {}, icon = KitIcons.copy)

    @Test fun `B-A11Y-AUDIT conversation top bar`() =
        check("top bar") {
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

    @Test fun `B-A11Y-AUDIT top bar with requests waiting elsewhere`() =
        check("top bar needs you") {
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

    @Test fun `B-A11Y-AUDIT agent tiles`() =
        check("agent tiles") {
            KitAgentTiles(
                listOf(
                    { KitAgentTile("Claude Code", "Ready", "claude", {}, ready = true) },
                    { KitAgentTile("Gemini", "Not installed", "gemini", {}, action = KitAgentTileAction("Install", "Install Gemini", onClick = {})) },
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

    @Test fun `B-A11Y-AUDIT breadcrumb`() =
        check("breadcrumb") { Column { KitBreadcrumb(rootLabel = "Projects", segments = listOf("shopfront", "web", "src"), onSelected = {}) } }

    @Test fun `B-A11Y-AUDIT tab strip`() = check("tabs") { v -> TabsScene(KitbWords.of(v.localeTag), index = 1) }

    @Test fun `B-A11Y-AUDIT search`() = check("search") { v -> SearchScene(KitbWords.of(v.localeTag)) }

    @Test fun `B-A11Y-AUDIT filter chips`() = check("filter chips") { v -> FilterChipsScene(KitbWords.of(v.localeTag)) }

    @Test fun `B-A11Y-AUDIT motion parts`() = check("motion parts") { v -> MotionPartsScene(KitbWords.of(v.localeTag)) }

    // --- The proof that the proof can fail: a scene made of known faults must be caught, rule by rule. ---

    @Test
    fun `B-A11Y-AUDIT catches a tiny tap target, an unnamed one, two on one box, and text cut off`() {
        val found =
            offenders("bad scene", only = listOf(variants[0])) {
                Column {
                    Box(Modifier.size(20.dp).semantics(mergeDescendants = true) { contentDescription = "Tiny"; onClick { true } })
                    Box(Modifier.size(48.dp).clickable {})
                    Box { Box(Modifier.size(48.dp).clickable {}); Box(Modifier.size(48.dp).clickable {}) }
                    Box(Modifier.size(40.dp, 14.dp)) { KitText("A long line that cannot fit one small box and wraps over many lines of words") }
                }
            }
        val rules = found.map { it.rule }.toSet()
        assertTrue("small" in rules, "a 20 dp target is small: $found")
        assertTrue("unnamed" in rules, "a clickable box with no words is unnamed: $found")
        assertTrue("duplicate" in rules, "two controls on one box are a duplicate: $found")
        assertTrue("clipped" in rules, "text taller than its box is clipped: $found")
    }
}

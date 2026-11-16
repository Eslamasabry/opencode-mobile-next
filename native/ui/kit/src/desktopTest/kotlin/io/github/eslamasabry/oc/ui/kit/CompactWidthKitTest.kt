package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate.Frame
import io.github.eslamasabry.oc.ui.gate.LayoutGuard
import io.github.eslamasabry.oc.ui.gate.LayoutGuard.Window
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitFilterChips
import io.github.eslamasabry.oc.ui.kit.nav.KitSessionTab
import io.github.eslamasabry.oc.ui.kit.nav.KitSessionTabs
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.chip.FilterChipsRules
import io.github.eslamasabry.oc.ui.kit.chip.KitFilterChips as KitFilterChipsRest
import io.github.eslamasabry.oc.ui.kit.agent.KitAgentTile
import io.github.eslamasabry.oc.ui.kit.agent.KitAgentTileAction
import io.github.eslamasabry.oc.ui.kit.rb.o23.MARKS
import io.github.eslamasabry.oc.ui.kit.logo.LocalKitLogoLoader
import io.github.eslamasabry.oc.ui.kit.agent.KitAgentTiles
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.nav.KitTab
import io.github.eslamasabry.oc.ui.kit.nav.KitTabStrip
import io.github.eslamasabry.oc.ui.kit.nav.TabRules
import io.github.eslamasabry.oc.ui.kit.style.KitSignalSample
import kotlin.test.assertEquals
import io.github.eslamasabry.oc.ui.kit.agent.agentGridOneColumn
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Compact width (360 dp wide at text 1.0 and 1.3, and 320 dp wide): the kit parts every screen reuses keep their words
 * whole there. The class rules live in each part's doc; these hold them through the layout guard's compact windows.
 */
@OptIn(ExperimentalTestApi::class)
class CompactWidthKitTest {
    private val compact = listOf(Window.Compact, Window.CompactLarge, Window.Narrow)

    private fun found(content: @Composable () -> Unit): List<String> =
        LayoutGuard.audit(LayoutGuard.Scene(Frame.Part, content = content), compact)
            .map { "${it.window.name} ${it.rule} ${it.subject}" }

    @Test
    fun `COMPACT-010 a button in a narrow slot wraps its label and never ends it in an ellipsis`() {
        val scene: @Composable () -> Unit = {
            Column(Modifier.padding(16.dp)) {
                // The agents grid's card action: two columns leave it about 150 dp.
                KitButton("New conversation", onClick = {}, Modifier.width(120.dp), maxLines = 1, expand = false)
                KitButton("Delete this conversation for good", onClick = {}, Modifier.width(150.dp), role = KitButtonRole.Secondary)
                KitButton("Open project files", onClick = {}, Modifier.width(110.dp), role = KitButtonRole.Tertiary, maxLines = 1)
            }
        }
        val bad = found(scene).filter { it.contains("ellipsis") || it.contains("clipped") || it.contains("cut-off") }
        assertTrue(bad.isEmpty(), bad.toString())
    }

    @Test
    fun `COMPACT-011 a filter chip line that scrolls says so and a session strip fades at its edge`() {
        val scene: @Composable () -> Unit = {
            Column {
                KitFilterChips {
                    for (label in listOf("This phone", "All projects", "All connections", "Running now")) KitChip(label, onClick = {})
                }
                KitSessionTabs(
                    tabs = listOf("Build", "Review the change", "Tests", "Deploy to the phone").mapIndexed { i, l ->
                        KitSessionTab(label = l, selected = i == 0, ended = false, onClick = {})
                    },
                    label = "Sessions",
                    endedLabel = "ended",
                )
            }
        }
        val bad = found(scene).filter { it.contains("scroll-cue") }
        assertTrue(bad.isEmpty(), bad.toString())
    }

    @Test
    fun `COMPACT-020 a tab strip that overflows rests with no tab cut by its edge`() {
        val scene: @Composable () -> Unit = {
            // The Appearance page's four tabs: too wide for 360 dp, so it scrolls, and it must rest on whole tabs.
            KitTabStrip(listOf("Colours", "Style", "Background", "Text & motion").map { KitTab(it) }, 1, {})
        }
        val bad = found(scene).filter { it.contains("rest-cut") }
        assertTrue(bad.isEmpty(), bad.toString())
    }

    @Test
    fun `COMPACT-021 the home's chips beside the server pill rest whole, never cut, on one line`() {
        val scene: @Composable () -> Unit = {
            Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                KitChip("This phone", onClick = {})
                Box(Modifier.weight(1f)) {
                    KitFilterChipsRest(bleed = true, restWhole = true) {
                        for (label in listOf("All projects", "All connections", "Running now")) KitChip(label, onClick = {})
                    }
                }
            }
        }
        val bad = found(scene).filter { it.contains("rest-cut") || it.contains("scroll-cue") || it.contains("clipped") }
        assertTrue(bad.isEmpty(), bad.toString())
    }

    @Test
    fun `COMPACT-022 the rest snap puts both edges between tabs and keeps the chosen tab whole`() {
        // Four tabs 100 wide, 8 apart, 16 in: content 456; a 330 window.
        val lefts = floatArrayOf(16f, 124f, 232f, 340f)
        val widths = floatArrayOf(100f, 100f, 100f, 100f)
        val max = 456 - 330
        var placed = 0
        for (selected in 0..3) for (value in 0..max) {
            val v = TabRules.restSnap(value, max, 330f, lefts, widths, selected, 16f, rtl = false) ?: continue
            placed++
            for (e in listOf(v.toFloat(), v + 330f)) for (i in 0..3) {
                assertTrue(!(e > lefts[i] + 0.5f && e < lefts[i] + widths[i] - 0.5f), "edge $e cuts tab $i (value $value, chosen $selected, got $v)")
            }
            assertTrue(lefts[selected] >= v - 0.5f && lefts[selected] + 100f <= v + 330f + 0.5f, "chosen $selected shown whole at $v")
        }
        assertTrue(placed > 0, "some chosen tab has a whole place")
        assertTrue(TabRules.restSnap(0, max, 330f, lefts, widths, 2, 16f, rtl = false) != null, "the third tab has a whole place")
    }

    @Test
    fun `COMPACT-024 a strip that overruns by less than a tab has no whole place to rest and says so`() {
        // 456 wide in a 420 window: every window of 420 starts or ends inside a tab.
        val lefts = floatArrayOf(16f, 124f, 232f, 340f)
        val widths = floatArrayOf(100f, 100f, 100f, 100f)
        assertEquals(null, TabRules.restSnap(0, 36, 420f, lefts, widths, 1, 16f, rtl = false))
    }

    @Test
    fun `COMPACT-023 the Signal style's preview draws no red`() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                    Box(Modifier.width(300.dp).testTag("sample")) { KitSignalSample() }
                }
            }
        }
        val map = onNodeWithTag("sample").captureToImage().toPixelMap()
        var red = 0
        for (x in 0 until map.width) for (y in 0 until map.height) {
            val c = map[x, y]
            if (c.red > 0.6f && c.green < 0.35f && c.blue < 0.35f) red++
        }
        assertEquals(0, red, "pixels that read as red")
    }

    @Test
    fun `COMPACT-025 agent cards fall to one column where two would be narrower than 160 dp`() {
        // A 360 dp phone leaves 328 dp inside the gutters: two columns of 158.
        assertTrue(agentGridOneColumn(1f, 328f), "328 dp wide")
        assertTrue(!agentGridOneColumn(1f, 412f), "412 dp wide keeps two columns")
    }

    @Test
    fun `COMPACT-026 a row's value wraps instead of ending in an ellipsis`() {
        val scene: @Composable () -> Unit = {
            KitRowGroup {
                KitRow("Account", trailing = KitRowTrailing.Value("someone.with.a.long.account.label@example.org", chevron = false))
                KitRow("Folder", trailing = KitRowTrailing.Value("/projects/client-work/the-long-folder-name", chevron = true))
            }
        }
        val bad = found(scene).filter { it.contains("ellipsis") || it.contains("clipped") }
        assertTrue(bad.isEmpty(), bad.toString())
    }

    @Test
    fun `COMPACT-027 a chip line that does not fit ends after its last whole chip and settles on a chip start`() {
        val sizes = intArrayOf(113, 140, 80) // All projects, All connections, Running
        // 187 wide window starting 0: the second chip would be cut, so the window ends after the first.
        assertEquals(121f, FilterChipsRules.wholeEnd(sizes, 8f, 0f, 0f, 187f))
        // everything fits: the whole width
        assertEquals(400f, FilterChipsRules.wholeEnd(sizes, 8f, 0f, 0f, 400f))
        // scrolled a little: the nearest chip start (121) wins over where the finger left it
        assertEquals(121, FilterChipsRules.restStart(sizes, 8f, 0f, 90f, 400))
        assertEquals(0, FilterChipsRules.restStart(sizes, 8f, 0f, 30f, 400))
    }

    @Test
    fun `COMPACT-028 an agent tile's action wraps its words and never ends in an ellipsis`() {
        val scene: @Composable () -> Unit = {
          CompositionLocalProvider(LocalKitLogoLoader provides MARKS) {
            KitAgentTiles(
                listOf<@Composable () -> Unit>(
                    { KitAgentTile("Codex", "Ready", "codex", {}, ready = true, action = KitAgentTileAction("New conversation", "New conversation with Codex", KitIcons.chat) {}) },
                    { KitAgentTile("Claude Code", "Sign in needed", "claude", {}, action = KitAgentTileAction("Sign in", "Sign in to Claude Code", prominent = true) {}) },
                    { KitAgentTile("Antigravity", "Ready", "ag", {}, ready = true, action = KitAgentTileAction("New conversation", "New conversation with Antigravity", KitIcons.chat) {}) },
                    { KitAgentTile("Qwen Code", "Not installed · 44 MB", "qwen", {}, action = KitAgentTileAction("Install", "Install Qwen Code", KitIcons.download) {}) },
                )
            )
          }
        }
        val bad = found(scene).filter { it.contains("ellipsis") || it.contains("clipped") }
        assertTrue(bad.isEmpty(), bad.toString())
    }
}

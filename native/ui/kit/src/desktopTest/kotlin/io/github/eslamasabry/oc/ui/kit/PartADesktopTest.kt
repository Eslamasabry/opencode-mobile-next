package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import io.github.eslamasabry.oc.core.common.CertificateWhy
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.ui.design.color.DynamicSource
import io.github.eslamasabry.oc.ui.design.color.DynamicTones
import io.github.eslamasabry.oc.ui.design.color.Graphite
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.Packs
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.color.argb
import io.github.eslamasabry.oc.ui.design.color.withPartner
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.layout.CapAlign
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitRuleBroken
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.nav.KitNavRail
import io.github.eslamasabry.oc.ui.kit.nav.KitSidebar
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.kitBreakableFileName
import io.github.eslamasabry.oc.ui.kit.row.kitRowSupportingLines
import io.github.eslamasabry.oc.ui.kit.row.kitRowTitleLines
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitErrorKind
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitTechnicalValue
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import io.github.eslamasabry.oc.ui.kit.status.KitStatusLine
import io.github.eslamasabry.oc.ui.kit.status.KitStatusScope
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.toKitColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** S1-KITA1, way 1: the part-A kit parts headless on desktop (`runComposeUiTest`). Density 1, so 1 dp = 1 px. */
@OptIn(ExperimentalTestApi::class)
class PartADesktopTest {
    private val w = PartAWords.En
    private val still = KitAppearance(motion = MotionLevel.Off)

    @Composable
    private fun Themed(
        appearance: KitAppearance = still,
        locale: String = "en",
        fontScale: Float = 1f,
        platform: KitPlatform = FakeKitPlatform(),
        content: @Composable () -> Unit,
    ) {
        CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
            KitTheme(appearance = appearance, platform = platform, localeTag = locale, stillFrame = true, content = content)
        }
    }

    // ---- theme: packs, accents, Material You (B-KIT-053, 061, 009, 010)

    @Test
    fun `B-KIT-053 a missing appearance renders the defaults dark Graphite`() = runComposeUiTest {
        var ground = androidx.compose.ui.graphics.Color.Unspecified
        setContent { KitTheme(platform = FakeKitPlatform(), localeTag = "en") { ground = LocalKitColors.current.ground } }
        waitForIdle()
        assertEquals(Graphite.dark.toKitColors().ground, ground)
    }

    @Test
    fun `B-KIT-010 the stored pack accent and mode reach every kit colour`() = runComposeUiTest {
        var seen: io.github.eslamasabry.oc.ui.kit.theme.KitColors? = null
        val appearance = still.copy(mode = AppearanceMode.Light, pack = PackId.Nord, accent = "pink")
        setContent { Themed(appearance) { seen = LocalKitColors.current } }
        waitForIdle()
        val expected = Packs.of(PackId.Nord).light.roles.withPartner(argb(0xFFAD2E99)).toKitColors()
        assertEquals(expected, seen)
    }

    @Test
    fun `B-KIT-061 Material You reads the platform tones and falls back to Graphite without them`() = runComposeUiTest {
        val tones = DynamicTones(argb(0xFFA8C7FA), argb(0xFFBFC6DC), argb(0xFFDDBCE0), argb(0xFFE3E2E6), argb(0xFF0E0E11), argb(0xFF1B1B1F))
        val withYou = object : KitPlatform by FakeKitPlatform() {
            override fun dynamicSource() = DynamicSource(tones, tones.copy(onSurface = argb(0xFF1B1B1F), surfaceContainerLowest = argb(0xFFFFFFFF)))
        }
        var you = androidx.compose.ui.graphics.Color.Unspecified
        var fallback = androidx.compose.ui.graphics.Color.Unspecified
        setContent {
            Column {
                Themed(still.copy(pack = PackId.Dynamic), platform = withYou) { you = LocalKitColors.current.ground }
                Themed(still.copy(pack = PackId.Dynamic)) { fallback = LocalKitColors.current.ground }
            }
        }
        waitForIdle()
        assertEquals(androidx.compose.ui.graphics.Color(0xFF0E0E11), you)
        assertEquals(Graphite.dark.toKitColors().ground, fallback)
    }

    // ---- max widths (plan 06 §8.2 KitMaxWidthTest)

    @Test
    fun `B-KIT-029 a capped part wraps its child and centres or mirrors at its cap`() = runComposeUiTest {
        setContent {
            Themed {
                InWindow(1600, 400) { Column(Modifier.width(1600.dp)) {
                    Box(Modifier.fillMaxWidth().testTag("reading").kitMaxWidth(CapRole.Reading)) { Box(Modifier.fillMaxWidth().size(10.dp).testTag("readingChild")) }
                    Row(Modifier.width(412.dp)) {
                        Box(Modifier.kitMaxWidth(CapRole.Chip).testTag("chip")) { Box(Modifier.size(80.dp, 32.dp)) }
                        Box(Modifier.weight(1f).size(1.dp).testTag("sibling"))
                    }
                    Box(Modifier.fillMaxWidth().kitMaxWidth(CapRole.Fill).testTag("fill")) { Box(Modifier.size(10.dp)) }
                } }
            }
        }
        val reading = onNodeWithTag("reading").getBoundsInRoot()
        assertEquals(1600.dp, reading.width) // the box fills its slot (the caller forced it) …
        assertEquals(720.dp, onNodeWithTag("readingChild").getBoundsInRoot().width) // … its content stops at the cap
        assertEquals(80.dp, onNodeWithTag("chip").getBoundsInRoot().width) // … a chip keeps its own width
        assertEquals(332.dp, onNodeWithTag("sibling").getBoundsInRoot().width)
        assertEquals(CapAlign.Center, CapRole.Reading.defaultAlign)
    }

    @Test
    fun `B-KIT-029 the reading cap holds at 1600 and start mirrors in RTL`() = runComposeUiTest {
        var rtl by mutableStateOf(false)
        setContent {
            Themed(locale = if (rtl) "ar" else "en") {
                InWindow(1600, 100) {
                    Box(Modifier.fillMaxWidth().kitMaxWidth(CapRole.Reading)) { Box(Modifier.fillMaxWidth().size(1.dp).testTag("c")) }
                    Box(Modifier.fillMaxWidth().kitMaxWidth(CapRole.Popover)) { Box(Modifier.fillMaxWidth().size(1.dp).testTag("p")) }
                }
            }
        }
        val c = inWindow("c")
        assertEquals(720.dp, c.right - c.left)
        assertEquals(440.dp, c.left)
        assertEquals(0.dp, inWindow("p").left)
        rtl = true
        waitForIdle()
        assertEquals(1280.dp, inWindow("p").left) // start = right in Arabic
    }

    // ---- screen frame (B-KIT-029)

    @Test
    fun `B-KIT-029 the frame stacks bar status header loading body and bottom in that order`() = runComposeUiTest {
        setContent { Themed { InWindow(412, 915) { ScreenScene(w, status = reconnecting(w), loading = true) } } }
        val bar = onNodeWithTag("kit-top-bar").getBoundsInRoot()
        val line = onNodeWithTag("kit-status-line").getBoundsInRoot()
        val header = onNodeWithTag("scene-header").getBoundsInRoot()
        val loading = onNodeWithTag("kit-screen-loading").getBoundsInRoot()
        val body = onNodeWithTag("scene-body").getBoundsInRoot()
        val bottom = onNodeWithTag("kit-screen-bottom").getBoundsInRoot()
        assertTrue(bar.bottom <= line.top && line.bottom <= header.top && header.bottom <= loading.top)
        assertTrue(loading.bottom <= body.top && body.bottom <= bottom.top, "$body $bottom")
        assertEquals(2.dp, loading.height)
        onNodeWithTag("kit-screen-loading").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
    }

    @Test
    fun `B-KIT-029 reading and list widths centre the body from medium up and compact is full`() = runComposeUiTest {
        var width by mutableStateOf(KitScreenWidth.Reading)
        var window by mutableStateOf(1280)
        setContent { Themed { InWindow(window, 800) { ScreenScene(w, width = width) } } }
        assertEquals(720.dp, onNodeWithTag("scene-body").getBoundsInRoot().width)
        assertEquals(280.dp, inWindow("scene-body").left)
        width = KitScreenWidth.List
        waitForIdle()
        assertEquals(960.dp, onNodeWithTag("scene-body").getBoundsInRoot().width)
        width = KitScreenWidth.Full
        waitForIdle()
        assertEquals(1280.dp, onNodeWithTag("scene-body").getBoundsInRoot().width)
        width = KitScreenWidth.Reading
        window = 412
        waitForIdle()
        assertEquals(412.dp, onNodeWithTag("scene-body").getBoundsInRoot().width)
    }

    @Test
    fun `B-KIT-029 panes show list and detail from expanded and side only on large`() = runComposeUiTest {
        var size by mutableStateOf(1280 to 800)
        var selected by mutableStateOf(false)
        var rtl by mutableStateOf(false)
        setContent { Themed(locale = if (rtl) "ar" else "en") { InWindow(size.first, size.second) { PaneScene(if (rtl) PartAWords.Ar else w, selected) } } }
        assertEquals(296.dp, onNodeWithTag("kit-screen-pane-list").getBoundsInRoot().width)
        onNodeWithTag("scene-empty").assertExists()
        // Panes follow the body: 1280 − 296 − 340 = 644 ≥ 360, so a large window without a sidebar shows the side.
        assertEquals(340.dp, onNodeWithTag("kit-screen-pane-side").getBoundsInRoot().width)
        selected = true
        waitForIdle()
        onNodeWithTag("scene-empty").assertDoesNotExist()
        assertTrue(onNodeWithTag("kit-page-heading").getBoundsInRoot().width <= 700.dp)
        size = 1600 to 1000
        waitForIdle()
        assertEquals(340.dp, onNodeWithTag("kit-screen-pane-side").getBoundsInRoot().width)
        size = 915 to 412 // short: compact panes
        waitForIdle()
        onAllNodesWithTag("kit-screen-pane-detail").assertCountEquals(0)
        size = 412 to 915
        waitForIdle()
        onAllNodesWithTag("kit-screen-pane-detail").assertCountEquals(0)
        size = 1280 to 800
        rtl = true
        waitForIdle()
        assertEquals(1280.dp - 296.dp, inWindow("kit-screen-pane-list").left) // list at the start = right
    }

    // ---- status line and slot (B-KIT-035)

    @Test
    fun `B-KIT-035 the highest kind wins and ties keep the first`() {
        val info = KitStatus(KitStatusKind.Info, "info")
        val work1 = KitStatus(KitStatusKind.Work, "first")
        val work2 = KitStatus(KitStatusKind.Work, "second")
        val conn = KitStatus(KitStatusKind.Connection, "conn")
        assertEquals(work1, KitStatus.highest(listOf(info, work1, null, work2)))
        assertEquals(conn, KitStatus.highest(listOf(info, work1, conn)))
        assertNull(KitStatus.highest(listOf(null)))
        assertEquals(
            listOf(KitStatusKind.Connection, KitStatusKind.AppStopped, KitStatusKind.Heat, KitStatusKind.RiskySwitch,
                KitStatusKind.Work, KitStatusKind.Update, KitStatusKind.Info),
            KitStatusKind.entries.sortedBy { KitStatus(it, "").priority },
        )
        assertEquals(KitStatus(KitStatusKind.Work, "a", id = "x").announcementKey, KitStatus(KitStatusKind.Work, "a", id = "x", supporting = "5 min").announcementKey)
    }

    @Test
    fun `B-KIT-035 one line per window - an app-wide condition outranks the screen and a tab contributes`() = runComposeUiTest {
        var conditions by mutableStateOf(emptyList<KitStatus>())
        var tabStatus by mutableStateOf<KitStatus?>(KitStatus(KitStatusKind.Work, "tab work"))
        setContent {
            Themed {
                KitStatusScope(conditions) {
                    InWindow(412, 915) {
                        KitScreen(topBar = { KitTopBar(w.conversations) }, status = KitStatus(KitStatusKind.Update, "update ready")) {
                            // A tab body inside the shell's screen: it contributes, it does not draw.
                            KitScreen(status = tabStatus) { KitText("tab") }
                        }
                    }
                }
            }
        }
        onAllNodesWithTag("kit-status-line").assertCountEquals(1)
        onNodeWithText("tab work").assertExists()
        conditions = listOf(reconnecting(w))
        waitForIdle()
        onAllNodesWithTag("kit-status-line").assertCountEquals(1)
        onNodeWithText(w.reconnecting).assertExists()
        conditions = emptyList()
        tabStatus = null
        waitForIdle()
        onNodeWithText("update ready").assertExists()
    }

    @Test
    fun `B-KIT-035 a risky switch line is never dismissible`() = runComposeUiTest {
        assertFailsWith<KitRuleBroken> {
            setContent { Themed { KitStatusLine(KitStatus(KitStatusKind.RiskySwitch, "risky", onDismiss = {}, dismissLabel = "Dismiss")) } }
            waitForIdle()
        }
    }

    @Test
    fun `B-KIT-035 the status action moves under the words at large text`() = runComposeUiTest {
        var scale by mutableStateOf(1f)
        setContent { Themed(fontScale = scale) { Box(Modifier.width(412.dp)) { KitStatusLine(reconnecting(w)) } } }
        val inline = onNodeWithTag("kit-status-action").getBoundsInRoot()
        val words = onNodeWithTag("kit-status-message", useUnmergedTree = true).getBoundsInRoot()
        assertTrue(inline.left > words.right - 1.dp)
        scale = 1.3f
        waitForIdle()
        assertTrue(
            onNodeWithTag("kit-status-action").getBoundsInRoot().top >=
                onNodeWithTag("kit-status-message", useUnmergedTree = true).getBoundsInRoot().bottom,
        )
    }

    // ---- top bar

    @Test
    fun `B-KIT-029 the top bar keeps two actions on compact three on medium and all on wide`() = runComposeUiTest {
        var window by mutableStateOf(412)
        val actions = (1..4).map { KitLineAction("Act $it", {}) }
        setContent {
            Themed {
                InWindow(window, 800) {
                    KitTopBar(w.conversations, actions = actions, more = { hidden -> KitText("more ${hidden.size}", Modifier.testTag("more")) })
                }
            }
        }
        onNodeWithText("Act 3").assertDoesNotExist()
        onNodeWithText("more 2").assertExists()
        window = 700
        waitForIdle()
        onNodeWithText("Act 3").assertExists()
        onNodeWithText("more 1").assertExists()
        window = 1280
        waitForIdle()
        onNodeWithText("Act 4").assertExists()
        onNodeWithText("more 0").assertExists()
    }

    @Test
    fun `B-KIT-029 hidden actions without an overflow break the bar's rule`() = runComposeUiTest {
        assertFailsWith<KitRuleBroken> {
            setContent { Themed { InWindow(412, 800) { KitTopBar(w.conversations, actions = (1..3).map { KitLineAction("A$it", {}) }) } } }
            waitForIdle()
        }
    }

    @Test
    fun `B-KIT-052 the bar title is frame text - capped at 1_3 at 200 percent`() = runComposeUiTest {
        setContent { Themed(fontScale = 2f) { InWindow(412, 800) { KitTopBar("Hi") } } }
        val layout = textLayout("kit-top-bar-title")
        assertEquals(16f * 1.3f, layout.layoutInput.style.fontSize.value, 0.01f)
    }

    @Test
    fun `B-KIT-001 Back sits at the start and Close at the end in both directions`() = runComposeUiTest {
        var rtl by mutableStateOf(false)
        var exit by mutableStateOf(KitTopBarExit.Back)
        setContent {
            Themed(locale = if (rtl) "ar" else "en") {
                InWindow(412, 200) { KitTopBar(w.conversations, exit = exit, exitLabel = w.back, onExit = {}) }
            }
        }
        assertEquals(4.dp, inWindow("kit-top-bar-exit").left)
        rtl = true
        waitForIdle()
        assertEquals(412.dp - 4.dp, inWindow("kit-top-bar-exit").right)
        exit = KitTopBarExit.Close
        rtl = false
        waitForIdle()
        assertEquals(412.dp - 4.dp, inWindow("kit-top-bar-exit").right)
    }

    // ---- rows (B-KIT-027, B-KIT-051)

    @Test
    fun `B-KIT-027 rows keep supporting lines at large text and titles wrap from 1_3`() {
        assertEquals(1, kitRowSupportingLines(1f))
        assertEquals(1, kitRowSupportingLines(1.28f))
        assertEquals(2, kitRowSupportingLines(1.2999999523f))
        assertEquals(3, kitRowSupportingLines(2f))
        assertEquals(4, kitRowSupportingLines(2f, asked = 4))
        assertEquals(1, kitRowTitleLines(1f))
        assertEquals(2, kitRowTitleLines(1.3f))
    }

    @Test
    fun `B-KIT-027 file-name titles break only after separators and before the extension without losing characters`() {
        val name = "release_notes-v2.final.md"
        val breakable = kitBreakableFileName(name)
        assertEquals(name, breakable.replace("​", ""))
        assertEquals("release_​notes-​v2.final​.md", breakable)
    }

    @Test
    fun `B-KIT-027 the supporting line wraps to two lines at 1_3 and three at 2_0`() = runComposeUiTest {
        var scale by mutableStateOf(1f)
        setContent {
            Themed(fontScale = scale) {
                Box(Modifier.width(320.dp)) { KitRow(w.rowTitle, supporting = w.rowSupporting + " " + w.rowSupporting, onClick = {}) }
            }
        }
        assertEquals(1, textLayout("kit-row-supporting").lineCount)
        scale = 1.3f
        waitForIdle()
        assertEquals(2, textLayout("kit-row-supporting").lineCount)
        scale = 2f
        waitForIdle()
        assertEquals(3, textLayout("kit-row-supporting").lineCount)
    }

    @Test
    fun `B-KIT-051 a disabled row states its reason once and does not act`() = runComposeUiTest {
        var taps = 0
        setContent { Themed { KitRow(w.disabledRow, enabled = false, disabledReason = w.disabledReason, onClick = { taps++ }) } }
        onNodeWithTag("kit-row").assertIsNotEnabled()
        onNodeWithTag("kit-row").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, w.disabledReason))
        onNodeWithText(w.disabledReason, useUnmergedTree = true).assertExists()
        onNodeWithTag("kit-row").performClick()
        assertEquals(0, taps)
    }

    @Test
    fun `B-KIT-051 a disabled row without a reason breaks the rule`() = runComposeUiTest {
        assertFailsWith<KitRuleBroken> {
            setContent { Themed { KitRow(w.disabledRow, enabled = false, onClick = {}) } }
            waitForIdle()
        }
    }

    @Test
    fun `B-KIT-027 a trailing value goes under the title at large text and rows reach 48 dp`() = runComposeUiTest {
        var scale by mutableStateOf(1f)
        setContent { Themed(fontScale = scale) { Box(Modifier.width(412.dp)) { KitRow(w.valueTitle, trailing = KitRowTrailing.Value(w.value), onClick = {}) } } }
        val row = onNodeWithTag("kit-row").getBoundsInRoot()
        assertTrue(row.height >= 56.dp)
        val title = onNodeWithTag("kit-row-title", useUnmergedTree = true).getBoundsInRoot()
        assertTrue(onNodeWithTag("kit-row-value", useUnmergedTree = true).getBoundsInRoot().left > title.right - 1.dp)
        scale = 1.3f
        waitForIdle()
        assertTrue(
            onNodeWithTag("kit-row-value", useUnmergedTree = true).getBoundsInRoot().top >=
                onNodeWithTag("kit-row-title", useUnmergedTree = true).getBoundsInRoot().bottom,
        )
    }

    @Test
    fun `B-KIT-027 a group puts its destructive row last and labels itself as a heading`() = runComposeUiTest {
        setContent { Themed { InWindow(412, 915) { RowsScene(w) } } }
        val rows = onAllNodesWithTag("kit-row").fetchSemanticsNodes()
        assertEquals(6, rows.size)
        val last = rows.maxBy { it.boundsInRoot.top }
        assertTrue(last.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString().contains(w.delete))
        onNodeWithTag("kit-section-label").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        assertTrue(onNodeWithTag("kit-group-note").getBoundsInRoot().top >= last.boundsInRoot.bottom.let { androidx.compose.ui.unit.Dp(it) })
    }

    // ---- notice and details (B-KIT-024)

    @Test
    fun `B-KIT-024 timeouts and transport failures are network and the rest are other`() {
        assertEquals(KitErrorKind.Network, KitErrorKind.of(Failure.Network(NetworkWhy.Offline)))
        assertEquals(KitErrorKind.Network, KitErrorKind.of(Failure.TimedOut()))
        assertEquals(KitErrorKind.Network, KitErrorKind.of(Failure.Certificate(CertificateWhy.Untrusted)))
        assertEquals(KitErrorKind.Network, KitErrorKind.of(Failure.Refused(RefusalReason.DispatchUncertain)))
        assertEquals(KitErrorKind.Network, KitErrorKind.of(Failure.ComputerDidNotAnswer()))
        assertEquals(KitErrorKind.Other, KitErrorKind.of(Failure.Server(500)))
        assertEquals(KitErrorKind.Other, KitErrorKind.of(Failure.Unavailable(FeatureId("pty"))))
        assertEquals(KitErrorKind.Other, KitErrorKind.of(Failure.Refused(RefusalReason.ConsentMissing)))
        assertEquals(KitErrorKind.Other, KitErrorKind.of(Failure.Unexpected()))
        assertEquals(KitErrorKind.Other, KitErrorKind.of(null))
    }

    @Test
    fun `B-KIT-024 a network error offers Switch server and another error offers Report a problem`() = runComposeUiTest {
        var failure by mutableStateOf<Failure>(Failure.Network(NetworkWhy.Offline))
        setContent {
            Themed {
                KitErrorNotice(
                    w.couldNotLoad, failure = failure, details = redacted("GET /api → 503"), detailsLabel = w.details,
                    copyLabel = w.copyAll, retry = KitLineAction(w.tryAgain, {}),
                    switchServer = KitLineAction(w.switchServer, {}), report = KitLineAction(w.report, {}),
                )
            }
        }
        onNodeWithText(w.switchServer).assertExists()
        onNodeWithText(w.report).assertDoesNotExist()
        onNodeWithText(w.details).assertExists() // the details fold is last and collapsed
        onNodeWithText("GET /api → 503").assertDoesNotExist()
        failure = Failure.Unexpected()
        waitForIdle()
        onNodeWithText(w.report).assertExists()
        onNodeWithText(w.switchServer).assertDoesNotExist()
        onNodeWithTag("kit-notice").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion).let { m ->
            SemanticsMatcher("has a live region below") { node -> node.children.any { m.matches(it) } || m.matches(node) }
        })
    }

    @Test
    fun `B-KIT-024 the details fold opens shows twelve lines then all and lays values out left to right`() = runComposeUiTest {
        val text = (1..20).joinToString("\n") { "line $it" }
        setContent {
            Themed(locale = "ar") {
                Box(Modifier.width(412.dp)) {
                    KitDetailsFold(
                        label = w.details, copyLabel = w.copyAll, text = redacted(text), showAllLabel = { "all $it" },
                        values = listOf(KitTechnicalValue(w.addressLabel, redacted("http://laptop.local:4096")),
                            KitTechnicalValue("again", redacted("http://laptop.local:4096"))),
                    )
                }
            }
        }
        onNodeWithTag("kit-details-body").assertDoesNotExist()
        onNodeWithTag("kit-details-toggle").assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand)).performClick()
        waitForIdle()
        assertEquals(12, textLayout("kit-details-text").lineCount)
        onAllNodesWithTag("kit-details-value").assertCountEquals(1) // a value shows once
        onNodeWithText("all 20").performClick()
        waitForIdle()
        assertEquals(20, textLayout("kit-details-text").lineCount)
        val value = onNodeWithTag("kit-details-value", useUnmergedTree = true).getBoundsInRoot()
        val body = onNodeWithTag("kit-details-body").getBoundsInRoot()
        assertTrue(value.left - body.left < 20.dp, "LTR value starts at the left edge in Arabic: $value in $body")
    }

    @Test
    fun `B-KIT-024 an empty details fold draws nothing`() = runComposeUiTest {
        setContent { Themed { KitDetailsFold(label = w.details, copyLabel = w.copyAll) } }
        onNodeWithTag("kit-details-fold").assertDoesNotExist()
    }

    // ---- nav

    @Test
    fun `B-KIT-029 the rail is 80 dp and the sidebar grows with text from 296 to its cap`() = runComposeUiTest {
        var scale by mutableStateOf(1f)
        setContent {
            Themed(fontScale = scale) {
                InWindow(1600, 900) {
                    Row {
                        KitNavRail(destinations(w), "chats", {})
                        KitSidebar(destinations(w), "files", {})
                    }
                }
            }
        }
        assertEquals(80.dp, onNodeWithTag("kit-nav-rail").getBoundsInRoot().width)
        assertEquals(296.dp, onNodeWithTag("kit-sidebar").getBoundsInRoot().width)
        onAllNodesWithTag("kit-nav-chats")[0].assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        onAllNodesWithTag("kit-nav-chats")[0].assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, w.needsYouWords))
        scale = 2f
        waitForIdle()
        assertEquals(400.dp, onNodeWithTag("kit-sidebar").getBoundsInRoot().width)
    }

    /** Bounds relative to the pinned window (the test host's own window is 1024 dp wide). */
    private fun androidx.compose.ui.test.ComposeUiTest.inWindow(tag: String, unmerged: Boolean = false): androidx.compose.ui.unit.DpRect {
        val win = onNodeWithTag("window").getBoundsInRoot()
        val b = onNodeWithTag(tag, useUnmergedTree = unmerged).getBoundsInRoot()
        return androidx.compose.ui.unit.DpRect(b.left - win.left, b.top - win.top, b.right - win.left, b.bottom - win.top)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.textLayout(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
        return results.first()
    }
}

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.style.GlassLookId
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import io.github.eslamasabry.oc.ui.kit.live.LiveShaderCompiler
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.DesktopLiveShader
import kotlin.test.Test

/**
 * Joined glass in the kit (docs/designs/liquid-merge-one-body.md section 4, "Kit tests"): a menu opened from a control
 * on the bar's liquid capsule is drawn as one body with it, both panes by the joined pane; when either cannot be
 * liquid (the Frosted look, no lens pass on the platform) both stay the library's panes, and nothing joins once the menu
 * has gone.
 */
class KitLiquidJoinKitTest {
    private val glass = KitAppearance(style = Style.Glass)
    private val joinedPane = androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(KitLiquidJoined)

    /** A platform with the lens and, when [joins], desktop's join shader and lens pass. */
    private fun platform(joins: Boolean): FakeKitPlatform =
        FakeKitPlatform(
            liquidGlass = true,
            liveShaders = if (joins) LiveShaderCompiler(DesktopLiveShader::compile) else null,
            joinEffect = if (joins) ({ p, b -> DesktopKitPlatform().liquidJoinEffect(p, b) }) else null,
        )

    @Composable
    private fun Scene(open: Boolean, onClose: () -> Unit) {
        val source = rememberKitFrostState()
        KitScreen(
            page = true,
            overlayChrome = true,
            frost = source,
            topBar = {
                Box(Modifier.fillMaxWidth().height(56.dp)) {
                    KitMenu(
                        expanded = open,
                        items = listOf(KitMenuItem("Find", onSelect = {}), KitMenuItem("Rename", onSelect = {}), KitMenuItem("Fork", onSelect = {})),
                        onDismissRequest = onClose,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                    ) { KitText("More") }
                }
            },
            edges = KitScreenEdges.Bleed,
        ) {
            Column(Modifier.fillMaxSize().kitFrostSource(source)) {
                repeat(24) { i -> Box(Modifier.fillMaxWidth().height(30.dp).background(if (i % 2 == 0) Color(0xFF3355FF) else Color(0xFFFFCC22))) }
            }
        }
    }

    private fun joinedPanes(appearance: KitAppearance, joins: Boolean, open: Boolean): Int {
        var count = -1
        runDesktopComposeUiTest(412, 800) {
            var shown by mutableStateOf(false)
            setContent { KitTheme(appearance = appearance, platform = platform(joins), localeTag = "en") { Scene(shown) { shown = false } } }
            waitForIdle()
            shown = open
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            count = onAllNodes(joinedPane, useUnmergedTree = true).fetchSemanticsNodes().size
        }
        return count
    }

    @Test
    fun `liquid join - a menu from the bar's control and the bar are one body of glass`() {
        kotlin.test.assertEquals(2, joinedPanes(glass, joins = true, open = true), "the bar's side and the menu's side")
    }

    @Test
    fun `liquid join - with no menu open the bar is the library's pane`() {
        kotlin.test.assertEquals(0, joinedPanes(glass, joins = true, open = false))
    }

    @Test
    fun `liquid join - a partner that is frost keeps both panes the library's`() {
        kotlin.test.assertEquals(0, joinedPanes(glass.copy(glassLook = GlassLookId.Frosted), joins = true, open = true), "the Frosted look")
    }

    @Test
    fun `liquid join - a platform without the lens pass never joins`() {
        kotlin.test.assertEquals(0, joinedPanes(glass, joins = false, open = true))
    }

    @Test
    fun `liquid join - nothing joins once the menu has closed`() = runDesktopComposeUiTest(412, 800) {
        var shown by mutableStateOf(false)
        setContent { KitTheme(appearance = glass, platform = platform(true), localeTag = "en") { Scene(shown) { shown = false } } }
        shown = true
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        onAllNodes(joinedPane, useUnmergedTree = true).assertCountEquals(2)
        shown = false
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        onAllNodes(joinedPane, useUnmergedTree = true).assertCountEquals(0)
    }
}

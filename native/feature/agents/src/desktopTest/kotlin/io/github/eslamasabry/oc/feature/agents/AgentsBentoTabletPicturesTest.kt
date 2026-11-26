package io.github.eslamasabry.oc.feature.agents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.feature.agents.AgentsBentoTest.Companion.EVERY_STATE
import io.github.eslamasabry.oc.feature.agents.AgentsBentoTest.Companion.MARKS
import io.github.eslamasabry.oc.feature.agents.ui.AgentsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.logo.LocalKitLogoLoader
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.text.KitWholeWordsRules
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val STAGE = "after"
private const val DENSITY = 2f
private const val TABLET_WIDTH_DP = 960
private const val TABLET_HEIGHT_DP = 1200
private const val PHONE_WIDTH_DP = 360
private const val PHONE_HEIGHT_DP = 800
private const val EDGE_TOLERANCE_PX = 4f
private const val MIN_TARGET_PX = 48f * DENSITY

/**
 * B-630: Tablet Agents bento alignment against the 960 dp segmented bar, plus before/after review pictures at
 * 960 dp (light, dark, text 1.3, Arabic RTL) and 360 dp phone (light, dark).
 */
@OptIn(ExperimentalTestApi::class)
class AgentsBentoTabletPicturesTest {
    private var previousLocale: Locale = Locale.getDefault()

    private data class SceneSpec(
        val fileName: String,
        val widthDp: Int,
        val heightDp: Int,
        val mode: AppearanceMode,
        val locale: String = "en",
        val scale: Float = 1f,
    )

    @BeforeTest
    fun localeBefore() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun localeAfter() {
        Locale.setDefault(previousLocale)
    }

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "native/settings.gradle.kts").isFile) dir = dir.parentFile
        return checkNotNull(dir) { "repo root not found from ${System.getProperty("user.dir")}" }
    }

    @Test
    fun `B-630 capture tablet 960 dp and phone 360 dp agents pictures`() {
        val out = File(repoRoot(), "native/feature/agents/build/agents-bento-960").apply { mkdirs() }
        val shots =
            listOf(
                SceneSpec("agents-960-solid-dark-$STAGE.png", TABLET_WIDTH_DP, TABLET_HEIGHT_DP, AppearanceMode.Dark),
                SceneSpec("agents-960-solid-light-$STAGE.png", TABLET_WIDTH_DP, TABLET_HEIGHT_DP, AppearanceMode.Light),
                SceneSpec(
                    "agents-960-solid-dark-t13-$STAGE.png",
                    TABLET_WIDTH_DP,
                    TABLET_HEIGHT_DP,
                    AppearanceMode.Dark,
                    scale = 1.3f,
                ),
                SceneSpec(
                    "agents-960-solid-dark-ar-$STAGE.png",
                    TABLET_WIDTH_DP,
                    TABLET_HEIGHT_DP,
                    AppearanceMode.Dark,
                    locale = "ar",
                ),
                SceneSpec("agents-360-solid-dark-$STAGE.png", PHONE_WIDTH_DP, PHONE_HEIGHT_DP, AppearanceMode.Dark),
                SceneSpec("agents-360-solid-light-$STAGE.png", PHONE_WIDTH_DP, PHONE_HEIGHT_DP, AppearanceMode.Light),
            )
        for (shot in shots) {
            runScene(shot) {
                val file = File(out, shot.fileName)
                val image = onAllNodes(isRoot()).onFirst().captureToImage().toAwtImage()
                ImageIO.write(image, "png", file)
                assertTrue(file.isFile && file.length() > 0L, "expected picture ${file.path}")
            }
        }
    }

    @Test
    fun `B-630 tablet 960 dp bento grid aligns with segmented bar in light dark en ar and 1_3`() {
        val cases =
            listOf(
                SceneSpec("en-dark-t10", TABLET_WIDTH_DP, TABLET_HEIGHT_DP, AppearanceMode.Dark, "en", 1f),
                SceneSpec("en-light-t13", TABLET_WIDTH_DP, TABLET_HEIGHT_DP, AppearanceMode.Light, "en", 1.3f),
                SceneSpec("ar-dark-t10", TABLET_WIDTH_DP, TABLET_HEIGHT_DP, AppearanceMode.Dark, "ar", 1f),
                SceneSpec("ar-light-t13", TABLET_WIDTH_DP, TABLET_HEIGHT_DP, AppearanceMode.Light, "ar", 1.3f),
            )
        for (spec in cases) {
            runScene(spec) {
                assertGridAlignsWithBar(spec, expectedColumns = 4)
                assertChipsReadable(spec.fileName)
            }
        }
    }

    @Test
    fun `B-630 compact 360 dp phone bento grid keeps two columns aligned with segmented bar`() {
        val cases =
            listOf(
                SceneSpec("phone-t10", PHONE_WIDTH_DP, PHONE_HEIGHT_DP, AppearanceMode.Dark, "en", 1f),
                SceneSpec("phone-t13", PHONE_WIDTH_DP, PHONE_HEIGHT_DP, AppearanceMode.Light, "en", 1.3f),
            )
        for (spec in cases) {
            runScene(spec) {
                assertGridAlignsWithBar(spec, expectedColumns = 2)
                val tiles = onAllNodesWithTag("kit-agent-tile", useUnmergedTree = true).fetchSemanticsNodes()
                assertTrue(
                    tiles[2].boundsInRoot.top > tiles[0].boundsInRoot.bottom,
                    "[${spec.fileName}] expected tile 2 on second row",
                )
            }
        }
    }

    private fun runScene(spec: SceneSpec, block: ComposeUiTest.() -> Unit) {
        Locale.setDefault(Locale.forLanguageTag(spec.locale))
        try {
            runDesktopComposeUiTest((spec.widthDp * DENSITY).toInt(), (spec.heightDp * DENSITY).toInt()) {
                setContent {
                    CompositionLocalProvider(
                        LocalDensity provides Density(DENSITY, spec.scale),
                        LocalKitSecretSink provides KitSecretSink {},
                        LocalKitLogoLoader provides MARKS,
                    ) {
                        KitTheme(
                            appearance = KitAppearance(mode = spec.mode, style = Style.Solid, motion = MotionLevel.Off),
                            platform = DesktopKitPlatform(),
                            localeTag = spec.locale,
                            stillFrame = true,
                        ) {
                            CompositionLocalProvider(
                                LocalKitWindow provides KitWindowInfo(spec.widthDp.toFloat(), spec.heightDp.toFloat()),
                            ) {
                                Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                    AgentsScreen(EVERY_STATE, AgentsBentoTest.Recorder())
                                }
                            }
                        }
                    }
                }
                waitForIdle()
                mainClock.advanceTimeBy(1_000)
                waitForIdle()
                block()
            }
        } finally {
            Locale.setDefault(Locale.ENGLISH)
        }
    }

    private fun ComposeUiTest.assertGridAlignsWithBar(spec: SceneSpec, expectedColumns: Int) {
        val bar = onNodeWithTag("kit-segmented-track", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val tiles = onAllNodesWithTag("kit-agent-tile", useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(6, tiles.size, "[${spec.fileName}] expected 6 agent tiles")
        val rowTop = tiles[0].boundsInRoot.top
        val row0 = tiles.filter { abs(it.boundsInRoot.top - rowTop) <= EDGE_TOLERANCE_PX }
        assertEquals(expectedColumns, row0.size, "[${spec.fileName}] expected $expectedColumns columns in row 0")
        val gridLeft = row0.minOf { it.boundsInRoot.left }
        val gridRight = row0.maxOf { it.boundsInRoot.right }
        assertTrue(
            abs(gridLeft - bar.left) <= EDGE_TOLERANCE_PX,
            "[${spec.fileName}] left edge mismatch: grid=$gridLeft vs bar=${bar.left}",
        )
        assertTrue(
            abs(gridRight - bar.right) <= EDGE_TOLERANCE_PX,
            "[${spec.fileName}] right edge mismatch: grid=$gridRight vs bar=${bar.right}",
        )
        assertRowOrderAndTargets(spec, row0, bar.left, bar.right)
    }

    private fun assertRowOrderAndTargets(
        spec: SceneSpec,
        row0: List<SemanticsNode>,
        barLeft: Float,
        barRight: Float,
    ) {
        val rtl = spec.locale == "ar"
        val firstBounds = row0.first().boundsInRoot
        val lastBounds = row0.last().boundsInRoot
        if (rtl) {
            assertTrue(
                abs(firstBounds.right - barRight) <= EDGE_TOLERANCE_PX,
                "[${spec.fileName}] RTL first tile right=${firstBounds.right} should match bar right=$barRight",
            )
            assertTrue(
                abs(lastBounds.left - barLeft) <= EDGE_TOLERANCE_PX,
                "[${spec.fileName}] RTL last tile left=${lastBounds.left} should match bar left=$barLeft",
            )
        } else {
            assertTrue(
                abs(firstBounds.left - barLeft) <= EDGE_TOLERANCE_PX,
                "[${spec.fileName}] LTR first tile left=${firstBounds.left} should match bar left=$barLeft",
            )
            assertTrue(
                abs(lastBounds.right - barRight) <= EDGE_TOLERANCE_PX,
                "[${spec.fileName}] LTR last tile right=${lastBounds.right} should match bar right=$barRight",
            )
        }
        for (tile in row0) {
            val b = tile.boundsInRoot
            assertTrue(b.width >= MIN_TARGET_PX, "[${spec.fileName}] tile width ${b.width} < $MIN_TARGET_PX")
            assertTrue(b.height >= MIN_TARGET_PX, "[${spec.fileName}] tile height ${b.height} < $MIN_TARGET_PX")
            assertTrue(
                abs(b.width - firstBounds.width) <= EDGE_TOLERANCE_PX,
                "[${spec.fileName}] uneven tile width ${b.width} vs ${firstBounds.width}",
            )
            assertTrue(
                abs(b.height - firstBounds.height) <= EDGE_TOLERANCE_PX,
                "[${spec.fileName}] uneven tile height ${b.height} vs ${firstBounds.height}",
            )
        }
    }

    private fun ComposeUiTest.assertChipsReadable(label: String) {
        val chips = onAllNodesWithTag("kit-chip-label", useUnmergedTree = true)
        val count = chips.fetchSemanticsNodes().size
        assertTrue(count >= 5, "[$label] expected at least 5 tile action chips, got $count")
        for (i in 0 until count) {
            val layouts = mutableListOf<TextLayoutResult>()
            chips[i].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            val text = layout.layoutInput.text.text
            assertFalse(layout.hasVisualOverflow, "[$label] visual overflow on: $text")
            assertFalse(
                (0 until layout.lineCount).any { layout.isLineEllipsized(it) },
                "[$label] ellipsized: $text",
            )
            assertTrue(layout.lineCount <= 2, "[$label] expected <= 2 lines for '$text', got ${layout.lineCount}")
            assertTrue(KitWholeWordsRules.whole(layout, text), "[$label] broke mid-word: $text")
        }
    }
}

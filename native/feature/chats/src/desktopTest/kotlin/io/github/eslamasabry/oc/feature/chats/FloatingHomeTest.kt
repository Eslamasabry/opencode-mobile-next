package io.github.eslamasabry.oc.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.engine.agents.feed.FeedRunning
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsFilters
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsListSourceUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsState
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.FrostSpec
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidBackdrop
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidPaneSpec
import io.github.eslamasabry.oc.ui.kit.frame.LocalKitScreenInsets
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.sign
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Glass step 4 on the Conversations home (docs/designs/glass-in-the-kit/STEP4-PLAN.md §3): where the lens is drawn the
 * title row is a floating liquid bar and the server pill and filters float as pills under it, over the list; at rest the
 * list starts exactly where it did. Also writes the before/after pictures of the design note when FLOAT_RENDER_DIR names a
 * folder (FLOAT_RENDER_TAG: "" for after, "-before" for a run on the base).
 */
@OptIn(ExperimentalTestApi::class)
class FloatingHomeTest {
    private val savedLocale: Locale = Locale.getDefault()

    @BeforeTest fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest fun restore() = Locale.setDefault(savedLocale)

    /**
     * Desktop with the lens (as Android 13+ has it) and a frost blur. A liquid pane draws what it reads blurred under its
     * tint, as ChatEdgeATest's stand-in does: no bend, no rim light (the desktop cannot show those).
     */
    private class LensPlatform : KitPlatform by DesktopKitPlatform() {
        override val liquidGlass: Boolean = true

        override fun frostEffect(spec: FrostSpec, density: Float): RenderEffect? =
            (spec.sigmaDp * density).takeIf { it > 0f }?.let { BlurEffect(it, it) }

        override fun liquidPane(spec: KitLiquidPaneSpec, backdrop: KitLiquidBackdrop, shape: CornerBasedShape, transform: () -> Float): Modifier {
            val at = arrayOfNulls<LayoutCoordinates>(1)
            val draw = backdropDraw(backdrop)
            return Modifier.onGloballyPositioned { at[0] = it }
                .clip(shape)
                .drawWithCache {
                    val layer = obtainGraphicsLayer()
                    val r = LENS_BLUR_DP.dp.toPx()
                    onDrawBehind {
                        val c = at[0] ?: return@onDrawBehind
                        layer.renderEffect = BlurEffect(r, r)
                        layer.record { draw(this, c) }
                        drawLayer(layer)
                        drawRect(spec.fill)
                    }
                }
        }

        /** The backdrop's draw is kit-internal; a test outside the kit reaches it through its JVM getter. */
        @Suppress("UNCHECKED_CAST")
        private fun backdropDraw(backdrop: KitLiquidBackdrop): (DrawScope, LayoutCoordinates) -> Unit {
            val getter = KitLiquidBackdrop::class.java.methods.first { it.name.startsWith("getDraw") && it.parameterCount == 0 }
            return getter.invoke(backdrop) as (DrawScope, LayoutCoordinates) -> Unit
        }
    }

    private fun state(): ChatsUi {
        val items =
            listOf(
                item("explain", at = NOW, title = "Explain this project", status = ChatStatus.Running)
                    .copy(preview = "Reading lib/ui/kit to map how the parts fit together", agentId = "claude", running = FeedRunning(NOW - 4 * 60_000L)),
                item("news", at = NOW - 6 * 3_600_000L, title = "What's latest news on DeepSeek harness?")
                    .copy(preview = "Made you something. A process that reads the page and writes the notes."),
                item("hi", at = NOW - 26 * 3_600_000L, title = "Hi").copy(preview = "Created notes/check.txt with one line and ran ls"),
                item("who", at = NOW - 27 * 3_600_000L, title = "You know who you are and where are you working?")
                    .copy(preview = "Got it — I rebuilt it from scratch. Refresh here."),
                item("pc", at = NOW - 30 * 3_600_000L, title = "PC health check").copy(preview = "System: OS Ubuntu 24.04, memory fine"),
                item("new", at = NOW - 50 * 3_600_000L, title = "Hi").copy(preview = "new · This phone"),
            )
        val base =
            ChatsState.build(
                ChatFeedSnapshot(items.toImmutableList()),
                ReturnBrief.None,
                ChatsFilters(),
                startOfTodayMs = NOW - 12 * 3_600_000L,
                settingsAvailable = true,
                nowMs = NOW,
            )
        return base.copy(
            needsYou = 0,
            home =
                base.home.copy(
                    sources =
                        persistentListOf(
                            ChatsListSourceUi(
                                profile = ProfileId("this-phone"),
                                name = "This phone",
                                shown = true,
                                main = true,
                                onThisPhone = true,
                                unreachable = false,
                            )
                        )
                ),
        )
    }

    private class Seen(val bounds: Map<String, DpRect?>, val png: java.awt.image.BufferedImage)

    private fun home(platform: KitPlatform, motion: MotionLevel = MotionLevel.Full, act: ComposeUiTest.() -> Unit = {}): Seen {
        lateinit var seen: Seen
        runDesktopComposeUiTest((W * SCALE).toInt(), (H * SCALE).toInt()) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(SCALE, 1f)) {
                    ChatEnvironment {
                        KitTheme(
                            appearance = KitAppearance(mode = AppearanceMode.Dark, motion = motion, style = Style.Glass),
                            platform = platform,
                            localeTag = "en",
                            stillFrame = true,
                        ) {
                            CompositionLocalProvider(
                                LocalKitRedactor provides Redactor(),
                                LocalKitWindow provides KitWindowInfo(W.toFloat(), H.toFloat()),
                                LocalKitScreenInsets provides WindowInsets(top = STATUS_DP.dp, bottom = NAV_DP.dp),
                            ) {
                                Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                    ChatsScreen(state(), RecordingChatsActions())
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            act()
            waitForIdle()
            val tags = listOf("kit-liquid-bar", "kit-floating-pills", "kit-status-pill", "kit-page-heading")
            val bounds =
                tags.associateWith { runCatching { onNodeWithTag(it, useUnmergedTree = true).getBoundsInRoot() }.getOrNull() } +
                    ("first" to runCatching { onAllNodesWithText("Explain this project", useUnmergedTree = true).onFirst().getBoundsInRoot() }.getOrNull())
            seen = Seen(bounds, onRoot().captureToImage().toAwtImage())
        }
        return seen
    }

    /** A slow drag of [dyDp] (negative: up), held still before the finger lifts, so nothing flings. */
    private fun ComposeUiTest.drag(dyDp: Float) {
        onRoot().performTouchInput {
            val total = dyDp * SCALE
            down(Offset(width / 2f, height * 0.7f))
            moveBy(Offset(0f, viewConfiguration.touchSlop * sign(total)))
            repeat(STEPS) {
                advanceEventTime(16)
                moveBy(Offset(0f, total / STEPS))
            }
            advanceEventTime(200)
            up()
        }
    }

    @Test
    fun `B-HOME-FLOAT-001 under the lens the title floats as a liquid bar, the chips as pills, and the list rests where it did`() {
        val lens = home(LensPlatform())
        val flat = home(DesktopKitPlatform())
        assertNotNull(lens.bounds["kit-liquid-bar"], "the title row is a liquid capsule")
        assertNotNull(lens.bounds["kit-floating-pills"], "the pills float under it")
        assertNull(flat.bounds["kit-liquid-bar"], "without the lens there is no capsule")
        assertNull(flat.bounds["kit-floating-pills"], "without the lens the chips are the scrolling header's, as before")
        val capsule = lens.bounds["kit-liquid-bar"]!!
        val pills = lens.bounds["kit-floating-pills"]!!
        assertTrue(pills.top.value >= capsule.bottom.value - .5f, "the pills $pills lie under the capsule $capsule")
        val a = assertNotNull(lens.bounds["first"], "the first conversation shows under the lens")
        val b = assertNotNull(flat.bounds["first"], "the first conversation shows without the lens")
        assertTrue(abs(a.top.value - b.top.value) <= .5f, "the first conversation rests at ${a.top} under the lens, ${b.top} without")
        assertTrue(a.top.value >= pills.bottom.value - .5f, "the first conversation $a rests below the pills $pills")
        // Reduced motion holds the lens: the page is as it was.
        val held = home(LensPlatform(), motion = MotionLevel.Off)
        assertNull(held.bounds["kit-liquid-bar"], "reduced motion: no floating capsule")
        assertTrue(abs(assertNotNull(held.bounds["first"], "reduced motion: the first conversation shows").top.value - b.top.value) <= .5f, "reduced motion: the list rests where it did")
    }

    @Test
    fun `step 4 pictures of the home in Glass dark, at rest and scrolled 120 dp`() {
        val out = System.getenv("FLOAT_RENDER_DIR")?.let(::File) ?: return
        val tag = System.getenv("FLOAT_RENDER_TAG").orEmpty()
        out.mkdirs()
        val rest = home(LensPlatform())
        ImageIO.write(rest.png, "png", File(out, "render-step4-home-rest$tag.png"))
        val scrolled = home(LensPlatform()) { drag(-120f) }
        ImageIO.write(scrolled.png, "png", File(out, "render-step4-home-scrolled$tag.png"))
        println("step 4 renders$tag: rest first=${rest.bounds["first"]} scrolled first=${scrolled.bounds["first"]}")
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val SCALE = 2.5f
        const val W = 412
        const val H = 915
        const val STATUS_DP = 32
        const val NAV_DP = 16
        const val STEPS = 20
        const val LENS_BLUR_DP = 6f
    }
}
